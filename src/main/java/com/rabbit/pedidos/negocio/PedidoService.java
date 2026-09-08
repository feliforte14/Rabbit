package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — componente ServicioDePedidos (EJB @Stateless, patrón
 * Facade)
 *
 * PATRÓN FACADE
 * Orquesta el ciclo de vida completo del pedido (sincronizar, confirmar,
 * cancelar) detrás de una interfaz simple. El cliente (SincronizadorDePedidos
 * o PedidoBean) no conoce que sincronizarPedidoExterno() por dentro valida
 * el comercio contra ServicioDeComercios Y compromete stock contra
 * ServicioDeInventario — esa coordinación interna es exactamente lo que
 * el Facade esconde.
 *
 * POR QUÉ ESTE COMPONENTE ES STATELESS, NO STATEFUL
 * A diferencia de ServicioDeInventario, ninguna operación de acá depende
 * de un "pedido en curso" recordado entre llamadas: cada método recibe
 * por parámetro el ID del pedido sobre el que opera. El estado del
 * pedido vive en la base (columna "estado"), no en memoria de la
 * instancia — el mismo argumento que justifica @Stateless en
 * ServicioDeComercios.
 *
 * CÓMO SE EVITA MEZCLAR CONVERSACIONES AL LLAMAR A UN STATEFUL DESDE ACÁ
 * IReservaStock SÍ es conversacional (@Stateful): "reservar" y "confirmar"
 * tienen que ejecutarse sobre la MISMA instancia. Si este bean stateless
 * inyectara @Inject IReservaStock como campo fijo, el contenedor crearía
 * una única instancia stateful compartida por TODAS las invocaciones del
 * pool de PedidoService — dos pedidos sincronizados en paralelo por el
 * timer terminarían pisándose la reserva del otro.
 * La solución es Instance<IReservaStock>: cada llamada a
 * reservaProvider.get() crea una instancia stateful nueva y aislada, que
 * se usa para reservar+confirmar UN pedido y se descarta enseguida
 * (reservaProvider.destroy(...)) — la conversación stateful sigue
 * existiendo, solo que ahora la abre y cierra este método en vez de una
 * sesión de usuario como hace ReservaBean.
 *
 * POR QUÉ ACÁ CONVIVEN @Transactional Y @TransactionAttribute
 * Esta clase es un EJB (@Stateless), y en un EJB las transacciones las
 * gobierna el contenedor vía @TransactionAttribute (jakarta.ejb), no vía
 * @Transactional (jakarta.transaction), que es la anotación de los beans
 * CDI comunes. Los métodos marcados solo con @Transactional funcionan
 * igual porque el default de todo método de negocio de un EJB ya es
 * REQUIRED — la anotación no aporta nada, es el default el que actúa.
 *
 * Eso alcanza mientras REQUIRED sea lo que se quiere. No alcanza para
 * sincronizarPedidoExterno ni descartarPedidoExterno, que necesitan
 * REQUIRES_NEW: ahí sí hay que usar @TransactionAttribute, porque
 * @Transactional(REQUIRES_NEW) sobre un EJB se ignora en silencio y el
 * método seguiría corriendo en la transacción del llamador.
 *
 * Por qué necesitan transacción propia: SincronizadorDePedidos recorre
 * varias filas del mock en una sola pasada. Si una falla con
 * ValidacionException —anotada @ApplicationException(rollback = true)— y
 * todas comparten transacción, esa transacción queda marcada
 * rollback-only y ya no se puede des-marcar: el catch del loop atrapa la
 * excepción pero NO salva la pasada, y las filas que venían después
 * fallan con errores fantasma (un em.find() sobre una transacción
 * condenada devuelve null, y se ve como "ítem no encontrado" sobre un
 * ítem que existe). Con REQUIRES_NEW cada fila se juega su propia
 * transacción y una mala no arrastra a las demás.
 */

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.negocio.IReservaStock;
import com.rabbit.inventario.dto.ReservaStockDTO;
import com.rabbit.pedidos.datos.PedidoRepository;
import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
public class PedidoService implements IGestionPedidos, ISeguimientoPedido {

    private static final Logger LOG = Logger.getLogger(PedidoService.class.getName());

    @Inject
    private PedidoRepository repository;

    // Dependencia hacia OTRO componente, por su interfaz de solo lectura
    // — mismo criterio que InventarioService con IConsultaComercios.
    @Inject
    private IConsultaComercios comercios;

    // Provider, no la interfaz directa: ver el porqué en el comentario de clase.
    @Inject
    private Instance<IReservaStock> reservaProvider;

    // ===============================================================
    // IGestionPedidos — el Facade
    // ===============================================================

