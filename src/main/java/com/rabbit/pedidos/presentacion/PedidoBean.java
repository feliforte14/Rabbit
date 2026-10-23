package com.rabbit.pedidos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 *
 * El combo comercio→depósito→ítem para "simular pedido nuevo" sigue el
 * mismo patrón en cascada que ReservaBean: getListaItems() se recalcula
 * en vivo a partir del comercio y el depósito elegidos en cada render, en
 * vez de depender de que el listener del f:ajax se haya disparado — así
 * el combo queda correcto incluso si ese postback puntual no llegó a
 * invocar el listener.
 *
 * Solo ofrece los ítems del comercio elegido: un pedido del ERP de Kiosco
 * El Sol nunca referenciaría stock de otro comercio, y si igual llegara
 * una combinación incoherente, InventarioService.reservarStock la
 * rechaza al sincronizar.
 */

import com.rabbit.infraestructura.ArchivosSubidos;
import com.rabbit.infraestructura.Mensajes;
import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.PuntoPickingDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.DatosLineaPedidoDTO;
import com.rabbit.pedidos.dto.LineaPedidoDTO;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.ValidacionException;
import com.rabbit.pagos.dto.CobroDTO;
import com.rabbit.pagos.negocio.IConsultaCobros;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;

import com.rabbit.transportistas.dto.CotizacionDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import com.rabbit.transportistas.negocio.IEnvios;
import com.rabbit.transportistas.negocio.IGestionTransportistas;
import java.util.function.Function;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named
@ViewScoped
public class PedidoBean implements Serializable {

    @Inject
    private IGestionPedidos gestion;

    @Inject
    private ISeguimientoPedido seguimiento;

    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IConsultaStock stock;

    @Inject
    private IGestionRepartidores repartidores;

    @Inject
    private IConsultaCobros cobros;

    // Derivación a transportistas externos.
    @Inject
    private IGestionTransportistas transportistas;

    @Inject
    private IEnvios envios;

    private List<PedidoDTO> pedidos;
    private List<PedidoExternoDTO> pedidosExternos;
    // Nombre de cada ítem de stock que piden los pedidos del ERP: las líneas
    // de stock consignado traen solo el ID del ítem (ver descripcionLineas).
    private Map<Long, String> productosPorItem;
    private List<ComercioDTO> listaComercios;
    private List<DepositoDTO> listaDepositos;
    // Datos de otros componentes para mostrar junto a cada pedido. Se cargan
    // una vez por render en mapas, en vez de consultar fila por fila.
    private Map<Long, String> nombresRepartidores;
    private Map<Long, String> estadosCobro;
    private Map<Long, EnvioDTO> enviosPorPedido;
    private List<TransportistaDTO> listaTransportistas;
    private Long idPedidoADerivar;

    // Formulario "Registrar una entrega" (contingencia del ADMINISTRADOR).
    private Long idPedidoAEntregar;
    private transient jakarta.servlet.http.Part fotoRemito;
    private Long idTransportistaElegido;
    // Cotizaciones del último pedido cotizado. Se guarda cuál fue, así
    // "Derivar con este" deriva ESE pedido aunque después se haya elegido
    // otro en el desplegable.
    private List<CotizacionDTO> cotizaciones;
    private Long idPedidoCotizado;

    private Long idDepositoSeleccionado;
    private DatosPedidoExternoDTO nuevoPedido = nuevoPedidoVacio();

    // @PostConstruct: corre una sola vez al crear el Bean, así las tres
    // tablas de pedidos.xhtml (reales, mock del ERP, combos de alta) ya
    // llegan llenas en el primer render.
    @PostConstruct
    public void cargar() {
        ubicacionesDeEntrega = null;
        pedidos = seguimiento.listarTodos();
        pedidosExternos = seguimiento.listarPedidosExternos();
        List<Long> idsItems = pedidosExternos.stream().flatMap(pe -> pe.getLineas().stream())
                .map(LineaPedidoDTO::getIdItem).filter(java.util.Objects::nonNull).distinct()
                .collect(Collectors.toList());
        productosPorItem = idsItems.isEmpty() ? Map.of() : stock.listarItemsPorIds(idsItems).stream()
                .collect(Collectors.toMap(ItemInventarioDTO::getId, ItemInventarioDTO::getProducto));
        listaComercios = comercios.listarTodos();
        listaDepositos = stock.listarDepositos();
        nombresRepartidores = repartidores.listarTodos().stream()
                .collect(Collectors.toMap(RepartidorDTO::getId, RepartidorDTO::getNombre));
        estadosCobro = cobros.listarTodos().stream()
                .collect(Collectors.toMap(CobroDTO::getIdPedido, CobroDTO::getEstado));
        enviosPorPedido = envios.listarEnvios().stream()
                .collect(Collectors.toMap(EnvioDTO::getIdPedido, Function.identity(), (a, b) -> a));
        listaTransportistas = transportistas.listarActivos();
    }

