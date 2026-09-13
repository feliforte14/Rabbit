package com.rabbit.pedidos.dto;

/**
 * DTO de entrada para UNA línea del formulario de alta de pedido
 * (pedidos.xhtml): un producto y su cantidad. Un pedido tiene una lista de
 * estas — ver DatosPedidoExternoDTO.lineas.
 *
 * Según el origen del pedido dueño (ver OrigenPedido) se completa un campo
 * u otro: STOCK_CONSIGNADO usa idItem, PUNTO_PICKING usa producto
 * (descripción libre).
 */

public class DatosLineaPedidoDTO {
    public Long idItem;
    public String producto;
    public int cantidad;

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public Long getIdItem() { return idItem; }
    public void setIdItem(Long idItem) { this.idItem = idItem; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
