package com.rabbit.inventario.dto;

/**
 * DTO de entrada para registrar un depósito nuevo. Contiene los datos
 * que el usuario completa en el formulario de alta (depositos.xhtml).
 */

public class DatosDepositoDTO {
    public String nombre;
    public String direccion;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}
