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
import com.rabbit.inventario.negocio.StockModificadoException;
import com.rabbit.transportistas.dto.CotizacionDTO;
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
import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import com.rabbit.pedidos.datos.model.RemitoEntrega;
import com.rabbit.pedidos.datos.model.FotoIntentoEntrega;
import com.rabbit.pedidos.datos.model.IntentoEntrega;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import com.rabbit.pedidos.dto.EntregaFallidaDTO;
import com.rabbit.pedidos.dto.FotoDeVisitaDTO;
import com.rabbit.pedidos.dto.IntentoEntregaDTO;
import com.rabbit.repartidores.dto.UbicacionDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.dto.RemitoDTO;
import com.rabbit.pedidos.dto.ResumenEntregasDTO;

import com.rabbit.pedidos.datos.model.CalificacionEntrega;
import com.rabbit.pedidos.dto.CalificacionDTO;
import jakarta.persistence.PersistenceException;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.pedidos.datos.model.TipoEntrega;
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
import java.util.Map;
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

    // Tope de un pedido de punto de picking: más que cualquier vehículo.
    private static final BigDecimal PESO_MAXIMO_PEDIDO_KG = new BigDecimal("5000");

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

    // A quién representa el usuario que llama (comercio, ERP o repartidor).
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
    public Long registrarPedidoExterno(DatosPedidoExternoDTO datos, String claveIdempotencia) {
        // El ERP carga pedidos SOLO de su comercio: sale de su cuenta, nunca
        // del cuerpo del pedido (mismo criterio que el portal del comercio).
        if (contexto.isCallerInRole("ERP")) {
            datos.idComercio = idComercioDelErp();
        }
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
        // Cómo lo recibe quien compró (ADR-053). Con retiro en depósito no hay dirección ni código
        // postal: lo busca en un depósito de Rabbit (se decide al sincronizar, ver más abajo).
        TipoEntrega tipoEntrega = datos.tipoEntrega != null ? datos.tipoEntrega : TipoEntrega.DOMICILIO;
        boolean retiro = ReglaDeRetiro.esRetiro(tipoEntrega);
        if (!retiro) {
            if (datos.direccionEntrega == null || datos.direccionEntrega.isBlank()) {
                throw new ValidacionException("Debe indicar la dirección de entrega del pedido");
            }
            if (datos.direccionEntrega.trim().length() > 200) {
                throw new ValidacionException("La dirección de entrega no puede superar los 200 caracteres");
            }
            if (CodigosPostales.esInvalido(datos.codigoPostalEntrega)) {
                throw new ValidacionException("El código postal de entrega tiene que tener 4 dígitos (o ser un CPA como C1414ABC)");
            }
        }
        OrigenPedido origen = datos.origen != null ? datos.origen : OrigenPedido.STOCK_CONSIGNADO;
        ReglaDeRetiro.validarOrigen(tipoEntrega, origen);
        validarPesoInformado(datos.pesoKg, origen);
        // Quién recibe (ADR-039): el email es obligatorio (por ahí se le
        // avisa); nombre y teléfono, opcionales, pero si vienen tienen que servir.
        String nombreDestinatario = Destinatarios.nombre(datos.nombreDestinatario);
        String telefonoDestinatario = Destinatarios.telefono(datos.telefonoDestinatario);
        String emailDestinatario = Destinatarios.emailObligatorio(datos.emailDestinatario);

        // Idempotencia: si el ERP no recibió la respuesta (timeout) y
        // reintenta, la misma clave con el mismo pedido devuelve el que ya
        // se creó. Si dos intentos llegan a la vez, los dos pasan esta
        // consulta pero la restricción única (idComercio, clave) rechaza al
        // segundo al confirmar, y la API lo reintenta (ver
        // PedidosExternosResource): ahí ya lo encuentra acá.
        String huella = null;
        if (claveIdempotencia != null) {
            huella = HuellaDePedido.de(datos, origen);
            PedidoExterno anterior = repository.buscarPedidoExternoPorClave(datos.idComercio, claveIdempotencia);
            if (anterior != null) {
                if (!huella.equals(anterior.getHuellaSolicitud())) {
                    throw new ClaveIdempotenciaReutilizadaException(claveIdempotencia);
                }
                LOG.info("[Pedidos] Reintento con la clave " + claveIdempotencia
                        + ": se devuelve el pedido externo " + anterior.getId() + " sin duplicarlo");
                return anterior.getId();
            }
        }

        LocalDateTime ahora = LocalDateTime.now();
        String codigoPostal = retiro ? null : CodigosPostales.resolver(datos.codigoPostalEntrega, datos.direccionEntrega);
        PedidoExterno externo = new PedidoExterno();
        externo.setTipoEntrega(tipoEntrega);
        externo.setIdComercio(datos.idComercio);
        externo.setOrigen(origen);
        externo.setImporte(datos.importe);
        externo.setMedioPago(datos.medioPago);
        externo.setPesoKg(datos.pesoKg);
        externo.setDireccionEntrega(retiro ? ReglaDeRetiro.DIRECCION_PROVISORIA : datos.direccionEntrega.trim());
        externo.setCodigoPostalEntrega(codigoPostal);
        externo.setFechaPedido(ahora);
        externo.setNombreDestinatario(nombreDestinatario);
        externo.setTelefonoDestinatario(telefonoDestinatario);
        externo.setEmailDestinatario(emailDestinatario);
        // Fecha prometida (ADR-042): sin código postal no se sabe la zona,
        // y no se promete lo que quizás haya que despachar a mano.
        externo.setFechaPrometida(codigoPostal != null ? repartidores.diaDeEntregaPara(ahora) : null);
        externo.setSincronizado(false);
        externo.setClaveIdempotencia(claveIdempotencia);
        externo.setHuellaSolicitud(huella);

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

    // El peso lo informa el comercio solo para PUNTO_PICKING; con stock
    // consignado lo calcula Rabbit con el peso de cada ítem (ADR-032).
    private void validarPesoInformado(BigDecimal pesoKg, OrigenPedido origen) {
        if (pesoKg == null) {
            return;
        }
        if (origen != OrigenPedido.PUNTO_PICKING) {
            throw new ValidacionException("El peso solo se informa en pedidos de punto de picking: "
                    + "con stock consignado lo calcula Rabbit con el peso de cada producto");
        }
        if (pesoKg.signum() <= 0) {
            throw new ValidacionException("El peso del pedido debe ser mayor a cero");
        }
        if (pesoKg.compareTo(PESO_MAXIMO_PEDIDO_KG) > 0) {
            throw new ValidacionException("El peso del pedido no puede superar los " + PESO_MAXIMO_PEDIDO_KG + " kg");
        }
        if (pesoKg.stripTrailingZeros().scale() > 3) {
            throw new ValidacionException("El peso admite hasta 3 decimales (gramos)");
        }
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
        pedido.setCodigoPostalEntrega(externo.getCodigoPostalEntrega());
        pedido.setNombreDestinatario(externo.getNombreDestinatario());
        pedido.setTelefonoDestinatario(externo.getTelefonoDestinatario());
        pedido.setEmailDestinatario(externo.getEmailDestinatario());
        pedido.setFechaPrometida(externo.getFechaPrometida());
        pedido.setTipoEntrega(externo.getTipoEntrega());
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setFechaCreacion(ahora);
        pedido.setFechaActualizacion(ahora);
        pedido.setCodigoSeguimiento(CodigosDeSeguimiento.nuevo());

        // Retiro en depósito (ADR-053): se retira donde está el stock. Se decide ANTES de reservar:
        // si los productos están en distintos depósitos no hay un solo lugar de retiro, y no tiene
        // sentido comprometer stock para después deshacerlo.
        if (externo.isRetiroEnDeposito()) {
            ReglaDeRetiro.validarOrigen(externo.getTipoEntrega(), externo.getOrigen());
            List<Long> idsItems = externo.getLineas().stream().map(LineaPedidoExterno::getIdItem).collect(Collectors.toList());
            Map<Long, Long> depositoPorItem = stock.depositosPorItem(idsItems);
            Long idDeposito = ReglaDeRetiro.depositoUnico(
                    idsItems.stream().map(depositoPorItem::get).collect(Collectors.toList()));
            DepositoDTO deposito = stock.obtenerDeposito(idDeposito);
            pedido.setIdDepositoRetiro(idDeposito);
            pedido.setDireccionEntrega(ReglaDeRetiro.textoDeLaDireccion(deposito.getNombre(), deposito.getDireccion()));
        }

        List<LineaPedido> lineasPedido = new ArrayList<>();
        if (externo.getOrigen() == OrigenPedido.PUNTO_PICKING) {
            // El comercio ya validó y armó el pedido en su propio punto de
            // picking (ver PuntoPicking): Rabbit no gestiona ese stock, así
            // que acá no hay nada que reservar — solo confirmar que el
            // punto de picking existe y está operativo para coordinar el
            // retiro.
            validarPuntoPickingActivo(externo.getIdComercio(), externo.getIdPuntoPicking());
            pedido.setIdPuntoPicking(externo.getIdPuntoPicking());
            pedido.setPesoKg(externo.getPesoKg());
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
                } catch (StockModificadoException e) {
                    // Choque pasajero con otra sesión sobre el mismo item: no
                    // es una regla de negocio, se reintenta (ver la excepción).
                    throw new ConflictoDeStockException("No se pudo comprometer el stock del pedido: " + e.getMessage());
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
        if (externo.getOrigen() != OrigenPedido.PUNTO_PICKING) {
            pedido.setPesoKg(pesoDeStockConsignado(lineasPedido));
        }

        repository.guardarPedido(pedido);
        avisarCambioDeEstado(pedido);

        externo.setSincronizado(true);
        externo.setIdPedido(pedido.getId());
        repository.actualizarPedidoExterno(externo);

        LOG.info("[Pedidos] Sincronizado pedido externo " + idPedidoExterno + " -> pedido "
                + pedido.getId() + " (" + lineasPedido.size() + " línea(s))");
        return pedido.getId();
    }

    // Suma peso por unidad x cantidad de cada línea, con el peso que tiene
    // hoy cada ítem. Si alguno no tiene peso cargado, el total no se conoce
    // y queda null (contarlo como 0 haría pasar por liviano un pedido que
    // no lo es).
    private BigDecimal pesoDeStockConsignado(List<LineaPedido> lineas) {
        Map<Long, BigDecimal> pesoPorItem =
                stock.pesosPorItem(lineas.stream().map(LineaPedido::getIdItem).collect(Collectors.toList()));
        BigDecimal total = BigDecimal.ZERO;
        for (LineaPedido linea : lineas) {
            BigDecimal pesoUnidad = pesoPorItem.get(linea.getIdItem());
            if (pesoUnidad == null) {
                return null;
            }
            total = total.add(pesoUnidad.multiply(BigDecimal.valueOf(linea.getCantidad())));
        }
        return total;
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
    public Long confirmarPedido(Long idPedido) {
        return confirmar(idPedido, null);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Long confirmarPedidoEnZona(Long idPedido, Long idZona) {
        return confirmar(idPedido, idZona);
    }

    // idZona: la zona del pedido (la decide Ruteo); el repartidor se busca
    // primero ahí. Null: cualquier repartidor libre. Devuelve null si el
    // pedido quedó en la lista de espera (ADR-037).
    private Long confirmar(Long idPedido, Long idZona) {
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        validarTransicion(pedido, EstadoPedido.CONFIRMADO);

        // Retiro en depósito (ADR-053): no sale a reparto, así que no hay repartidor, ni lista de
        // espera, ni zona. Se cobra igual (si es prepago) y queda "listo para retirar".
        if (pedido.isRetiroEnDeposito()) {
            try {
                registrarCobroSiFalta(pedido);
            } catch (com.rabbit.pagos.negocio.ValidacionException e) {
                throw new ValidacionException(e.getMessage());
            }
            cambiarEstado(pedido, EstadoPedido.CONFIRMADO);
            return null; // sin repartidor: devuelve null como la lista de espera, pero no queda esperando
        }

        // Antes de cobrar: si no hay a quién dárselo, va a la lista de
        // espera sin cobrar nada (no tiene sentido cobrar en el banco para
        // reversarlo enseguida).
        String motivoEspera = repartidores.motivoParaEsperar(
                pedido.getPesoKg(), pedido.getBultos(), pedido.getIdRepartidorExcluido());
        if (motivoEspera != null) {
            ponerEnEspera(pedido, idZona, motivoEspera);
            return null;
        }

        // Los tres pasos son UNA transacción: si algo falla, el pedido sigue
        // PENDIENTE y no queda cobro ni repartidor asignado en Rabbit. Lo que
        // el rollback NO deshace es el cobro en el banco (sistema externo):
        // si el banco ya autorizó y después no hay repartidor (otro se quedó
        // con el lugar entre la consulta de arriba y la asignación), Pagos
        // pide la reversa al banco (ver ReversasBancarias).
        //
        // Las excepciones de Repartidores y Pagos ya marcan la transacción
        // para rollback (@ApplicationException(rollback = true)); acá solo
        // se traducen a la de Pedidos para que la vista muestre el motivo.
        Long idRepartidor;
        try {
            registrarCobroSiFalta(pedido);
            idRepartidor = repartidores.asignarRepartidor(idPedido, idZona != null ? idZona : pedido.getIdZonaEspera(),
                    pedido.getPesoKg(), pedido.getBultos(), pedido.getIdRepartidorExcluido());
        } catch (com.rabbit.repartidores.negocio.SinLugarException e) {
            throw new SinRepartidorConLugarException(e.getMessage() + ". Probá de nuevo en un momento.");
        } catch (com.rabbit.repartidores.negocio.ValidacionException
                 | com.rabbit.pagos.negocio.ValidacionException e) {
            throw new ValidacionException(e.getMessage());
        }
        pedido.setIdRepartidor(idRepartidor);
        cambiarEstado(pedido, EstadoPedido.CONFIRMADO);
        return idRepartidor;
    }

    // Un pedido devuelto al depósito ya tiene su cobro: no se cobra dos veces.
    private void registrarCobroSiFalta(Pedido pedido) {
        if (pedido.isCobroRegistrado()) {
            return;
        }
        cobros.registrarCobro(pedido.getId(), pedido.getIdComercio(), pedido.getImporte(), pedido.getMedioPago());
        pedido.setCobroRegistrado(true);
    }

    // Si ya estaba esperando, conserva su lugar en la fila (enEsperaDesde).
    // AsignadorDeEspera pasa por acá cada minuto con los que siguen sin
    // lugar: si no cambió nada, no se escribe.
    private void ponerEnEspera(Pedido pedido, Long idZona, String motivo) {
        String texto = aTexto300(motivo);
        boolean yaEsperaba = pedido.isEnEspera();
        if (yaEsperaba && texto.equals(pedido.getMotivoEspera())
                && (idZona == null || idZona.equals(pedido.getIdZonaEspera()))) {
            return;
        }
        if (!yaEsperaba) {
            pedido.setEnEsperaDesde(LocalDateTime.now());
        }
        if (idZona != null) {
            pedido.setIdZonaEspera(idZona);
        }
        pedido.setMotivoEspera(texto);
        repository.actualizarPedido(pedido);
        LOG.info("[Pedidos] Pedido " + pedido.getId() + " en lista de espera: " + motivo);
    }

    // REQUIRES_NEW: AsignadorDeEspera recorre la fila sin transacción y cada
    // pedido se asigna (o falla) en la suya.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public Long asignarDesdeEspera(Long idPedido) {
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE || !pedido.isEnEspera()) {
            return null;
        }
        Long idRepartidor = confirmar(idPedido, null);
        if (idRepartidor != null) {
            LOG.info("[Pedidos] Pedido " + idPedido + " asignado desde la lista de espera al repartidor " + idRepartidor);
        }
        return idRepartidor;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void sacarDeEspera(Long idPedido, String motivo) {
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        if (!pedido.isEnEspera()) {
            return;
        }
        pedido.setEnEsperaDesde(null);
        pedido.setMotivoEspera(aTexto300("No se pudo asignar solo: " + motivo + ". Confirmalo o derivalo a mano."));
        repository.actualizarPedido(pedido);
        LOG.warning("[Pedidos] Pedido " + idPedido + " sale de la lista de espera: " + motivo);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "REPARTIDOR"})
    public void devolverADeposito(Long idPedido, EntregaFallidaDTO datos) {
        MotivoEntregaFallida motivo = datos != null ? datos.motivo : null;
        if (motivo == null) {
            throw new ValidacionException("Elegí por qué no se pudo entregar el pedido");
        }
        // Evidencia (ADR-045): se valida antes de tocar nada.
        String comentario = EvidenciaDeVisita.comentario(motivo, datos.comentario);
        List<EvidenciaDeVisita.Foto> fotos = EvidenciaDeVisita.fotos(motivo, datos.fotos);
        String texto = comentario == null ? "" : comentario;
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        ReglaDeRetiro.exigirADomicilio(pedido.getTipoEntrega(), "devolver al depósito: nunca salió a reparto");
        exigirRepartidorAsignado(pedido);
        if (pedido.getEstado() != EstadoPedido.CONFIRMADO && pedido.getEstado() != EstadoPedido.EN_CAMINO) {
            throw new ValidacionException("El pedido " + idPedido + " está " + pedido.getEstado()
                    + ": solo se devuelve al depósito un pedido en reparto");
        }
        if (pedido.getIdRepartidor() == null) {
            throw new ValidacionException("El pedido " + idPedido
                    + " lo lleva una empresa de logística tercerizada: no se devuelve desde Rabbit");
        }
        Long idRepartidor = pedido.getIdRepartidor();
        String descripcion = aTexto300(texto.isEmpty() ? motivo.getDescripcion()
                : (motivo == MotivoEntregaFallida.OTRO ? texto : motivo.getDescripcion() + ": " + texto));
        int intentosPrevios = pedido.getIntentosFallidos();
        IntentosDeEntrega.Desenlace desenlace = IntentosDeEntrega.evaluar(
                pedido.getEstado() == EstadoPedido.EN_CAMINO, motivo, intentosPrevios,
                IntentosDeEntrega.maximo());
        registrarVisita(pedido, idRepartidor, motivo, comentario, fotos, datos,
                desenlace.intentos() > intentosPrevios ? desenlace.intentos() : 0, desenlace.devolverAlComercio());
        repartidores.liberarRepartidor(idRepartidor, idPedido);

        pedido.setUltimaFallaContoComoVisita(desenlace.intentos() > intentosPrevios);
        pedido.setIntentosFallidos(desenlace.intentos());
        pedido.setMotivoEntregaFallida(motivo);
        pedido.setMotivoDevolucion(descripcion);
        // Se confirmó, así que tiene cobro (también los confirmados antes
        // de que existiera esta marca).
        pedido.setCobroRegistrado(true);

        if (desenlace.devolverAlComercio()) {
            // ADR-041: se agotaron las visitas, el cliente lo rechazó o el
            // paquete está dañado. Conserva el repartidor, para saber quién
            // lo devolvió. El cobro lo anula Pagos al recibir el cambio de
            // estado por el tópico. La mercadería dañada no vuelve al stock
            // (ADR-045): el comercio decide qué hacer con ella.
            if (motivo != MotivoEntregaFallida.PAQUETE_DANADO) {
                devolverStock(pedido);
            }
            cambiarEstado(pedido, EstadoPedido.DEVUELTO_AL_COMERCIO);
            pedido.setFechaUltimaFalla(pedido.getFechaActualizacion());
            LOG.info("[Pedidos] Pedido " + idPedido + " devuelto al comercio después de " + desenlace.intentos()
                    + " visita(s): " + descripcion);
            return;
        }

        pedido.setIdRepartidor(null);
        pedido.setIdRepartidorExcluido(idRepartidor);
        pedido.setEnEsperaDesde(LocalDateTime.now());
        pedido.setMotivoEspera(aTexto300("Devuelto al depósito: " + descripcion));
        cambiarEstado(pedido, EstadoPedido.PENDIENTE);
        // Entidad bloqueada y administrada: se guarda al confirmar la transacción.
        pedido.setFechaUltimaFalla(pedido.getFechaActualizacion());
        LOG.info("[Pedidos] Pedido " + idPedido + " devuelto al depósito por el repartidor " + idRepartidor
                + " (visita " + desenlace.intentos() + " de " + IntentosDeEntrega.maximo() + "): " + descripcion);
    }

    // Guarda la visita fallida con su evidencia (ADR-045). numeroVisita es 0
    // si no contó como visita.
    private void registrarVisita(Pedido pedido, Long idRepartidor, MotivoEntregaFallida motivo, String comentario,
                                 List<EvidenciaDeVisita.Foto> fotos, EntregaFallidaDTO datos, int numeroVisita,
                                 boolean devueltoAlComercio) {
        LocalDateTime ahora = LocalDateTime.now();
        UbicacionDTO ultima = repartidores.ultimaUbicacion(idRepartidor);
        EvidenciaDeVisita.Ubicacion ubicacion = EvidenciaDeVisita.ubicacion(datos.latitud, datos.longitud,
                datos.precisionMetros,
                ultima != null ? ultima.latitud : null, ultima != null ? ultima.longitud : null,
                ultima != null ? ultima.fecha : null, ahora);

        IntentoEntrega intento = new IntentoEntrega();
        intento.setIdPedido(pedido.getId());
        intento.setIdRepartidor(idRepartidor);
        intento.setRegistradoPor(aTexto(contexto.getCallerPrincipal().getName(), 30));
        intento.setFecha(ahora);
        intento.setMotivo(motivo);
        intento.setComentario(comentario);
        intento.setNumeroVisita(numeroVisita);
        intento.setDevueltoAlComercio(devueltoAlComercio);
        if (ubicacion != null) {
            intento.setLatitud(ubicacion.latitud());
            intento.setLongitud(ubicacion.longitud());
            intento.setPrecisionMetros(ubicacion.precisionMetros());
            intento.setOrigenUbicacion(ubicacion.origen());
            intento.setFechaUbicacion(ubicacion.fecha());
        }
        intento.setCantidadFotos(fotos.size());
        repository.guardarIntento(intento);

        for (int i = 0; i < fotos.size(); i++) {
            FotoIntentoEntrega foto = new FotoIntentoEntrega();
            foto.setIdIntento(intento.getId());
            foto.setIdPedido(pedido.getId());
            foto.setOrden(i + 1);
            foto.setTipoContenido(fotos.get(i).tipo());
            foto.setImagen(fotos.get(i).imagen());
            foto.setTamanoBytes(fotos.get(i).imagen().length);
            repository.guardarFotoDeIntento(foto);
        }
    }

    private static String aTexto(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
    public List<IntentoEntregaDTO> listarIntentosDeEntrega(Long idPedido) {
        Pedido pedido = idPedido != null ? repository.buscarPedidoPorId(idPedido) : null;
        if (pedido == null || !puedeVerRemito(pedido)) {
            throw new PedidoNoEncontradoException("Pedido no encontrado: " + idPedido);
        }
        List<IntentoEntregaDTO> intentos = repository.listarIntentosDePedido(idPedido).stream()
                .map(IntentoEntregaDTO::desde)
                .collect(Collectors.toList());
        Map<Long, IntentoEntregaDTO> porId = intentos.stream()
                .collect(Collectors.toMap(IntentoEntregaDTO::getId, i -> i));
        for (Object[] fila : repository.listarIdsDeFotos(new ArrayList<>(porId.keySet()))) {
            porId.get((Long) fila[0]).idsFotos.add((Long) fila[1]);
        }
        return intentos;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
    public FotoDeVisitaDTO obtenerFotoDeVisita(Long idFoto) {
        FotoIntentoEntrega foto = idFoto != null ? repository.buscarFotoDeIntento(idFoto) : null;
        Pedido pedido = foto != null ? repository.buscarPedidoPorId(foto.getIdPedido()) : null;
        // Mismo criterio que el remito; sin REPARTIDOR en @RolesAllowed: al
        // devolverlo deja de tenerlo asignado.
        if (pedido == null || !puedeVerRemito(pedido)) {
            throw new PedidoNoEncontradoException("No existe la foto " + idFoto);
        }
        return new FotoDeVisitaDTO(foto.getId(), foto.getIdPedido(), foto.getTipoContenido(), foto.getImagen());
    }

    // Las columnas de la espera y la devolución son de 300.
    private static String aTexto300(String texto) {
        return texto.length() <= 300 ? texto : texto.substring(0, 297) + "...";
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public String derivarATransportista(Long idPedido, Long idTransportista) {
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        ReglaDeRetiro.exigirADomicilio(pedido.getTipoEntrega(), "derivar a un transportista");
        validarTransicion(pedido, EstadoPedido.CONFIRMADO);
        if (idTransportista == null) {
            throw new ValidacionException("Elegí a qué empresa de logística derivar el pedido");
        }

        // Mismo esquema que confirmarPedido, con el transportista en lugar
        // del repartidor: todo en una transacción. Si el transportista ya
        // tomó el envío y después algo falla, se lo cancela (compensación,
        // ver CancelacionesDeEnvios); si el banco ya cobró, se reversa.
        DatosEnvioDTO datos = datosDeEnvio(pedido);
        String codigoSeguimiento;
        try {
            registrarCobroSiFalta(pedido);
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

    // Lo mismo que se le mandaría al transportista al derivar, así la
    // cotización corresponde exactamente al envío que después se pide.
    // NOT_SUPPORTED: no escribe nada, y así no retiene una transacción (ni
    // una conexión a la base) mientras espera a los transportistas. Por eso
    // el pedido se lee con sus líneas en una sola consulta.
    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<CotizacionDTO> cotizarDerivacion(Long idPedido) {
        Pedido pedido = repository.buscarPedidoConLineas(idPedido);
        if (pedido == null) {
            throw new PedidoNoEncontradoException("Pedido no encontrado: " + idPedido);
        }
        ReglaDeRetiro.exigirADomicilio(pedido.getTipoEntrega(), "cotizar un envío");
        validarTransicion(pedido, EstadoPedido.CONFIRMADO);
        return envios.cotizarEnvio(idPedido, datosDeEnvio(pedido));
    }

    private DatosEnvioDTO datosDeEnvio(Pedido pedido) {
        DatosEnvioDTO datos = new DatosEnvioDTO();
        datos.direccionRetiro = direccionDeRetiro(pedido);
        datos.direccionEntrega = pedido.getDireccionEntrega();
        datos.bultos = pedido.getLineas().stream().mapToInt(LineaPedido::getCantidad).sum();
        datos.cobrarAlEntregar = pedido.getMedioPago() == MedioPago.CONTRA_ENTREGA ? pedido.getImporte() : null;
        return datos;
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
                .map(d -> d.getEtiqueta() + " — " + d.getDireccion() + ", " + d.getLocalidad())
                .collect(Collectors.joining("; "));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "REPARTIDOR"})
    public void despacharPedido(Long idPedido) {
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        ReglaDeRetiro.exigirADomicilio(pedido.getTipoEntrega(), "despachar");
        exigirRepartidorAsignado(pedido);
        cambiarEstado(pedido, EstadoPedido.EN_CAMINO);
    }

    // Con prueba: la entrega dispara el cobro contra entrega, así que no
    // alcanza con un clic (ADR-033). OPERADOR no: la marca quien estuvo ahí
    // (el repartidor) o, como contingencia, un ADMINISTRADOR.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "REPARTIDOR"})
    public void registrarEntrega(Long idPedido, byte[] imagen) {
        String tipo = Remitos.tipoDeImagen(imagen);
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        ReglaDeRetiro.exigirADomicilio(pedido.getTipoEntrega(), "registrar una entrega con remito: se entrega en el depósito");
        exigirRepartidorAsignado(pedido);
        if (pedido.getIdRepartidor() == null) {
            throw new ValidacionException("El pedido " + idPedido
                    + " lo lleva una empresa de logística tercerizada: su entrega la informa la empresa");
        }
        String usuario = contexto.getCallerPrincipal().getName();
        LocalDateTime ahora = LocalDateTime.now();

        RemitoEntrega remito = new RemitoEntrega();
        remito.setIdPedido(idPedido);
        remito.setTipoContenido(tipo);
        remito.setImagen(imagen);
        remito.setTamanoBytes(imagen.length);
        remito.setFechaCarga(ahora);
        remito.setCargadoPor(usuario);
        repository.guardarRemito(remito);

        pedido.setConRemito(true);
        marcarEntregado(pedido, usuario, ahora);
    }

    // Retiro en depósito (ADR-053): quien retira muestra el código de seguimiento y el personal
    // del depósito lo ingresa. Es la prueba de la entrega: no hay repartidor ni foto de remito.
    // Como cualquier entrega, dispara el cobro contra entrega (lo efectiviza Pagos al recibir ENTREGADO).
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void entregarEnDeposito(Long idPedido, String codigoMostrado) {
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        ReglaDeRetiro.validarEntregaEnDeposito(pedido.getTipoEntrega(), pedido.getEstado());
        if (!ReglaDeRetiro.codigoCoincide(pedido.getCodigoSeguimiento(), codigoMostrado)) {
            // No se aclara cuál es el correcto ni qué pedido es: quien retira tiene que mostrarlo.
            throw new ValidacionException("El código no coincide con el del pedido " + idPedido);
        }
        marcarEntregado(pedido, contexto.getCallerPrincipal().getName(), LocalDateTime.now());
    }

    // Lo dispara el seguimiento de envíos (timer con @RunAs): sin foto
    // porque la prueba de entrega es del transportista. Un pedido con
    // repartidor propio no puede entrar por acá sin remito.
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void registrarEntregaDeTransportista(Long idPedido) {
        Pedido pedido = obtenerParaActualizarOFallar(idPedido);
        ReglaDeRetiro.exigirADomicilio(pedido.getTipoEntrega(), "registrar la entrega de un transportista");
        if (pedido.getIdRepartidor() != null) {
            throw new ValidacionException("El pedido " + idPedido
                    + " lo lleva un repartidor de Rabbit: la entrega se registra con la foto del remito");
        }
        marcarEntregado(pedido, "transportista", LocalDateTime.now());
    }

    // El cobro CONTRA_ENTREGA NO se hace acá: lo efectiviza Pagos al
    // recibir ENTREGADO por el tópico (ver SuscriptorPagosEstadoPedido).
    private void marcarEntregado(Pedido pedido, String usuario, LocalDateTime cuando) {
        pedido.setFechaEntrega(cuando);
        pedido.setEntregadoPor(usuario);
        cambiarEstado(pedido, EstadoPedido.ENTREGADO);
        repartidores.liberarRepartidor(pedido.getIdRepartidor(), pedido.getId());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "COMERCIO", "REPARTIDOR"})
    public RemitoDTO obtenerRemito(Long idPedido) {
        Pedido pedido = idPedido != null ? repository.buscarPedidoPorId(idPedido) : null;
        RemitoEntrega remito = pedido != null && puedeVerRemito(pedido) ? repository.buscarRemitoDePedido(idPedido) : null;
        if (remito == null) {
            throw new PedidoNoEncontradoException("No hay remito para el pedido " + idPedido);
        }
        return new RemitoDTO(idPedido, remito.getTipoContenido(), remito.getImagen());
    }

    private boolean puedeVerRemito(Pedido pedido) {
        if (contexto.isCallerInRole("ADMINISTRADOR") || contexto.isCallerInRole("OPERADOR")) {
            return true;
        }
        try {
            if (contexto.isCallerInRole("COMERCIO")) {
                return pedido.getIdComercio().equals(contextoUsuario.idComercioActual());
            }
            if (contexto.isCallerInRole("REPARTIDOR")) {
                return contextoUsuario.idRepartidorActual().equals(pedido.getIdRepartidor());
            }
        } catch (com.rabbit.seguridad.negocio.ValidacionException e) {
            // Cuenta sin comercio o repartidor asociado: no ve nada.
        }
        return false;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public void cancelarPedido(Long idPedido) {
        cancelar(obtenerParaActualizarOFallar(idPedido));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ERP")
    public PedidoExternoDTO cancelarPedidoExterno(Long idPedidoExterno) {
        // Con bloqueo: no puede cruzarse con la sincronización de la misma fila.
        PedidoExterno externo = repository.buscarPedidoExternoParaActualizar(idPedidoExterno);
        verificarQueEsDelErp(externo, idPedidoExterno);

        if (!externo.isSincronizado()) {
            // Todavía no es un pedido: se cierra la fila y la sincronización
            // la va a saltear (ver PedidoYaSincronizadoException).
            externo.setSincronizado(true);
            externo.setCancelado(true);
            externo.setErrorSincronizacion("Cancelado por el comercio");
            repository.actualizarPedidoExterno(externo);
            LOG.info("[Pedidos] Pedido externo " + idPedidoExterno + " cancelado por el ERP antes de sincronizarse");
        } else if (externo.getIdPedido() != null) {
            Pedido pedido = obtenerParaActualizarOFallar(externo.getIdPedido());
            String noSePuede = ReglaDeCancelacion.motivoParaNoCancelar(
                    pedido.getId(), pedido.getEstado(), pedido.isSalioAReparto());
            if (pedido.getEstado() == EstadoPedido.CANCELADO) {
                // Ya cancelado: idempotente, no queda nada que hacer.
            } else if (noSePuede != null) {
                throw new CancelacionNoPermitidaException(noSePuede);
            } else if (pedido.getEstado() == EstadoPedido.PENDIENTE && pedido.isCobroRegistrado()) {
                // Devuelto sin haber salido (por ejemplo, no entraba en el
                // vehículo): ya tiene cobro, que solo anula el personal.
                throw new CancelacionNoPermitidaException("El pedido " + pedido.getId()
                        + " ya fue confirmado y tiene su cobro: lo puede cancelar el personal de Rabbit.");
            } else if (pedido.getEstado() == EstadoPedido.PENDIENTE) {
                cancelar(pedido);
                LOG.info("[Pedidos] Pedido " + pedido.getId() + " cancelado por el ERP");
            } else if (pedido.getEstado() != EstadoPedido.CANCELADO) {
                throw new CancelacionNoPermitidaException("El pedido " + pedido.getId() + " está "
                        + pedido.getEstado() + ": el comercio ya no puede cancelarlo por la API. "
                        + porQueNoSeCancela(pedido.getEstado()));
            }
        }
        // Ya cancelado o descartado: no queda nada que cancelar.
        return aDTOConPedido(externo);
    }

    // Qué le queda al ERP según hasta dónde llegó el pedido: solo un
    // CONFIRMADO lo puede cancelar todavía el personal.
    private static String porQueNoSeCancela(EstadoPedido estado) {
        switch (estado) {
            case EN_CAMINO: return "Ya salió a reparto: no se puede cancelar.";
            case ENTREGADO: return "Ya fue entregado: no se puede cancelar.";
            case DEVUELTO_AL_COMERCIO: return "No se pudo entregar y ya volvió al comercio.";
            default: return "Si todavía no salió, lo puede cancelar el personal de Rabbit.";
        }
    }

    // Cancela un pedido devolviendo lo que tenía comprometido. Lo usan el
    // personal (cancelarPedido) y el ERP (cancelarPedidoExterno, solo PENDIENTE).
    private void cancelar(Pedido pedido) {
        Long idPedido = pedido.getId();
        // Ya salió a reparto (o terminó): no se cancela, con un mensaje claro
        // en lugar del "no puede pasar a CANCELADO" de la máquina de estados.
        String noSePuede = ReglaDeCancelacion.motivoParaNoCancelar(idPedido, pedido.getEstado(), pedido.isSalioAReparto());
        if (noSePuede != null) {
            throw new CancelacionNoPermitidaException(noSePuede);
        }
        // Se valida antes de devolver el stock: si el pedido ya está
        // EN_CAMINO o ENTREGADO, la mercadería no está para devolverla.
        validarTransicion(pedido, EstadoPedido.CANCELADO);

        // Un pedido CONFIRMADO ya tiene cobro y repartidor. Anular el cobro
        // exige rol ADMINISTRADOR (@RolesAllowed en PagoService.anularCobro):
        // un OPERADOR recibe EJBAccessException y no se cancela nada.
        if (pedido.getEstado() == EstadoPedido.CONFIRMADO) {
            cobros.anularCobro(idPedido);
            repartidores.liberarRepartidor(pedido.getIdRepartidor(), idPedido);
            // Si lo llevaba un transportista, se le cancela el envío (se le
            // avisa recién cuando esta cancelación queda confirmada).
            envios.cancelarEnvioDePedido(idPedido);
        } else if (pedido.isCobroRegistrado()) {
            // PENDIENTE pero devuelto al depósito: conserva el cobro de
            // cuando se confirmó.
            cobros.anularCobro(idPedido);
        }

        devolverStock(pedido);
        cambiarEstado(pedido, EstadoPedido.CANCELADO);
    }

    // El stock de cada línea con reserva se descontó al sincronizarla
    // (reservar + confirmar juntos). Cancelar sin devolverlo dejaría la
    // mercadería del comercio "consumida" por un pedido que nunca se
    // despachó, así que hay que revertir esa confirmación contra
    // ServicioDeInventario — una línea de PUNTO_PICKING no tiene
    // idReservaStock (nunca reservó nada) y se salta. Lo mismo cuando el
    // pedido vuelve al comercio sin entregarse (ADR-041): la mercadería
    // vuelve a su stock consignado.
    private void devolverStock(Pedido pedido) {
        for (LineaPedido linea : pedido.getLineas()) {
            if (linea.getIdReservaStock() == null) {
                continue;
            }
            IReservaStock reserva = reservaProvider.get();
            try {
                reserva.registrarDevolucion(linea.getIdReservaStock());
            } catch (StockModificadoException e) {
                // Choque pasajero con otra sesión sobre el mismo item: con
                // reintentar alcanza (ver ConflictoDeStockException).
                throw new ConflictoDeStockException(
                        "No se pudo devolver el stock del pedido: " + e.getMessage());
            } catch (RuntimeException e) {
                throw new ValidacionException(
                        "No se pudo devolver el stock del pedido: " + e.getMessage());
            } finally {
                reservaProvider.destroy(reserva);
            }
        }
    }

    // ===============================================================
    // ISeguimientoPedido
    // ===============================================================

    // Devuelve el pedido como DTO (nunca expone la entidad directamente)
    @Override
    // Sin restricción de rol: la usan Ruteo y el seguimiento de envíos,
    // que corre sin usuario. El seguimiento público ya no entra por acá
    // (el ID es secuencial) sino por consultarSeguimiento.
    public PedidoDTO consultarEstadoPedido(Long idPedido) {
        return PedidoDTO.desde(obtenerOFallar(idPedido));
    }

    @Override
    // Pública a propósito: el seguimiento sin login (/api/v1/seguimiento)
    // entra por el código aleatorio, no por el ID secuencial.
    public PedidoDTO consultarSeguimiento(String codigoSeguimiento) {
        // Normalizado acá, en el negocio: la página y la API aceptan lo mismo
        // ("rb-7kq2m9xhta", con espacios de más, etc.).
        String codigo = codigoSeguimiento == null ? null : codigoSeguimiento.trim().toUpperCase();
        Pedido pedido = codigo == null || codigo.isEmpty() ? null : repository.buscarPedidoPorCodigoSeguimiento(codigo);
        if (pedido == null) {
            throw new PedidoNoEncontradoException("No hay ningún pedido con el código " + codigoSeguimiento);
        }
        PedidoDTO dto = PedidoDTO.desde(pedido);
        if (pedido.isRetiroEnDeposito() && pedido.getIdDepositoRetiro() != null) {
            // Dónde retirarlo (ADR-053): el seguimiento público lo muestra junto al código.
            DepositoDTO deposito = stock.obtenerDeposito(pedido.getIdDepositoRetiro());
            dto.depositoRetiro = ReglaDeRetiro.textoDeLaDireccion(deposito.getNombre(), deposito.getDireccion())
                    .substring(ReglaDeRetiro.DIRECCION_PROVISORIA.length() + 2);
        }
        return dto;
    }

    @Override
    // Pública a propósito, como consultarSeguimiento: entra por el código aleatorio.
    public CalificacionDTO consultarCalificacion(String codigoSeguimiento) {
        Pedido pedido = pedidoPorCodigoOFallar(codigoSeguimiento);
        CalificacionEntrega calificacion = repository.buscarCalificacionDePedido(pedido.getId());
        return calificacion == null ? null : CalificacionDTO.desde(calificacion);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    // Pública a propósito (ADR-052): quien recibe el pedido no tiene cuenta; lo que lo
    // autoriza es el código aleatorio del pedido. Solo escribe una calificación, una vez,
    // de un pedido ya entregado; el comentario es texto de un tercero (se valida y se
    // muestra siempre escapado) y no se escribe en el log.
    public CalificacionDTO calificarEntrega(String codigoSeguimiento, int estrellas, String comentario) {
        Pedido pedido = pedidoPorCodigoOFallar(codigoSeguimiento);
        String comentarioLimpio = ReglaDeCalificacion.validar(pedido.getEstado(), estrellas, comentario);
        if (repository.buscarCalificacionDePedido(pedido.getId()) != null) {
            throw new ValidacionException("Esta entrega ya fue calificada. ¡Gracias!");
        }
        CalificacionEntrega calificacion = new CalificacionEntrega(pedido.getId(), estrellas, comentarioLimpio,
                LocalDateTime.now());
        try {
            repository.guardarCalificacion(calificacion);
        } catch (PersistenceException e) {
            // Dos calificaciones a la vez del mismo pedido: gana la primera (idPedido es único).
            throw new ValidacionException("Esta entrega ya fue calificada. ¡Gracias!");
        }
        LOG.info("[Pedidos] Entrega del pedido " + pedido.getId() + " calificada con " + estrellas + " estrellas");
        return CalificacionDTO.desde(calificacion);
    }

    // Busca el pedido por su código de seguimiento, normalizado como en consultarSeguimiento.
    private Pedido pedidoPorCodigoOFallar(String codigoSeguimiento) {
        String codigo = codigoSeguimiento == null ? null : codigoSeguimiento.trim().toUpperCase();
        Pedido pedido = codigo == null || codigo.isEmpty() ? null : repository.buscarPedidoPorCodigoSeguimiento(codigo);
        if (pedido == null) {
            throw new PedidoNoEncontradoException("No hay ningún pedido con el código " + codigoSeguimiento);
        }
        return pedido;
    }

    // Devuelve todos los pedidos reales como DTO — usado por la vista de listado
    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<PedidoDTO> listarTodos() {
        return repository.listarTodos().stream().map(PedidoDTO::desde).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<PedidoDTO> listarPendientes() {
        // Los retiros en depósito no salen a reparto: no entran al ruteo (ADR-053).
        return repository.listarPorEstado(EstadoPedido.PENDIENTE).stream()
                .filter(p -> !p.isRetiroEnDeposito())
                .map(PedidoDTO::desde).collect(Collectors.toList());
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

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<PedidoDTO> listarEntregasEnCursoDeRepartidor(Long idRepartidor) {
        return repository.listarPedidosDeRepartidor(idRepartidor).stream()
                .filter(p -> p.getEstado() == EstadoPedido.CONFIRMADO || p.getEstado() == EstadoPedido.EN_CAMINO)
                .sorted(java.util.Comparator.comparing(Pedido::getId))
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

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<ResumenEntregasDTO> resumenEntregas(LocalDateTime desde, LocalDateTime hasta) {
        return repository.resumenEntregas(desde, hasta).stream()
                .map(fila -> new ResumenEntregasDTO((Long) fila[0], ((Number) fila[1]).intValue(),
                        fila[2] != null ? (BigDecimal) fila[2] : BigDecimal.ZERO))
                .collect(Collectors.toList());
    }

    @Override
    @RolesAllowed("COMERCIO")
    public ResumenEntregasDTO resumenEntregasDelComercioActual(LocalDateTime desde, LocalDateTime hasta) {
        Long idComercio = contextoUsuario.idComercioActual();
        return resumenEntregas(desde, hasta).stream()
                .filter(r -> r.idComercio().equals(idComercio))
                .findFirst()
                .orElse(new ResumenEntregasDTO(idComercio, 0, BigDecimal.ZERO));
    }

    // Todas las filas del mock del ERP (sincronizadas y pendientes) —
    // deja ver en la vista el "antes y después" de la sincronización.
    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR", "ERP"})
    public PedidoExternoDTO consultarPedidoExterno(Long idPedidoExterno) {
        PedidoExterno externo = repository.buscarPedidoExternoPorId(idPedidoExterno);
        verificarQueEsDelErp(externo, idPedidoExterno);
        return aDTOConPedido(externo);
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<PedidoExternoDTO> listarPedidosExternos() {
        return repository.listarTodosLosExternos().stream()
                .map(PedidoExternoDTO::desde)
                .collect(Collectors.toList());
    }

    // --- privados ---

    // Un ERP solo ve los pedidos externos de su comercio. Uno ajeno se
    // informa como inexistente: ni siquiera se confirma que existe.
    private void verificarQueEsDelErp(PedidoExterno externo, Long idPedidoExterno) {
        boolean ajeno = externo != null && contexto.isCallerInRole("ERP")
                && !externo.getIdComercio().equals(idComercioDelErp());
        if (externo == null || ajeno) {
            throw new PedidoNoEncontradoException("Pedido externo no encontrado: " + idPedidoExterno);
        }
    }

    // Traduce la excepción de Seguridad a la propia de este componente
    // (mismo criterio que con Inventario en sincronizarPedidoExterno).
    private Long idComercioDelErp() {
        try {
            return contextoUsuario.idComercioActual();
        } catch (com.rabbit.seguridad.negocio.ValidacionException e) {
            throw new CuentaErpSinComercioException(
                    "Tu usuario ERP no está asociado a ningún comercio: pedile a Rabbit que lo dé de alta desde la app");
        }
    }

    // El DTO de la fila del ERP más el estado y el código de seguimiento del
    // pedido real, si ya se generó: es lo que el ERP necesita para seguirlo.
    private PedidoExternoDTO aDTOConPedido(PedidoExterno externo) {
        PedidoExternoDTO dto = PedidoExternoDTO.desde(externo);
        if (externo.getIdPedido() != null) {
            Pedido pedido = repository.buscarPedidoPorId(externo.getIdPedido());
            if (pedido != null) {
                dto.estadoPedido = pedido.getEstado().name();
                dto.codigoSeguimiento = pedido.getCodigoSeguimiento();
                dto.enListaDeEspera = pedido.getEstado() == EstadoPedido.PENDIENTE && pedido.isEnEspera();
                dto.devueltoADeposito = pedido.getEstado() == EstadoPedido.PENDIENTE && pedido.isCobroRegistrado();
                dto.intentosFallidos = pedido.getIntentosFallidos();
            }
        }
        return dto;
    }

    // Lanza excepción si el pedido no existe — evita repetir este chequeo
    // en cada método que opera sobre uno puntual.
    // Para las operaciones que cambian el estado: bloquea la fila del pedido
    // hasta el fin de la transacción (ver PedidoRepository.buscarPedidoParaActualizar).
    private Pedido obtenerParaActualizarOFallar(Long id) {
        Pedido pedido = repository.buscarPedidoParaActualizar(id);
        if (pedido == null) {
            throw new PedidoNoEncontradoException("Pedido no encontrado: " + id);
        }
        return pedido;
    }

    private Pedido obtenerOFallar(Long id) {
        Pedido pedido = repository.buscarPedidoPorId(id);
        if (pedido == null) {
            throw new PedidoNoEncontradoException("Pedido no encontrado: " + id);
        }
        return pedido;
    }

    // Lanza excepción si el pedido externo (mock del ERP) no existe. Lo
    // devuelve bloqueado hasta el fin de la transacción (ver
    // PedidoRepository.buscarPedidoExternoParaActualizar).
    private PedidoExterno obtenerExternoParaActualizarOFallar(Long id) {
        PedidoExterno externo = repository.buscarPedidoExternoParaActualizar(id);
        if (externo == null) {
            throw new PedidoNoEncontradoException("Pedido externo no encontrado: " + id);
        }
        return externo;
    }

    // Único lugar donde cambia el estado de un pedido ya existente: valida
    // la transición contra la máquina de estados (EstadoPedido.puedePasarA)
    // y avisa el cambio.
    private void cambiarEstado(Pedido pedido, EstadoPedido destino) {
        validarTransicion(pedido, destino);
        if (destino != EstadoPedido.PENDIENTE) {
            // Confirmado, derivado o cancelado: ya no espera repartidor.
            pedido.setEnEsperaDesde(null);
            pedido.setMotivoEspera(null);
            pedido.setIdZonaEspera(null);
        }
        if (destino == EstadoPedido.EN_CAMINO) {
            // Desde acá ya no se puede cancelar (ReglaDeCancelacion).
            pedido.setSalioAReparto(true);
        }
        pedido.setEstado(destino);
        pedido.setFechaActualizacion(LocalDateTime.now());
        repository.actualizarPedido(pedido);
        avisarCambioDeEstado(pedido);
    }

    private void validarTransicion(Pedido pedido, EstadoPedido destino) {
        if (!pedido.getEstado().puedePasarA(destino, pedido.getTipoEntrega())) {
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
