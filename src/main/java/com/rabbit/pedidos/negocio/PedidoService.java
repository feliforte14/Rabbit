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
 * TRANSACCIONES: @TransactionAttribute, NUNCA @Transactional
 * Esta clase es un EJB (@Stateless), y en un EJB las transacciones las
 * gobierna el contenedor vía @TransactionAttribute (jakarta.ejb), no vía
 * @Transactional (jakarta.transaction), que es la anotación de los beans
 * CDI comunes y sobre un EJB se ignora en silencio: un
 * @Transactional(REQUIRES_NEW) seguiría corriendo en la transacción del
 * llamador. Las operaciones comunes van con REQUIRED (el default, puesto
 * explícito); sincronizarPedidoExterno y descartarPedidoExterno necesitan
 * REQUIRES_NEW.
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
 *
 * INTEGRACIÓN ASINCRÓNICA (JMS, mensajería punto a punto)
 * registrarPedidoExterno() ya no depende únicamente de que
 * SincronizadorDePedidos pase a revisarlo por polling: al guardar la fila
 * dispara el evento CDI PedidoExternoRegistrado, PublicadorPedidosExternos
 * lo observa y, una vez confirmada la transacción, publica un mensaje en
 * "cola.pedidos.externos"; PedidoExternoListener (un @MessageDriven) la
 * sincroniza en cuanto el mensaje llega, llamando al mismo
 * sincronizarPedidoExterno() de este Facade. El polling sigue existiendo
 * como red de contención (ver PublicadorPedidosExternos), pero el camino
 * normal ahora es event-driven, no por sondeo cada un minuto.
 *
 * Como ahora hay dos disparadores que pueden llegar a la vez sobre la
 * misma fila, sincronizarPedidoExterno y descartarPedidoExterno la leen
 * con bloqueo (ver PedidoRepository.buscarPedidoExternoParaActualizar).
 */