    @Override
    @Transactional
    public Long registrarPedidoExterno(DatosPedidoExternoDTO datos) {
        if (datos.idComercio == null) {
            throw new ValidacionException("Debe indicar el comercio del pedido");
        }
        if (datos.idItem == null) {
            throw new ValidacionException("Debe indicar el producto del pedido");
        }
        if (datos.cantidad <= 0) {
            throw new ValidacionException("La cantidad debe ser mayor a cero");
        }

        PedidoExterno externo = new PedidoExterno();
        externo.setIdComercio(datos.idComercio);
        externo.setIdItem(datos.idItem);
        externo.setCantidad(datos.cantidad);
        externo.setFechaPedido(LocalDateTime.now());
        externo.setSincronizado(false);
        return repository.guardarPedidoExterno(externo).getId();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Long sincronizarPedidoExterno(Long idPedidoExterno) {
        PedidoExterno externo = obtenerExternoOFallar(idPedidoExterno);
        if (externo.isSincronizado()) {
            throw new ValidacionException("El pedido externo " + idPedidoExterno + " ya fue sincronizado");
        }
        if (!comercios.validarComercioActivo(externo.getIdComercio())) {
            throw new ValidacionException(
                    "El comercio " + externo.getIdComercio() + " no existe o está dado de baja");
        }

        // Reservar + confirmar en la MISMA instancia stateful, fresca
        // para este pedido puntual (ver comentario de clase).
        IReservaStock reserva = reservaProvider.get();
        ReservaStockDTO reservaCreada;
        try {
            reservaCreada = reserva.reservarStock(externo.getIdComercio(), externo.getIdItem(), externo.getCantidad());
            reserva.confirmarReserva();
        } catch (RuntimeException e) {
            // Traduce la excepción de Inventario a la propia de este
            // componente: cada ServicioDeX no expone hacia afuera las
            // excepciones internas de otro (mismo criterio que
            // ComercioService.validarComercioActivo, que devuelve
            // boolean en vez de dejar pasar su propia ValidacionException).
            throw new ValidacionException("No se pudo comprometer el stock del pedido: " + e.getMessage());
        } finally {
            reservaProvider.destroy(reserva);
        }

        LocalDateTime ahora = LocalDateTime.now();
        Pedido pedido = new Pedido();
        pedido.setIdComercio(externo.getIdComercio());
        pedido.setIdItem(externo.getIdItem());
        pedido.setProducto(reservaCreada.getProducto());
        pedido.setCantidad(externo.getCantidad());
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setIdReservaStock(reservaCreada.getId());
        pedido.setFechaCreacion(ahora);
        pedido.setFechaActualizacion(ahora);
        repository.guardarPedido(pedido);

        externo.setSincronizado(true);
        repository.actualizarPedidoExterno(externo);

        LOG.info("[Pedidos] Sincronizado pedido externo " + idPedidoExterno + " -> pedido "
                + pedido.getId() + " (" + externo.getCantidad() + " x " + reservaCreada.getProducto() + ")");
        return pedido.getId();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void descartarPedidoExterno(Long idPedidoExterno, String motivo) {
        PedidoExterno externo = obtenerExternoOFallar(idPedidoExterno);
        externo.setSincronizado(true);
        externo.setErrorSincronizacion(recortar(motivo));
        repository.actualizarPedidoExterno(externo);
        LOG.warning("[Pedidos] Pedido externo " + idPedidoExterno + " descartado: " + motivo);
    }

    /** La columna admite 500 caracteres; los mensajes anidados se pasan. */
    private String recortar(String motivo) {
        if (motivo == null) {
            return "Sin detalle";
        }
        return motivo.length() <= 500 ? motivo : motivo.substring(0, 497) + "...";
    }

    @Override
    @Transactional
    public void confirmarPedido(Long idPedido) {
        Pedido pedido = obtenerOFallar(idPedido);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new ValidacionException("Solo se puede confirmar un pedido PENDIENTE");
        }
        pedido.setEstado(EstadoPedido.CONFIRMADO);
        pedido.setFechaActualizacion(LocalDateTime.now());
        repository.actualizarPedido(pedido);
    }

    @Override
    @Transactional
    public void cancelarPedido(Long idPedido) {
        Pedido pedido = obtenerOFallar(idPedido);
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new ValidacionException("El pedido ya está cancelado");
        }

        // El stock de este pedido se descontó al sincronizarlo (reservar +
        // confirmar juntos). Cancelar sin devolverlo dejaría la mercadería
        // del comercio "consumida" por un pedido que nunca se despachó, así
        // que hay que revertir esa confirmación contra ServicioDeInventario.
        if (pedido.getIdReservaStock() != null) {
            IReservaStock reserva = reservaProvider.get();
            try {
                reserva.registrarDevolucion(pedido.getIdReservaStock());
            } catch (RuntimeException e) {
                throw new ValidacionException(
                        "No se pudo devolver el stock del pedido: " + e.getMessage());
            } finally {
                reservaProvider.destroy(reserva);
            }
        }

        pedido.setEstado(EstadoPedido.CANCELADO);
        pedido.setFechaActualizacion(LocalDateTime.now());
        repository.actualizarPedido(pedido);
    }

    // ===============================================================
    // ISeguimientoPedido
    // ===============================================================

    @Override
    public PedidoDTO consultarEstadoPedido(Long idPedido) {
        return PedidoDTO.desde(obtenerOFallar(idPedido));
    }

    @Override
    public List<PedidoDTO> listarTodos() {
        return repository.listarTodos().stream().map(PedidoDTO::desde).collect(Collectors.toList());
    }

    @Override
    public List<PedidoDTO> listarPedidosDeComercio(Long idComercio) {
        return repository.listarPedidosDeComercio(idComercio).stream()
                .map(PedidoDTO::desde)
                .collect(Collectors.toList());
    }

    @Override
    public List<PedidoExternoDTO> listarPedidosExternos() {
        return repository.listarTodosLosExternos().stream()
                .map(PedidoExternoDTO::desde)
                .collect(Collectors.toList());
    }

    // --- privados ---

    private Pedido obtenerOFallar(Long id) {
        Pedido pedido = repository.buscarPedidoPorId(id);
        if (pedido == null) {
            throw new ValidacionException("Pedido no encontrado: " + id);
        }
        return pedido;
    }

    private PedidoExterno obtenerExternoOFallar(Long id) {
        PedidoExterno externo = repository.buscarPedidoExternoPorId(id);
        if (externo == null) {
            throw new ValidacionException("Pedido externo no encontrado: " + id);
        }
        return externo;
    }
}