    // El envío de un pedido derivado a un transportista, o null si lo lleva
    // (o lo va a llevar) un repartidor propio.
    public EnvioDTO envio(Long idPedido) {
        return enviosPorPedido.get(idPedido);
    }

    // Para el resumen de arriba de la pantalla (pedidos por estado).
    public long contarPedidos(String estado) {
        return pedidos.stream().filter(p -> estado.equals(p.getEstado())).count();
    }

    // Los que se pueden derivar: los PENDIENTE.
    public List<PedidoDTO> getPedidosPendientes() {
        return pedidos.stream().filter(p -> "PENDIENTE".equals(p.getEstado())).collect(Collectors.toList());
    }

    // Pregunta a cada transportista activo cuánto cobraría por el pedido
    // elegido, sin pedirle el envío (ver IGestionPedidos.cotizarDerivacion).
    public void cotizar() {
        try {
            cotizaciones = gestion.cotizarDerivacion(idPedidoADerivar);
            idPedidoCotizado = idPedidoADerivar;
            if (cotizaciones.isEmpty()) {
                Mensajes.error("No hay empresas de logística activas para cotizar");
            }
        } catch (ValidacionException e) {
            cotizaciones = null;
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            cotizaciones = null;
            Mensajes.error("No se pudo cotizar el pedido. Intentá de nuevo.");
        }
    }

    // Deriva el pedido cotizado al transportista de esa fila.
    public void derivarCon(Long idTransportista) {
        idPedidoADerivar = idPedidoCotizado;
        idTransportistaElegido = idTransportista;
        derivar();
    }

    // PENDIENTE -> CONFIRMADO con un transportista externo en lugar de un
    // repartidor propio (ver IGestionPedidos.derivarATransportista).
    public void derivar() {
        try {
            String codigo = gestion.derivarATransportista(idPedidoADerivar, idTransportistaElegido);
            String transportista = listaTransportistas.stream()
                    .filter(t -> t.getId().equals(idTransportistaElegido))
                    .map(TransportistaDTO::getNombre).findFirst().orElse("la empresa de logística");
            Mensajes.info("Pedido " + idPedidoADerivar + " derivado a " + transportista + " (seguimiento " + codigo + ")");
            idPedidoADerivar = null;
            idTransportistaElegido = null;
            cotizaciones = null;
            idPedidoCotizado = null;
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
            return;
        } catch (EJBException e) {
            Mensajes.error("No se pudo derivar el pedido. Intentá de nuevo.");
            return;
        }
        // La derivación ya quedó hecha: si falla solo la recarga, no se
        // informa como si hubiera fallado la derivación.
        try {
            cargar();
        } catch (EJBException e) {
            Mensajes.error("El pedido se derivó, pero no se pudo actualizar la lista: recargá la página.");
        }
    }

    public String nombreRepartidor(Long idRepartidor) {
        return idRepartidor == null ? "—" : nombresRepartidores.getOrDefault(idRepartidor, "#" + idRepartidor);
    }

    // Dónde estaba el repartidor al entregar (ADR-055), una consulta por pedido y solo si se pide.
    private transient Map<Long, java.util.Optional<com.rabbit.pedidos.dto.UbicacionEntregaDTO>> ubicacionesDeEntrega;

    public com.rabbit.pedidos.dto.UbicacionEntregaDTO ubicacionEntrega(Long idPedido) {
        if (ubicacionesDeEntrega == null) {
            ubicacionesDeEntrega = new java.util.HashMap<>();
        }
        return ubicacionesDeEntrega.computeIfAbsent(idPedido,
                id -> java.util.Optional.ofNullable(seguimiento.consultarUbicacionDeEntrega(id))).orElse(null);
    }