import com.rabbit.comercios.dto.PuntoPickingDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.inventario.negocio.IReservaStock;
import com.rabbit.transportistas.dto.DatosEnvioDTO;
import com.rabbit.transportistas.negocio.IEnvios;
import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pagos.negocio.IRegistroCobros;
import com.rabbit.repartidores.negocio.IAsignacionRepartidores;
import com.rabbit.inventario.dto.ReservaStockDTO;
import com.rabbit.pedidos.datos.PedidoRepository;
import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.LineaPedido;
import com.rabbit.pedidos.datos.model.LineaPedidoExterno;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// SEGURIDAD: @PermitAll de clase porque la sincronización la disparan el
// listener JMS y el timer, que corren sin usuario (y este WildFly deniega
// todo método sin permiso declarado en cuanto el EJB tiene alguno). Las
// operaciones que solo dispara una persona o el ERP llevan @RolesAllowed;
// un REPARTIDOR solo puede mover los pedidos que tiene asignados.
@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO", "REPARTIDOR", "ERP"})
@PermitAll
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

    // Aviso de "se guardó un pedido externo". Lo observa
    // PublicadorPedidosExternos, que publica en cola.pedidos.externos
    // recién cuando esta transacción se confirma.
    @Inject
    private Event<PedidoExternoRegistrado> pedidoExternoRegistrado;

    // Aviso de "un pedido cambió de estado" (ver cambiarEstado). Lo observa
    // PublicadorEstadosPedido, que lo publica en topico.pedidos.estado.
    @Inject
    private Event<EstadoPedidoCambiado> estadoPedidoCambiado;

    // Flujo de confirmación (cobrar → asignar repartidor → confirmar): los
    // dos corren con REQUIRED y se suman a la transacción de confirmarPedido.
    @Inject
    private IAsignacionRepartidores repartidores;

    @Inject
    private IRegistroCobros cobros;

    // Derivación a transportistas externos (ver derivarATransportista).
    @Inject
    private IEnvios envios;

    // Para armar la dirección de retiro de un pedido de stock consignado.
    @Inject
    private IConsultaStock stock;

    // A quién representa el usuario que llama (comercio o repartidor).
    @Inject
    private IContextoUsuario contextoUsuario;

    @Resource
    private SessionContext contexto;

    // ===============================================================
    // IGestionPedidos — el Facade
    // ===============================================================

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "ERP"})
    public Long registrarPedidoExterno(DatosPedidoExternoDTO datos) {
        if (datos.idComercio == null) {
            throw new ValidacionException("Debe indicar el comercio del pedido");
        }
        if (datos.lineas == null || datos.lineas.isEmpty()) {
            throw new ValidacionException("El pedido debe tener al menos un producto");
        }
        if (datos.importe == null || datos.importe.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("El importe del pedido debe ser mayor a cero");
        }
        if (datos.medioPago == null) {
            throw new ValidacionException("Debe indicar el medio de pago del pedido");
        }
        if (datos.direccionEntrega == null || datos.direccionEntrega.isBlank()) {
            throw new ValidacionException("Debe indicar la dirección de entrega del pedido");
        }
        if (datos.direccionEntrega.trim().length() > 200) {
            throw new ValidacionException("La dirección de entrega no puede superar los 200 caracteres");
        }
        OrigenPedido origen = datos.origen != null ? datos.origen : OrigenPedido.STOCK_CONSIGNADO;

        PedidoExterno externo = new PedidoExterno();
        externo.setIdComercio(datos.idComercio);
        externo.setOrigen(origen);
        externo.setImporte(datos.importe);
        externo.setMedioPago(datos.medioPago);
        externo.setDireccionEntrega(datos.direccionEntrega.trim());
        externo.setFechaPedido(LocalDateTime.now());
        externo.setSincronizado(false);

        List<LineaPedidoExterno> lineas = new ArrayList<>();
        if (origen == OrigenPedido.PUNTO_PICKING) {
            // El comercio ya validó el pedido en su propio punto de picking:
            // Rabbit no gestiona ese stock (ver PuntoPicking), así que no hay
            // ítem que referenciar — solo qué retirar y de dónde. El punto de
            // picking es del pedido completo: todas sus líneas salen de ahí.
            if (datos.idPuntoPicking == null) {
                throw new ValidacionException("Debe indicar el punto de picking del pedido");
            }
            externo.setIdPuntoPicking(datos.idPuntoPicking);
            for (DatosLineaPedidoDTO datosLinea : datos.lineas) {
                if (datosLinea.producto == null || datosLinea.producto.isBlank()) {
                    throw new ValidacionException("Debe indicar qué se va a retirar del punto de picking");
                }
                validarCantidadLinea(datosLinea.cantidad);
                LineaPedidoExterno linea = new LineaPedidoExterno();
                linea.setPedidoExterno(externo);
                linea.setProducto(datosLinea.producto.trim());
                linea.setCantidad(datosLinea.cantidad);
                lineas.add(linea);
            }
        } else {
            for (DatosLineaPedidoDTO datosLinea : datos.lineas) {
                if (datosLinea.idItem == null) {
                    throw new ValidacionException("Debe indicar el producto del pedido");
                }
                validarCantidadLinea(datosLinea.cantidad);
                LineaPedidoExterno linea = new LineaPedidoExterno();
                linea.setPedidoExterno(externo);
                linea.setIdItem(datosLinea.idItem);
                linea.setCantidad(datosLinea.cantidad);
                lineas.add(linea);
            }
        }
        externo.setLineas(lineas);

        Long idExterno = repository.guardarPedidoExterno(externo).getId();

        // Acá la fila todavía NO está confirmada: el observer es
        // AFTER_SUCCESS, así que el mensaje sale recién después del commit
        // (y no sale si esta transacción se deshace).
        pedidoExternoRegistrado.fire(new PedidoExternoRegistrado(idExterno, origen));

        return idExterno;
    }

    private void validarCantidadLinea(int cantidad) {
        if (cantidad <= 0) {
            throw new ValidacionException("La cantidad debe ser mayor a cero");
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Long sincronizarPedidoExterno(Long idPedidoExterno) {
        PedidoExterno externo = obtenerExternoParaActualizarOFallar(idPedidoExterno);
        if (externo.isSincronizado()) {
            // Ya lo procesó el otro disparador (o es una redelivery): no es
            // una regla de negocio violada, ver PedidoYaSincronizadoException.
            throw new PedidoYaSincronizadoException(idPedidoExterno);
        }
        if (!comercios.validarComercioActivo(externo.getIdComercio())) {
            throw new ValidacionException(
                    "El comercio " + externo.getIdComercio() + " no existe o está dado de baja");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Pedido pedido = new Pedido();
        pedido.setIdComercio(externo.getIdComercio());
        pedido.setOrigen(externo.getOrigen());
        pedido.setImporte(externo.getImporte());
        pedido.setMedioPago(externo.getMedioPago());
        pedido.setDireccionEntrega(externo.getDireccionEntrega());
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setFechaCreacion(ahora);
        pedido.setFechaActualizacion(ahora);

        List<LineaPedido> lineasPedido = new ArrayList<>();
        if (externo.getOrigen() == OrigenPedido.PUNTO_PICKING) {
            // El comercio ya validó y armó el pedido en su propio punto de
            // picking (ver PuntoPicking): Rabbit no gestiona ese stock, así
            // que acá no hay nada que reservar — solo confirmar que el
            // punto de picking existe y está operativo para coordinar el
            // retiro.
            validarPuntoPickingActivo(externo.getIdComercio(), externo.getIdPuntoPicking());
            pedido.setIdPuntoPicking(externo.getIdPuntoPicking());
            for (LineaPedidoExterno lineaExterna : externo.getLineas()) {
                LineaPedido linea = new LineaPedido();
                linea.setPedido(pedido);
                linea.setProducto(lineaExterna.getProducto());
                linea.setCantidad(lineaExterna.getCantidad());
                lineasPedido.add(linea);
            }
        } else {
            // Cada línea reserva + confirma en su PROPIA instancia stateful,
            // fresca (ver comentario de clase): un pedido con varios
            // productos compromete varias reservas independientes. Al correr
            // todas dentro de esta misma transacción REQUIRES_NEW, si una
            // línea falla su rollback deshace también las reservas ya
            // confirmadas de las líneas anteriores — el pedido es atómico,
            // todo o nada.
            for (LineaPedidoExterno lineaExterna : externo.getLineas()) {
                IReservaStock reserva = reservaProvider.get();
                ReservaStockDTO reservaCreada;
                try {
                    reservaCreada = reserva.reservarStock(
                            externo.getIdComercio(), lineaExterna.getIdItem(), lineaExterna.getCantidad());
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

                LineaPedido linea = new LineaPedido();
                linea.setPedido(pedido);
                linea.setIdItem(lineaExterna.getIdItem());
                linea.setProducto(reservaCreada.getProducto());
                linea.setCantidad(lineaExterna.getCantidad());
                linea.setIdReservaStock(reservaCreada.getId());
                lineasPedido.add(linea);
            }
        }
        pedido.setLineas(lineasPedido);

        repository.guardarPedido(pedido);
        avisarCambioDeEstado(pedido);

        externo.setSincronizado(true);
        externo.setIdPedido(pedido.getId());
        repository.actualizarPedidoExterno(externo);

        LOG.info("[Pedidos] Sincronizado pedido externo " + idPedidoExterno + " -> pedido "
                + pedido.getId() + " (" + lineasPedido.size() + " línea(s))");
        return pedido.getId();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void descartarPedidoExterno(Long idPedidoExterno, String motivo) {
        PedidoExterno externo = obtenerExternoParaActualizarOFallar(idPedidoExterno);
        if (externo.isSincronizado()) {
            // Mientras se decidía descartarla, el otro disparador la
            // sincronizó: no pisar ese resultado con un motivo de descarte.
            return;
        }
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
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void confirmarPedido(Long idPedido) {
        Pedido pedido = obtenerOFallar(idPedido);
        validarTransicion(pedido, EstadoPedido.CONFIRMADO);

        // Los tres pasos son UNA transacción: si algo falla, el pedido sigue
        // PENDIENTE y no queda cobro ni repartidor asignado en Rabbit. Lo que
        // el rollback NO deshace es el cobro en el banco (sistema externo):
        // si el banco ya autorizó y después no hay repartidor, Pagos pide la
        // reversa al banco (ver ReversasBancarias). Se cobra primero a
        // propósito: así ese caso se puede mostrar en la demo.
        //
        // Las excepciones de Repartidores y Pagos ya marcan la transacción
        // para rollback (@ApplicationException(rollback = true)); acá solo
        // se traducen a la de Pedidos para que la vista muestre el motivo.
        Long idRepartidor;
        try {
            cobros.registrarCobro(idPedido, pedido.getIdComercio(), pedido.getImporte(), pedido.getMedioPago());
            idRepartidor = repartidores.asignarRepartidor(idPedido);
        } catch (com.rabbit.repartidores.negocio.ValidacionException
                 | com.rabbit.pagos.negocio.ValidacionException e) {
            throw new ValidacionException(e.getMessage());
        }
        pedido.setIdRepartidor(idRepartidor);
        cambiarEstado(pedido, EstadoPedido.CONFIRMADO);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public String derivarATransportista(Long idPedido, Long idTransportista) {
        Pedido pedido = obtenerOFallar(idPedido);
        validarTransicion(pedido, EstadoPedido.CONFIRMADO);
        if (idTransportista == null) {
            throw new ValidacionException("Elegí a qué transportista derivar el pedido");
        }

        // Mismo esquema que confirmarPedido, con el transportista en lugar
        // del repartidor: todo en una transacción. Si el transportista ya
        // tomó el envío y después algo falla, se lo cancela (compensación,
        // ver CancelacionesDeEnvios); si el banco ya cobró, se reversa.
        DatosEnvioDTO datos = new DatosEnvioDTO();
        datos.direccionRetiro = direccionDeRetiro(pedido);
        datos.direccionEntrega = pedido.getDireccionEntrega();
        datos.bultos = pedido.getLineas().stream().mapToInt(LineaPedido::getCantidad).sum();
        datos.cobrarAlEntregar = pedido.getMedioPago() == MedioPago.CONTRA_ENTREGA ? pedido.getImporte() : null;
        String codigoSeguimiento;
        try {
            cobros.registrarCobro(idPedido, pedido.getIdComercio(), pedido.getImporte(), pedido.getMedioPago());
            codigoSeguimiento = envios.solicitarEnvio(idPedido, pedido.getIdComercio(), idTransportista, datos)
                    .getCodigoSeguimiento();
        } catch (com.rabbit.pagos.negocio.ValidacionException
                 | com.rabbit.transportistas.negocio.ValidacionException e) {
            throw new ValidacionException(e.getMessage());
        }
        pedido.setIdRepartidor(null);
        cambiarEstado(pedido, EstadoPedido.CONFIRMADO);
        return codigoSeguimiento;
    }

    // De dónde retira el transportista: el punto de picking del comercio, o
    // los depósitos de Rabbit de los que sale el stock consignado.
    private String direccionDeRetiro(Pedido pedido) {
        if (pedido.getOrigen() == OrigenPedido.PUNTO_PICKING) {
            return comercios.listarPuntosPickingDeComercio(pedido.getIdComercio()).stream()
                    .filter(pp -> pp.id.equals(pedido.getIdPuntoPicking()))
                    .map(pp -> pp.nombre + " — " + pp.direccion)
                    .findFirst().orElse("Punto de picking " + pedido.getIdPuntoPicking());
        }
        List<Long> idsItems = pedido.getLineas().stream()
                .map(LineaPedido::getIdItem).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        return stock.listarItemsPorIds(idsItems).stream()
                .map(ItemInventarioDTO::getIdDeposito)
                .distinct()
                .map(stock::obtenerDeposito)
                .map(d -> "Depósito " + d.getNombre() + " — " + d.getDireccion() + ", " + d.getLocalidad())
                .collect(Collectors.joining("; "));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "REPARTIDOR"})
    public void despacharPedido(Long idPedido) {
        Pedido pedido = obtenerOFallar(idPedido);
        exigirRepartidorAsignado(pedido);
        cambiarEstado(pedido, EstadoPedido.EN_CAMINO);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "REPARTIDOR"})
    public void registrarEntrega(Long idPedido) {
        Pedido pedido = obtenerOFallar(idPedido);
        exigirRepartidorAsignado(pedido);
        cambiarEstado(pedido, EstadoPedido.ENTREGADO);
        // El repartidor queda libre para otro pedido. El cobro CONTRA_ENTREGA
        // NO se hace acá: lo efectiviza Pagos al recibir ENTREGADO por el
        // tópico (ver SuscriptorPagosEstadoPedido).
        repartidores.liberarRepartidor(pedido.getIdRepartidor());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void cancelarPedido(Long idPedido) {
        Pedido pedido = obtenerOFallar(idPedido);
        // Se valida antes de devolver el stock: si el pedido ya está
        // EN_CAMINO o ENTREGADO, la mercadería no está para devolverla.
        validarTransicion(pedido, EstadoPedido.CANCELADO);

        // Un pedido CONFIRMADO ya tiene cobro y repartidor. Anular el cobro
        // exige rol ADMINISTRADOR (@RolesAllowed en PagoService.anularCobro):
        // un OPERADOR recibe EJBAccessException y no se cancela nada.
        if (pedido.getEstado() == EstadoPedido.CONFIRMADO) {
            cobros.anularCobro(idPedido);
            repartidores.liberarRepartidor(pedido.getIdRepartidor());
            // Si lo llevaba un transportista, se le cancela el envío (se le
            // avisa recién cuando esta cancelación queda confirmada).
            envios.cancelarEnvioDePedido(idPedido);
        }

        // El stock de cada línea con reserva se descontó al sincronizarla
        // (reservar + confirmar juntos). Cancelar sin devolverlo dejaría la
        // mercadería del comercio "consumida" por un pedido que nunca se
        // despachó, así que hay que revertir esa confirmación contra
        // ServicioDeInventario — una línea de PUNTO_PICKING no tiene
        // idReservaStock (nunca reservó nada) y se salta.
        for (LineaPedido linea : pedido.getLineas()) {
            if (linea.getIdReservaStock() == null) {
                continue;
            }
            IReservaStock reserva = reservaProvider.get();
            try {
                reserva.registrarDevolucion(linea.getIdReservaStock());
            } catch (RuntimeException e) {
                throw new ValidacionException(
                        "No se pudo devolver el stock del pedido: " + e.getMessage());
            } finally {
                reservaProvider.destroy(reserva);
            }
        }

        cambiarEstado(pedido, EstadoPedido.CANCELADO);
    }

    // ===============================================================
    // ISeguimientoPedido
    // ===============================================================

    // Devuelve el pedido como DTO (nunca expone la entidad directamente)
    @Override
    // Pública a propósito: la usa el seguimiento sin login
    // (/api/seguimiento/{id}), que solo expone el estado.
    public PedidoDTO consultarEstadoPedido(Long idPedido) {
        return PedidoDTO.desde(obtenerOFallar(idPedido));
    }

    // Devuelve todos los pedidos reales como DTO — usado por la vista de listado
    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<PedidoDTO> listarTodos() {
        return repository.listarTodos().stream().map(PedidoDTO::desde).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<PedidoDTO> listarEntregasEnCurso() {
        return repository.listarEntregasEnCurso().stream().map(PedidoDTO::desde).collect(Collectors.toList());
    }

    // Pedidos del comercio que representa el usuario que llama: el comercio
    // sale de la identidad autenticada, nunca de un parámetro.
    @Override
    @RolesAllowed("COMERCIO")
    public List<PedidoDTO> listarPedidosDelComercioActual() {
        return listarPedidosDeComercio(contextoUsuario.idComercioActual());
    }

    // Pedidos que tuvo o tiene asignados el repartidor que llama.
    @Override
    @RolesAllowed("REPARTIDOR")
    public List<PedidoDTO> listarPedidosDelRepartidorActual() {
        return repository.listarPedidosDeRepartidor(contextoUsuario.idRepartidorActual()).stream()
                .map(PedidoDTO::desde)
                .collect(Collectors.toList());
    }

    // Un REPARTIDOR solo mueve los pedidos que tiene asignados; el personal
    // de Rabbit puede mover cualquiera.
    private void exigirRepartidorAsignado(Pedido pedido) {
        if (contexto.isCallerInRole("REPARTIDOR")
                && !contextoUsuario.idRepartidorActual().equals(pedido.getIdRepartidor())) {
            throw new ValidacionException("El pedido " + pedido.getId() + " no está asignado a vos");
        }
    }

    // Pedidos reales de un comercio puntual.
    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<PedidoDTO> listarPedidosDeComercio(Long idComercio) {
        return repository.listarPedidosDeComercio(idComercio).stream()
                .map(PedidoDTO::desde)
                .collect(Collectors.toList());
    }

    // Todas las filas del mock del ERP (sincronizadas y pendientes) —
    // deja ver en la vista el "antes y después" de la sincronización.
    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "ERP"})
    public PedidoExternoDTO consultarPedidoExterno(Long idPedidoExterno) {
        PedidoExterno externo = repository.buscarPedidoExternoPorId(idPedidoExterno);
        if (externo == null) {
            throw new ValidacionException("Pedido externo no encontrado: " + idPedidoExterno);
        }
        return PedidoExternoDTO.desde(externo);
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<PedidoExternoDTO> listarPedidosExternos() {
        return repository.listarTodosLosExternos().stream()
                .map(PedidoExternoDTO::desde)
                .collect(Collectors.toList());
    }

    // --- privados ---

    // Lanza excepción si el pedido no existe — evita repetir este chequeo
    // en cada método que opera sobre uno puntual.
    private Pedido obtenerOFallar(Long id) {
        Pedido pedido = repository.buscarPedidoPorId(id);
        if (pedido == null) {
            throw new ValidacionException("Pedido no encontrado: " + id);
        }
        return pedido;
    }

    // Lanza excepción si el pedido externo (mock del ERP) no existe. Lo
    // devuelve bloqueado hasta el fin de la transacción (ver
    // PedidoRepository.buscarPedidoExternoParaActualizar).
    private PedidoExterno obtenerExternoParaActualizarOFallar(Long id) {
        PedidoExterno externo = repository.buscarPedidoExternoParaActualizar(id);
        if (externo == null) {
            throw new ValidacionException("Pedido externo no encontrado: " + id);
        }
        return externo;
    }

    // Único lugar donde cambia el estado de un pedido ya existente: valida
    // la transición contra la máquina de estados (EstadoPedido.puedePasarA)
    // y avisa el cambio.
    private void cambiarEstado(Pedido pedido, EstadoPedido destino) {
        validarTransicion(pedido, destino);
        pedido.setEstado(destino);
        pedido.setFechaActualizacion(LocalDateTime.now());
        repository.actualizarPedido(pedido);
        avisarCambioDeEstado(pedido);
    }

    private void validarTransicion(Pedido pedido, EstadoPedido destino) {
        if (!pedido.getEstado().puedePasarA(destino)) {
            throw new ValidacionException("El pedido " + pedido.getId() + " está " + pedido.getEstado()
                    + " y no puede pasar a " + destino);
        }
    }

    // Todavía no hay observer: lo va a escuchar el publicador del tópico
    // (AFTER_SUCCESS), así que si la transacción se deshace el aviso no sale.
    private void avisarCambioDeEstado(Pedido pedido) {
        estadoPedidoCambiado.fire(new EstadoPedidoCambiado(
                pedido.getId(), pedido.getIdComercio(), pedido.getEstado(), pedido.getFechaActualizacion()));
    }

    // Confirma que el punto de picking exista, pertenezca a ESE comercio y
    // esté activo — mismo criterio que validarComercioActivo: sin esto,
    // Rabbit coordinaría un retiro en un punto que el comercio ya dio de baja.
    private void validarPuntoPickingActivo(Long idComercio, Long idPuntoPicking) {
        boolean activo = comercios.listarPuntosPicking(idComercio).stream()
                .map(PuntoPickingDTO::getId)
                .anyMatch(id -> id.equals(idPuntoPicking));
        if (!activo) {
            throw new ValidacionException(
                    "El punto de picking " + idPuntoPicking + " no existe, no pertenece a este comercio, o está dado de baja");
        }
    }
}
