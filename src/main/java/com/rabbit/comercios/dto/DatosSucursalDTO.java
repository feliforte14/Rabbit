package com.rabbit.comercios.dto;

/**
 * DTO de entrada para registrar una sucursal nueva en un comercio existente.
 * Se usa en el formulario de alta de sucursales (sucursales.xhtml).
 */
public class DatosSucursalDTO {
    public String nombre;
    public String direccion;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}
