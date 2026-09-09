package com.rabbit.comercios.dto;

/**
 * DTO de entrada para registrar un punto de picking nuevo en un comercio
 * existente (renombrado desde DatosSucursalDTO). Se usa en el formulario
 * de alta (puntos-picking.xhtml).
 */
public class DatosPuntoPickingDTO {
    public String nombre;
    public String direccion;

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}
