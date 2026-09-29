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
 * Fuera de alcance (Entrega Final): optimizar recorridos, agrupar por zona
 * y asignar varios pedidos a un mismo viaje.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.PuntoPickingDTO;
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
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
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

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<HojaDeRutaDTO> listarEntregasEnCurso() {
        return pedidos.listarEntregasEnCurso().stream().map(this::armar).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed("REPARTIDOR")
    public HojaDeRutaDTO entregaActualDelRepartidor() {
        return pedidos.listarPedidosDelRepartidorActual().stream()
                .filter(p -> !TERMINADOS.contains(p.getEstado()))
                .findFirst()
                .map(this::armar)
                .orElse(null);
    }

    @Override
    @RolesAllowed("REPARTIDOR")
    public List<HojaDeRutaDTO> historialDelRepartidor() {
        return pedidos.listarPedidosDelRepartidorActual().stream()
                .filter(p -> TERMINADOS.contains(p.getEstado()))
                .map(this::armar)
                .collect(Collectors.toList());
    }

    private HojaDeRutaDTO armar(PedidoDTO p) {
        HojaDeRutaDTO hoja = new HojaDeRutaDTO();
        hoja.idPedido = p.getId();
        hoja.estado = p.getEstado();
        hoja.productos = p.getProductos();
        hoja.cantidadTotal = p.getCantidadTotal();
        hoja.direccionEntrega = p.getDireccionEntrega();
        hoja.actualizado = p.getFechaActualizacion();
        hoja.cobrarAlEntregar = p.getMedioPago() == MedioPago.CONTRA_ENTREGA ? p.getImporte() : null;

        ComercioDTO comercio = comercios.obtenerComercio(p.getIdComercio());
        hoja.comercio = comercio.nombre;
        hoja.retiros = retiros(p);

        RepartidorDTO repartidor = repartidores.obtenerRepartidor(p.getIdRepartidor());
        if (repartidor != null) {
            hoja.repartidor = repartidor.getNombre();
            hoja.telefonoRepartidor = repartidor.getTelefono();
        }
        return hoja;
    }

    // Lugares de retiro: el punto de picking del comercio, o los depósitos
    // de los que sale cada línea de stock consignado (sin repetir).
    private List<String> retiros(PedidoDTO p) {
        if (p.getOrigen() == OrigenPedido.PUNTO_PICKING) {
            return comercios.listarPuntosPickingDeComercio(p.getIdComercio()).stream()
                    .filter(pp -> pp.id.equals(p.getIdPuntoPicking()))
                    .map(pp -> "Punto de picking " + pp.nombre + " — " + pp.direccion)
                    .collect(Collectors.toList());
        }
        Map<Long, ItemInventarioDTO> items = stock.listarItemsPorComercio(p.getIdComercio()).stream()
                .collect(Collectors.toMap(i -> i.id, Function.identity()));
        Set<Long> depositos = p.getLineas().stream()
                .map(LineaPedidoDTO::getIdItem)
                .map(items::get)
                .filter(Objects::nonNull)
                .map(i -> i.idDeposito)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return depositos.stream()
                .map(stock::obtenerDeposito)
                .map(this::describir)
                .collect(Collectors.toList());
    }

    private String describir(DepositoDTO d) {
        return "Depósito " + d.nombre + " — " + d.direccion + ", " + d.localidad;
    }
}