    public String estadoCobro(Long idPedido) {
        return estadosCobro.get(idPedido);
    }

    // Nombre del comercio para las tablas: antes se mostraba el ID.
    public String nombreComercio(Long idComercio) {
        return listaComercios.stream()
                .filter(c -> c.getId().equals(idComercio))
                .map(ComercioDTO::getNombre)
                .findFirst()
                .orElse("Comercio " + idComercio);
    }

    // Nombre del punto de picking de un pedido del ERP. Sale de la lista de
    // comercios ya cargada (trae sus puntos de picking): no consulta la base
    // por cada fila, y un comercio eliminado no rompe la pantalla.
    public String nombrePuntoPicking(Long idComercio, Long idPuntoPicking) {
        if (idPuntoPicking == null) {
            return "—";
        }
        return listaComercios.stream()
                .filter(c -> c.getId().equals(idComercio) && c.getPuntosPicking() != null)
                .flatMap(c -> c.getPuntosPicking().stream())
                .filter(pp -> pp.id.equals(idPuntoPicking))
                .map(pp -> pp.nombre)
                .findFirst()
                .orElse("Punto " + idPuntoPicking);
    }


    // Un pedido nuevo siempre arranca con una línea vacía, así el
    // formulario ya muestra la primera fila sin que el usuario tenga que
    // tocar "Agregar producto".
    private static DatosPedidoExternoDTO nuevoPedidoVacio() {
        DatosPedidoExternoDTO datos = new DatosPedidoExternoDTO();
        datos.getLineas().add(new DatosLineaPedidoDTO());
        return datos;
    }

    // Agrega una línea de producto vacía al pedido en curso — la usa el
    // botón "Agregar producto" del formulario.
    public void agregarLinea() {
        nuevoPedido.getLineas().add(new DatosLineaPedidoDTO());
    }

    // Quita una línea puntual del pedido en curso. Nunca deja la lista
    // vacía: un pedido sin líneas no tiene nada que sincronizar.
    public void quitarLinea(DatosLineaPedidoDTO linea) {
        if (nuevoPedido.getLineas().size() > 1) {
            nuevoPedido.getLineas().remove(linea);
        }
    }

    /**
     * Ítems del comercio y depósito elegidos — recalculado en cada render.
     *
     * A diferencia de ReservaBean, acá el depósito NO es un filtro
     * opcional: un pedido de stock consignado tiene que salir de un
     * depósito puntual (ver el campo en pedidos.xhtml), así que sin los
     * dos elegidos no hay nada para ofrecer todavía.
     */
    public List<ItemInventarioDTO> getListaItems() {
        Long idComercio = nuevoPedido.getIdComercio();
        if (idComercio == null || idDepositoSeleccionado == null) {
            return List.of();
        }
        return stock.listarItemsPorComercioYDeposito(idComercio, idDepositoSeleccionado);
    }

    /**
     * Puntos de picking ACTIVOS del comercio elegido — solo tiene sentido
     * cuando el origen del pedido es PUNTO_PICKING (ver OrigenPedido).
     */
    public List<PuntoPickingDTO> getListaPuntosPicking() {
        Long idComercio = nuevoPedido.getIdComercio();
        if (idComercio == null) {
            return List.of();
        }
        return comercios.listarPuntosPicking(idComercio);
    }

    /** Nombre del depósito de un ítem, para distinguirlos en el desplegable. */
    public String nombreDeposito(Long id) {
        if (id == null || listaDepositos == null) {
            return "—";
        }
        return listaDepositos.stream()
                .filter(d -> d.getId().equals(id))
                .map(DepositoDTO::getNombre)
                .findFirst()
                .orElse("Depósito " + id);
    }

    /**
     * Se llama por ajax al cambiar el comercio o el depósito elegido:
     * limpia el ítem ya seleccionado, que puede haber quedado fuera de la
     * lista nueva.
     */
    public void onContextoCambiado() {
        nuevoPedido.setIdPuntoPicking(null);
        for (DatosLineaPedidoDTO linea : nuevoPedido.getLineas()) {
            linea.setIdItem(null);
        }
    }

    // Valores del enum para el desplegable de origen en pedidos.xhtml.
    public OrigenPedido[] getOrigenes() {
        return OrigenPedido.values();
    }

