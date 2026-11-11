package com.rabbit.ruteo.datos.model;

/**
 * Entidad JPA: dónde queda una dirección (ADR-046), según OpenStreetMap
 * (Nominatim). Es una caché: cada dirección se consulta una sola vez, y la
 * hoja de ruta lee de acá sin salir a internet.
 *
 * clave: la dirección normalizada con su código postal (ver
 * Nominatim.clave). encontrada=false guarda también los "no la encontré",
 * para no volver a preguntar en cada pasada (se reintenta a los 7 días).
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coordenadas_direcciones")
public class CoordenadaDireccion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true, length = 300)
    private String clave;

    private boolean encontrada;

    private Double latitud;
    private Double longitud;

    @Column(nullable = false)
    private LocalDateTime fechaConsulta;

    public CoordenadaDireccion() {}

    public Long getId() { return id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public boolean isEncontrada() { return encontrada; }
    public void setEncontrada(boolean encontrada) { this.encontrada = encontrada; }
    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }
    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
    public LocalDateTime getFechaConsulta() { return fechaConsulta; }
    public void setFechaConsulta(LocalDateTime fechaConsulta) { this.fechaConsulta = fechaConsulta; }
}
