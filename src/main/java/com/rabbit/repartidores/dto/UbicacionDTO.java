package com.rabbit.repartidores.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// DTO de salida: la última ubicación que informó un repartidor (ADR-035).
public class UbicacionDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm");

    public Long idRepartidor;
    public String nombre;
    public double latitud;
    public double longitud;
    public LocalDateTime fecha;
    public int pedidosEnCurso;

    public Long getIdRepartidor() { return idRepartidor; }
    public String getNombre() { return nombre; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public String getHora() { return fecha != null ? fecha.format(FORMATO) : null; }
    public int getPedidosEnCurso() { return pedidosEnCurso; }
}