    // Valores del enum para el desplegable de medio de pago en pedidos.xhtml.
    public MedioPago[] getMediosPago() {
        return MedioPago.values();
    }

    // Crea la fila mock "recién llegada del ERP" (PedidoExterno), NO un
    // pedido real: el pedido real lo genera SincronizadorDePedidos cuando
    // la levanta en su próxima pasada.
    public void registrarPedidoExterno() {
        try {
            // Sin clave de idempotencia: la pantalla es una simulación y no
            // reintenta sola (eso es cosa del ERP, ver la API REST).
            gestion.registrarPedidoExterno(nuevoPedido, null);
            Mensajes.info("Pedido recibido: entra por la cola y en unos segundos aparece en la tabla de pedidos (recargá la página).");
            nuevoPedido = nuevoPedidoVacio();
            idDepositoSeleccionado = null;
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            // Falla técnica al guardar (por ejemplo la base rechazó el cambio
            // y la transacción se deshizo): el pedido quedó como estaba.
            Mensajes.error("No se pudo guardar el cambio del pedido. Intentá de nuevo.");
        }
    }

    // Avanza el pedido de PENDIENTE a CONFIRMADO (ver EstadoPedido), o lo
    // deja en la lista de espera si nadie tiene lugar (ADR-037).
    public void confirmar(Long idPedido) {
        try {
            Long idRepartidor = gestion.confirmarPedido(idPedido);
            if (idRepartidor != null) {
                Mensajes.info("Pedido confirmado");
            } else if (seguimiento.consultarEstadoPedido(idPedido).isRetiroEnDeposito()) {
                // Retiro en depósito (ADR-053): no lleva repartidor, queda listo para retirar.
                Mensajes.info("Pedido " + idPedido + " confirmado: listo para retirar en el depósito");
            } else {
                Mensajes.info("Pedido " + idPedido + " en lista de espera: "
                        + seguimiento.consultarEstadoPedido(idPedido).getMotivoEspera()
                        + ". No se cobró nada todavía: se asigna solo en cuanto se pueda.");
            }
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            // Falla técnica al guardar (por ejemplo la base rechazó el cambio
            // y la transacción se deshizo): el pedido quedó como estaba.
            Mensajes.error("No se pudo guardar el cambio del pedido. Intentá de nuevo.");
        }
    }

    // Retiro en depósito (ADR-053): el código que muestra quien retira, por pedido.
    private final Map<Long, String> codigosDeRetiro = new java.util.HashMap<>();

    public Map<Long, String> getCodigosDeRetiro() {
        return codigosDeRetiro;
    }

    // CONFIRMADO → ENTREGADO sin repartidor: el código mostrado es la prueba de la entrega.
    public void entregarEnDeposito(Long idPedido) {
        try {
            gestion.entregarEnDeposito(idPedido, codigosDeRetiro.get(idPedido));
            codigosDeRetiro.remove(idPedido);
            Mensajes.info("Pedido " + idPedido + " entregado en el depósito");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo guardar el cambio del pedido. Intentá de nuevo.");
        }
    }

    // El repartidor retiró el pedido: CONFIRMADO → EN_CAMINO (ver EstadoPedido).
    public void despachar(Long idPedido) {
        try {
            gestion.despacharPedido(idPedido);
            Mensajes.info("Pedido en camino");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            // Falla técnica al guardar (por ejemplo la base rechazó el cambio
            // y la transacción se deshizo): el pedido quedó como estaba.
            Mensajes.error("No se pudo guardar el cambio del pedido. Intentá de nuevo.");
        }
    }

    // Contingencia del ADMINISTRADOR (el repartidor no pudo registrarla):
    // EN_CAMINO → ENTREGADO, también con la foto del remito (ADR-033).
    public void entregar() {
        byte[] foto;
        try {
            foto = ArchivosSubidos.leer(fotoRemito, 5 * 1024 * 1024);
        } catch (java.io.IOException e) {
            Mensajes.error("No se pudo leer la foto del remito. Probá de nuevo.");
            return;
        } finally {
            fotoRemito = null;
        }
        if (foto == null) {
            Mensajes.error("La foto del remito no puede superar los 5 MB");
            return;
        }
        try {
            gestion.registrarEntrega(idPedidoAEntregar, foto, null);
            Mensajes.info("Entrega del pedido " + idPedidoAEntregar + " registrada");
            idPedidoAEntregar = null;
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            // Falla técnica al guardar (por ejemplo la base rechazó el cambio
            // y la transacción se deshizo): el pedido quedó como estaba.
            Mensajes.error("No se pudo guardar el cambio del pedido. Intentá de nuevo.");
        }
    }

