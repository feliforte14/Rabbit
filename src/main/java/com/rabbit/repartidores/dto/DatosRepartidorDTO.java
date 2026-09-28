package com.rabbit.repartidores.dto;

// DTO de entrada: los datos del formulario de alta de repartidor.
public class DatosRepartidorDTO {

    public String nombre;
    public String telefono;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
}
