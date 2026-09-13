package com.rabbit.pedidos.dto;

/**
 * DTO de salida: representa UNA línea de un pedido (real o mock del ERP)
 * tal como se muestra en la vista. Nunca se persiste — ver LineaPedido y
 * LineaPedidoExterno para las entidades.
 */

import com.rabbit.pedidos.datos.model.LineaPedido;
import com.rabbit.pedidos.datos.model.LineaPedidoExterno;

public class LineaPedidoDTO {

    public Long id;
    public Long idItem;
    public String producto;
    public int cantidad;

    public static LineaPedidoDTO desde(LineaPedido l) {
        LineaPedidoDTO dto = new LineaPedidoDTO();
        dto.id = l.getId();
        dto.idItem = l.getIdItem();
        dto.producto = l.getProducto();
        dto.cantidad = l.getCantidad();
        return dto;
    }

    public static LineaPedidoDTO desde(LineaPedidoExterno l) {
        LineaPedidoDTO dto = new LineaPedidoDTO();
        dto.id = l.getId();
        dto.idItem = l.getIdItem();
        dto.producto = l.getProducto();
        dto.cantidad = l.getCantidad();
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public Long getIdItem() { return idItem; }
    public String getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
}