    // Cancela el pedido y devuelve el stock que tenía comprometido en cada
    // línea (ver LineaPedido.idReservaStock e IReservaStock.registrarDevolucion).
    public void cancelar(Long idPedido) {
        try {
            boolean conStock = pedidos.stream()
                    .anyMatch(p -> p.getId().equals(idPedido) && p.getOrigen() == OrigenPedido.STOCK_CONSIGNADO);
            gestion.cancelarPedido(idPedido);
            Mensajes.info(conStock
                    ? "Pedido cancelado: el stock volvió al disponible"
                    : "Pedido cancelado");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBAccessException e) {
            // @RolesAllowed("ADMINISTRADOR") en PagoService.anularCobro: un
            // pedido confirmado ya tiene un cobro, y anularlo es sensible.
            Mensajes.error("Solo un administrador puede cancelar un pedido confirmado: hay que anular su cobro.");
        } catch (EJBException e) {
            // Falla técnica al guardar (por ejemplo la base rechazó el cambio
            // y la transacción se deshizo): el pedido quedó como estaba.
            Mensajes.error("No se pudo guardar el cambio del pedido. Intentá de nuevo.");
        }
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public List<PedidoDTO> getPedidos() { return pedidos; }
    public List<PedidoExternoDTO> getPedidosExternos() { return pedidosExternos; }

    // "Yerba 1kg, Aceite 900ml" en vez de "Ítem 2, Ítem 3"; si el ítem ya
    // no existe, queda su ID.
    public String descripcionLineas(PedidoExternoDTO pe) {
        return pe.getLineas().stream()
                .map(l -> l.getProducto() != null ? l.getProducto()
                        : productosPorItem.getOrDefault(l.getIdItem(), "Ítem " + l.getIdItem()))
                .collect(Collectors.joining(", "));
    }
    public List<ComercioDTO> getListaComercios() { return listaComercios; }
    public List<DepositoDTO> getListaDepositos() { return listaDepositos; }
    public List<TransportistaDTO> getListaTransportistas() { return listaTransportistas; }
    public List<CotizacionDTO> getCotizaciones() { return cotizaciones; }
    public Long getIdPedidoCotizado() { return idPedidoCotizado; }
    public Long getIdPedidoADerivar() { return idPedidoADerivar; }
    public void setIdPedidoADerivar(Long idPedidoADerivar) { this.idPedidoADerivar = idPedidoADerivar; }
    public Long getIdTransportistaElegido() { return idTransportistaElegido; }
    public void setIdTransportistaElegido(Long idTransportistaElegido) { this.idTransportistaElegido = idTransportistaElegido; }
    public Long getIdDepositoSeleccionado() { return idDepositoSeleccionado; }
    public void setIdDepositoSeleccionado(Long idDepositoSeleccionado) { this.idDepositoSeleccionado = idDepositoSeleccionado; }
    public DatosPedidoExternoDTO getNuevoPedido() { return nuevoPedido; }
    public void setNuevoPedido(DatosPedidoExternoDTO nuevoPedido) { this.nuevoPedido = nuevoPedido; }

    // Pedidos EN_CAMINO con repartidor propio: los que se pueden entregar con remito.
    public List<PedidoDTO> getEnCaminoConRepartidor() {
        return pedidos.stream()
                .filter(p -> "EN_CAMINO".equals(p.getEstado()) && p.getIdRepartidor() != null)
                .collect(java.util.stream.Collectors.toList());
    }

    public Long getIdPedidoAEntregar() { return idPedidoAEntregar; }
    public void setIdPedidoAEntregar(Long idPedidoAEntregar) { this.idPedidoAEntregar = idPedidoAEntregar; }
    public jakarta.servlet.http.Part getFotoRemito() { return fotoRemito; }
    public void setFotoRemito(jakarta.servlet.http.Part fotoRemito) { this.fotoRemito = fotoRemito; }
}
