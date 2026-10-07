package com.rabbit.ruteo.negocio;

/**
 * CAPA DE NEGOCIO — componente Ruteo (EJB @Stateless).
 *
 * Dos responsabilidades:
 *   - Hoja de ruta: la arma cruzando cuatro componentes por sus interfaces:
 *     Pedidos (qué y adónde), Comercios (punto de picking), Inventario
 *     (depósito de cada ítem consignado) y Repartidores (quién lleva).
 *   - Ruteo por zona: agrupa los pedidos pendientes según el código postal
 *     de entrega y los despacha según la cobertura de la zona (ver
 *     despacharPedido). Las zonas las guarda ZonaService.
 *
 * Stateless: cada hoja de ruta se arma de cero con lo que está en la base;
 * no hay conversación con el cliente.
 *
 * SEGURIDAD: el tablero es del personal de Rabbit; un REPARTIDOR solo ve
 * sus entregas (el repartidor sale de la identidad autenticada, ver
 * IContextoUsuario, a través de ISeguimientoPedido).
 *
 * Un pedido derivado a un transportista externo no tiene repartidor: la
 * hoja de ruta muestra el transportista y su código de seguimiento.
 *
 * Fuera de alcance: ordenar las paradas por distancia (no hay coordenadas
 * ni geocodificación) y juntar varios pedidos en un mismo viaje: un
 * repartidor lleva un pedido a la vez (ver ADR-017).
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.dto.LineaPedidoDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.negocio.IEnvios;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.ruteo.datos.model.CoberturaZona;
import com.rabbit.ruteo.dto.GrupoZonaDTO;
import com.rabbit.ruteo.dto.ResultadoDespachoDTO;
import com.rabbit.ruteo.dto.ResultadoDespachoDTO.Resultado;
import com.rabbit.ruteo.dto.ZonaDTO;
import com.rabbit.transportistas.dto.CotizacionDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import com.rabbit.transportistas.negocio.IGestionTransportistas;
import jakarta.ejb.EJBException;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "REPARTIDOR"})
@PermitAll
public class RuteoService implements IRuteo {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(RuteoService.class.getName());

    private static final Set<String> TERMINADOS = Set.of("ENTREGADO", "CANCELADO");

    @Inject
    private ISeguimientoPedido pedidos;

    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IConsultaStock stock;

    @Inject
    private IGestionRepartidores repartidores;

    // Pedidos derivados a un transportista externo (no tienen repartidor).
    @Inject
    private IEnvios envios;

    // Despacho por zona.
    @Inject
    private IZonas zonas;

    @Inject
    private IGestionPedidos gestion;

    @Inject
    private IGestionTransportistas transportistas;

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<HojaDeRutaDTO> listarEntregasEnCurso() {
        Map<Long, EnvioDTO> enviosPorPedido = envios.listarEnvios().stream()
                .collect(Collectors.toMap(EnvioDTO::getIdPedido, Function.identity(), (a, b) -> a));
        List<HojaDeRutaDTO> hojas = armar(pedidos.listarEntregasEnCurso());
        for (HojaDeRutaDTO hoja : hojas) {
            EnvioDTO envio = enviosPorPedido.get(hoja.idPedido);
            if (envio != null) {
                hoja.transportista = envio.getTransportista();
                hoja.codigoSeguimiento = envio.getCodigoSeguimiento();
            }
        }
        return hojas;
    }

    @Override
    @RolesAllowed("REPARTIDOR")
    public HojaDeRutaDTO entregaActualDelRepartidor() {
        List<PedidoDTO> enCurso = pedidos.listarPedidosDelRepartidorActual().stream()
                .filter(p -> !TERMINADOS.contains(p.getEstado()))
                .limit(1)
                .collect(Collectors.toList());
        return enCurso.isEmpty() ? null : armar(enCurso).get(0);
    }

    @Override
    @RolesAllowed("REPARTIDOR")
    public List<HojaDeRutaDTO> historialDelRepartidor() {
        return armar(pedidos.listarPedidosDelRepartidorActual().stream()
                .filter(p -> TERMINADOS.contains(p.getEstado()))
                .collect(Collectors.toList()));
    }

    // Arma las hojas de varios pedidos con una cantidad fija de consultas
    // (comercios con sus puntos de picking, repartidores, depósitos e
    // ítems), en vez de consultar a cada componente pedido por pedido.
    private List<HojaDeRutaDTO> armar(List<PedidoDTO> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, ComercioDTO> comerciosPorId = comercios.listarTodos().stream()
                .collect(Collectors.toMap(ComercioDTO::getId, Function.identity()));
        Map<Long, RepartidorDTO> repartidoresPorId = repartidores.listarTodos().stream()
                .collect(Collectors.toMap(RepartidorDTO::getId, Function.identity()));
        Map<Long, DepositoDTO> depositosPorId = stock.listarDepositos().stream()
                .collect(Collectors.toMap(DepositoDTO::getId, Function.identity()));
        Set<Long> idsItems = lista.stream()
                .flatMap(p -> p.getLineas().stream())
                .map(LineaPedidoDTO::getIdItem)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ItemInventarioDTO> itemsPorId = stock.listarItemsPorIds(idsItems).stream()
                .collect(Collectors.toMap(i -> i.id, Function.identity()));

        return lista.stream()
                .map(p -> armar(p, comerciosPorId, repartidoresPorId, depositosPorId, itemsPorId))
                .collect(Collectors.toList());
    }

    private HojaDeRutaDTO armar(PedidoDTO p, Map<Long, ComercioDTO> comerciosPorId,
                                Map<Long, RepartidorDTO> repartidoresPorId,
                                Map<Long, DepositoDTO> depositosPorId,
                                Map<Long, ItemInventarioDTO> itemsPorId) {
        HojaDeRutaDTO hoja = new HojaDeRutaDTO();
        hoja.idPedido = p.getId();
        hoja.codigoCliente = p.getCodigoSeguimiento();
        hoja.estado = p.getEstado();
        hoja.productos = p.getProductos();
        hoja.cantidadTotal = p.getCantidadTotal();
        hoja.direccionEntrega = p.getDireccionEntrega();
        hoja.actualizado = p.getFechaActualizacion();
        hoja.cobrarAlEntregar = p.getMedioPago() == MedioPago.CONTRA_ENTREGA ? p.getImporte() : null;

        ComercioDTO comercio = comerciosPorId.get(p.getIdComercio());
        hoja.comercio = comercio != null ? comercio.nombre : "Comercio " + p.getIdComercio();
        List<Retiro> retiros = retiros(p, comercio, depositosPorId, itemsPorId);
        hoja.retiros = retiros.stream().map(Retiro::descripcion).collect(Collectors.toList());
        hoja.urlMapa = EnlaceMapa.recorrido(
                retiros.stream().map(Retiro::direccion).collect(Collectors.toList()), p.getDireccionEntrega());

        RepartidorDTO repartidor = repartidoresPorId.get(p.getIdRepartidor());
        if (repartidor != null) {
            hoja.repartidor = repartidor.getNombre();
            hoja.telefonoRepartidor = repartidor.getTelefono();
        }
        return hoja;
    }

    // Un lugar de retiro: el texto para la hoja de ruta y la dirección sola
    // para el mapa.
    private record Retiro(String descripcion, String direccion) {
    }

    // Lugares de retiro: el punto de picking del comercio, o los depósitos
    // de los que sale cada línea de stock consignado (sin repetir).
    private List<Retiro> retiros(PedidoDTO p, ComercioDTO comercio,
                                 Map<Long, DepositoDTO> depositosPorId,
                                 Map<Long, ItemInventarioDTO> itemsPorId) {
        if (p.getOrigen() == OrigenPedido.PUNTO_PICKING) {
            if (comercio == null || comercio.getPuntosPicking() == null) {
                return List.of();
            }
            return comercio.getPuntosPicking().stream()
                    .filter(pp -> pp.id.equals(p.getIdPuntoPicking()))
                    .map(pp -> new Retiro("Punto de picking " + pp.nombre + " — " + pp.direccion, pp.direccion))
                    .collect(Collectors.toList());
        }
        return p.getLineas().stream()
                .map(LineaPedidoDTO::getIdItem)
                .map(itemsPorId::get)
                .filter(Objects::nonNull)
                .map(i -> i.idDeposito)
                .distinct()
                .map(depositosPorId::get)
                .filter(Objects::nonNull)
                .map(d -> new Retiro(nombreDeDeposito(d.nombre) + " — " + d.direccion + ", " + d.localidad,
                        d.direccion + ", " + d.localidad))
                .collect(Collectors.toList());
    }

    // ===============================================================
    // Ruteo por zona
    // ===============================================================

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<GrupoZonaDTO> listarPendientesPorZona() {
        List<ZonaDTO> activas = zonas.listarTodas().stream().filter(ZonaDTO::isActiva).collect(Collectors.toList());
        Map<Long, String> nombresTransportistas = transportistas.listarTodos().stream()
                .collect(Collectors.toMap(TransportistaDTO::getId, TransportistaDTO::getNombre));
        List<RepartidorDTO> libres = repartidores.listarTodos().stream()
                .filter(r -> "DISPONIBLE".equals(r.getEstado())).collect(Collectors.toList());

        Map<Long, GrupoZonaDTO> grupos = new LinkedHashMap<>();
        for (ZonaDTO z : activas) {
            GrupoZonaDTO g = new GrupoZonaDTO();
            g.zona = z;
            g.transportista = z.getIdTransportista() != null ? nombresTransportistas.get(z.getIdTransportista()) : null;
            g.repartidoresLibres = libres.stream().filter(r -> z.getId().equals(r.getIdZona())).count();
            grupos.put(z.getId(), g);
        }
        GrupoZonaDTO sinZona = new GrupoZonaDTO();
        sinZona.repartidoresLibres = libres.size();
        for (PedidoDTO p : pedidos.listarPendientes()) {
            ZonaDTO z = activas.stream().filter(zz -> zz.contiene(p.getCodigoPostalEntrega())).findFirst().orElse(null);
            (z != null ? grupos.get(z.getId()) : sinZona).pedidos.add(p);
        }
        List<GrupoZonaDTO> resultado = new ArrayList<>(grupos.values());
        resultado.add(sinZona);
        return resultado;
    }

    // NOT_SUPPORTED: cada operación de Pedidos que se llama abre su propia
    // transacción, así en un despacho masivo un pedido que falla no deshace
    // a los demás.
    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ResultadoDespachoDTO despacharPedido(Long idPedido) {
        PedidoDTO pedido;
        try {
            pedido = pedidos.consultarEstadoPedido(idPedido);
        } catch (com.rabbit.pedidos.negocio.ValidacionException e) {
            return ResultadoDespachoDTO.de(idPedido, Resultado.ERROR, e.getMessage());
        }
        if (!"PENDIENTE".equals(pedido.getEstado())) {
            return ResultadoDespachoDTO.de(idPedido, Resultado.ERROR, "El pedido no está pendiente");
        }
        return despachar(pedido, new Despacho());
    }

    // Despachar una zona entera: lo que no cambia entre un pedido y otro
    // (zonas, transportistas, repartidores) se consulta una sola vez para
    // todo el lote, en vez de una vez por pedido.
    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ResultadoDespachoDTO> despacharZona(Long idZona) {
        Despacho despacho = new Despacho();
        ZonaDTO zona = despacho.zonas.stream().filter(z -> z.getId().equals(idZona)).findFirst().orElse(null);
        if (zona == null) {
            return List.of();
        }
        return pedidos.listarPendientes().stream()
                .filter(p -> zona.contiene(p.getCodigoPostalEntrega()))
                .map(p -> despachar(p, despacho))
                .collect(Collectors.toList());
    }

    /**
     * Lo que un despacho necesita saber de la red, cargado una vez. Los
     * repartidores se van marcando ocupados a medida que se les asignan
     * pedidos del lote, así el siguiente pedido ve la disponibilidad real.
     */
    private final class Despacho {
        final List<ZonaDTO> zonas = RuteoService.this.zonas.listarTodas().stream()
                .filter(ZonaDTO::isActiva).collect(Collectors.toList());
        final Map<Long, String> nombresTransportistas = transportistas.listarTodos().stream()
                .collect(Collectors.toMap(TransportistaDTO::getId, TransportistaDTO::getNombre));
        final Map<Long, RepartidorDTO> repartidores = RuteoService.this.repartidores.listarTodos().stream()
                .collect(Collectors.toMap(RepartidorDTO::getId, r -> r));

        ZonaDTO zonaDe(String codigoPostal) {
            return zonas.stream().filter(z -> z.contiene(codigoPostal)).findFirst().orElse(null);
        }

        boolean hayLibreEn(ZonaDTO zona) {
            return repartidores.values().stream()
                    .anyMatch(r -> "DISPONIBLE".equals(r.getEstado()) && zona.getId().equals(r.getIdZona()));
        }

        String nombre(Long idTransportista) {
            return nombresTransportistas.getOrDefault(idTransportista, "transportista");
        }
    }

    private ResultadoDespachoDTO despachar(PedidoDTO pedido, Despacho despacho) {
        Long idPedido = pedido.getId();
        String cp = pedido.getCodigoPostalEntrega();
        ZonaDTO zona = despacho.zonaDe(cp);
        if (zona == null) {
            return ResultadoDespachoDTO.de(idPedido, Resultado.SIN_ZONA, cp == null
                    ? "Sin código postal de entrega: hay que despacharlo a mano"
                    : "El código postal " + cp + " no está en ninguna zona: hay que despacharlo a mano");
        }
        try {
            if (CoberturaZona.TRANSPORTISTA.name().equals(zona.getCobertura())) {
                return derivar(idPedido, zona.getIdTransportista(), "cubre la zona " + zona.getNombre(), despacho);
            }
            if (!despacho.hayLibreEn(zona) && zona.getIdTransportista() != null) {
                return derivar(idPedido, zona.getIdTransportista(),
                        "respaldo: no hay repartidores libres en la zona " + zona.getNombre(), despacho);
            }
            Long idRepartidor = gestion.confirmarPedidoEnZona(idPedido, zona.getId());
            RepartidorDTO asignado = idRepartidor != null ? despacho.repartidores.get(idRepartidor) : null;
            if (asignado != null) {
                asignado.estado = "OCUPADO";
            }
            boolean deLaZona = asignado != null && zona.getId().equals(asignado.getIdZona());
            return ResultadoDespachoDTO.de(idPedido, Resultado.REPARTIDOR,
                    (asignado != null ? asignado.getNombre() : "Repartidor")
                            + (deLaZona ? " (zona " + zona.getNombre() + ")"
                                        : " (de otra zona: no había libres en " + zona.getNombre() + ")"));
        } catch (com.rabbit.pedidos.negocio.ValidacionException e) {
            return ResultadoDespachoDTO.de(idPedido, Resultado.ERROR, e.getMessage());
        } catch (EJBException e) {
            return ResultadoDespachoDTO.de(idPedido, Resultado.ERROR, "No se pudo despachar el pedido. Intentá de nuevo.");
        }
    }

    // Deriva al transportista de la zona. Si lo rechaza o no responde, en
    // vez de dejar el pedido sin despachar se cotiza con los demás y se
    // deriva al más barato que lo tome (ADR-020).
    private ResultadoDespachoDTO derivar(Long idPedido, Long idTransportista, String motivo, Despacho despacho) {
        try {
            String codigo = gestion.derivarATransportista(idPedido, idTransportista);
            return ResultadoDespachoDTO.de(idPedido, Resultado.DERIVADO,
                    despacho.nombre(idTransportista) + " (" + motivo + "), seguimiento " + codigo);
        } catch (com.rabbit.pedidos.negocio.ValidacionException e) {
            for (CotizacionDTO alternativa : gestion.cotizarDerivacion(idPedido)) {
                if (!alternativa.isCotizado() || alternativa.idTransportista.equals(idTransportista)) {
                    continue;
                }
                try {
                    String codigo = gestion.derivarATransportista(idPedido, alternativa.idTransportista);
                    return ResultadoDespachoDTO.de(idPedido, Resultado.DERIVADO, alternativa.transportista
                            + " (la más barata de las alternativas: " + despacho.nombre(idTransportista)
                            + " no lo tomó), seguimiento " + codigo);
                } catch (com.rabbit.pedidos.negocio.ValidacionException otra) {
                    LOG.info("[Ruteo] " + alternativa.transportista + " tampoco tomó el pedido " + idPedido + ": " + otra.getMessage());
                }
            }
            throw e;
        }
    }

    // "Depósito Sur" ya dice qué es: no se le antepone otro "Depósito".
    private static String nombreDeDeposito(String nombre) {
        return nombre != null && nombre.toLowerCase().startsWith("depósito") ? nombre : "Depósito " + nombre;
    }
}
