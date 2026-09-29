package com.rabbit.ruteo.negocio;

/**
 * CAPA DE NEGOCIO — componente Ruteo (EJB @Stateless).
 *
 * Arma la hoja de ruta de un pedido cruzando cuatro componentes por sus
 * interfaces: Pedidos (qué y adónde), Comercios (punto de picking),
 * Inventario (depósito de cada ítem consignado) y Repartidores (quién
 * lleva). No guarda nada propio: por eso no tiene capa de datos.
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
 * Fuera de alcance (Entrega Final): optimizar recorridos, agrupar por zona
 * y asignar varios pedidos a un mismo viaje.
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
        hoja.estado = p.getEstado();
        hoja.productos = p.getProductos();
        hoja.cantidadTotal = p.getCantidadTotal();
        hoja.direccionEntrega = p.getDireccionEntrega();
        hoja.actualizado = p.getFechaActualizacion();
        hoja.cobrarAlEntregar = p.getMedioPago() == MedioPago.CONTRA_ENTREGA ? p.getImporte() : null;

        ComercioDTO comercio = comerciosPorId.get(p.getIdComercio());
        hoja.comercio = comercio != null ? comercio.nombre : "Comercio " + p.getIdComercio();
        hoja.retiros = retiros(p, comercio, depositosPorId, itemsPorId);

        RepartidorDTO repartidor = repartidoresPorId.get(p.getIdRepartidor());
        if (repartidor != null) {
            hoja.repartidor = repartidor.getNombre();
            hoja.telefonoRepartidor = repartidor.getTelefono();
        }
        return hoja;
    }

    // Lugares de retiro: el punto de picking del comercio, o los depósitos
    // de los que sale cada línea de stock consignado (sin repetir).
    private List<String> retiros(PedidoDTO p, ComercioDTO comercio,
                                 Map<Long, DepositoDTO> depositosPorId,
                                 Map<Long, ItemInventarioDTO> itemsPorId) {
        if (p.getOrigen() == OrigenPedido.PUNTO_PICKING) {
            if (comercio == null || comercio.getPuntosPicking() == null) {
                return List.of();
            }
            return comercio.getPuntosPicking().stream()
                    .filter(pp -> pp.id.equals(p.getIdPuntoPicking()))
                    .map(pp -> "Punto de picking " + pp.nombre + " — " + pp.direccion)
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
                .map(this::describir)
                .collect(Collectors.toList());
    }

    private String describir(DepositoDTO d) {
        return "Depósito " + d.nombre + " — " + d.direccion + ", " + d.localidad;
    }
}
