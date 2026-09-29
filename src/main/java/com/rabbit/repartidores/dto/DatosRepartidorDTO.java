package com.rabbit.repartidores.dto;

// DTO de entrada: los datos del formulario de alta de repartidor.
public class DatosRepartidorDTO {

    public String nombre;
    public String telefono;
    // Opcional: la zona donde reparte.
    public Long idZona;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Long getIdZona() { return idZona; }
    public void setIdZona(Long idZona) { this.idZona = idZona; }
}
