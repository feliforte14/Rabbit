package com.rabbit.repartidores.dto;

// DTO de entrada: los datos del formulario de alta de repartidor.
public class DatosRepartidorDTO {

    public String nombre;
    public String telefono;
    // Opcional: la zona donde reparte.
    public Long idZona;
    // Opcionales: lo que soporta su vehículo (ADR-032).
    public java.math.BigDecimal capacidadKg;
    public Integer capacidadBultos;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public java.math.BigDecimal getCapacidadKg() { return capacidadKg; }
    public void setCapacidadKg(java.math.BigDecimal capacidadKg) { this.capacidadKg = capacidadKg; }
    public Integer getCapacidadBultos() { return capacidadBultos; }
    public void setCapacidadBultos(Integer capacidadBultos) { this.capacidadBultos = capacidadBultos; }
    public Long getIdZona() { return idZona; }
    public void setIdZona(Long idZona) { this.idZona = idZona; }
}
