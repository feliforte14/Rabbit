package com.logiRed.comercios.dto;

/**
 * DTO de entrada para registrar un comercio nuevo.
 * Contiene los datos que el cliente envía en el body del POST.
 * No incluye el ID porque todavía no existe — lo asigna la BD.
 */

public class DatosComercioDTO {
    public String nombre;
    public String razonSocial;
    public String cuit;
    public String email;
    public String telefono;

    // Getters/setters JavaBean: los requiere Expression Language (JSF)
    // para leer y escribir estos campos desde las vistas .xhtml
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
}
