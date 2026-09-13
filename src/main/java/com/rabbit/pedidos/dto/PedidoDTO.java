package com.rabbit.pedidos.dto;

/**
 * DTO de salida: representa un pedido tal como se muestra en la vista.
 * Nunca se persiste (ver Pedido para la entidad).
 */

import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class PedidoDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public Long id;
    public Long idComercio;
    public OrigenPedido origen;
    public Long idPuntoPicking;
    public List<LineaPedidoDTO> lineas;
    public String estado;
    public String fechaCreacion;

    // Convierte una entidad Pedido en un DTO listo para mostrar en la vista.
    public static PedidoDTO desde(Pedido p) {
        PedidoDTO dto = new PedidoDTO();
        dto.id = p.getId();
        dto.idComercio = p.getIdComercio();
        dto.origen = p.getOrigen();
        dto.idPuntoPicking = p.getIdPuntoPicking();
        dto.lineas = p.getLineas().stream().map(LineaPedidoDTO::desde).collect(Collectors.toList());
        dto.estado = p.getEstado() != null ? p.getEstado().name() : null;
        dto.fechaCreacion = p.getFechaCreacion() != null ? p.getFechaCreacion().format(FORMATO) : null;
        return dto;
    }

    // Cantidad total de unidades del pedido, sumando todas las líneas.
    public int getCantidadTotal() {
        return lineas.stream().mapToInt(LineaPedidoDTO::getCantidad).sum();
    }

    // Descripción de productos separados por coma, para las tablas de
    // listado que hoy muestran una sola columna "Producto".
    public String getProductos() {
        return lineas.stream().map(LineaPedidoDTO::getProducto).collect(Collectors.joining(", "));
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public OrigenPedido getOrigen() { return origen; }
    public Long getIdPuntoPicking() { return idPuntoPicking; }
    public List<LineaPedidoDTO> getLineas() { return lineas; }
    public String getEstado() { return estado; }
    public String getFechaCreacion() { return fechaCreacion; }
}
