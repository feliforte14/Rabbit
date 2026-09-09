package com.rabbit.pedidos.dto;

/**
 * DTO de entrada para simular un pedido nuevo "llegando del ERP" del
 * comercio (formulario en pedidos.xhtml). No crea un Pedido directo: crea
 * la fila mock que SincronizadorDePedidos va a levantar en su próxima
 * pasada — ver PedidoExterno.
 */

public class DatosPedidoExternoDTO {
    public Long idComercio;
    public Long idItem;
    public int cantidad;

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Long getIdItem() { return idItem; }
    public void setIdItem(Long idItem) { this.idItem = idItem; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
