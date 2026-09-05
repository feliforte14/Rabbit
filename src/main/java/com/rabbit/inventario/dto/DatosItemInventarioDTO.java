package com.rabbit.inventario.dto;

/**
 * DTO de entrada para cargar stock nuevo en un depósito. Contiene los
 * datos que el usuario completa en el formulario de alta (items.xhtml).
 */

public class DatosItemInventarioDTO {
    public String producto;
    public int cantidadDisponible;

    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(int cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }
}
