package com.rabbit.inventario.dto;

/**
 * DTO de entrada para cargar stock nuevo en un depósito. Contiene los
 * datos que el usuario completa en el formulario de alta (items.xhtml).
 *
 * idComercio identifica de quién es la mercadería que se consigna: el
 * depósito es de Rabbit, pero el stock que entra tiene dueño.
 */

public class DatosItemInventarioDTO {
    public String producto;
    public int cantidadDisponible;
    public Long idComercio;

    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(int cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
}
