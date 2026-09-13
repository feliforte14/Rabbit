package com.rabbit.inventario.dto;

/**
 * DTO de entrada para registrar un depósito nuevo. Contiene los datos
 * que el usuario completa en el formulario de alta (depositos.xhtml).
 */

public class DatosDepositoDTO {
    public String nombre;
    public String direccion;
    public String provincia;
    public String localidad;
    public String codigoPostal;

    // Getters/setters JavaBean: los requiere Expression Language (JSF)
    // para leer y escribir estos campos desde la vista .xhtml.
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }
    public String getLocalidad() { return localidad; }
    public void setLocalidad(String localidad) { this.localidad = localidad; }
    public String getCodigoPostal() { return codigoPostal; }
    public void setCodigoPostal(String codigoPostal) { this.codigoPostal = codigoPostal; }
}
