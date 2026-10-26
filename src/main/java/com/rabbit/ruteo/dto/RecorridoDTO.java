package com.rabbit.ruteo.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * La hoja de ruta recomendada del repartidor: todas sus paradas del día
 * en el orden sugerido (ver OrdenDeRecorrido) y el recorrido en Google
 * Maps, partido en tramos de hasta 10 puntos (partida + 9 paradas).
 */
public class RecorridoDTO {

    public List<ParadaDTO> paradas = new ArrayList<>();
    public List<String> tramos = new ArrayList<>();
    // Pedidos en curso sin dirección de entrega: no entran en el recorrido.
    public List<Long> sinDireccion = new ArrayList<>();

    public List<ParadaDTO> getParadas() { return paradas; }
    public List<String> getTramos() { return tramos; }
    public List<Long> getSinDireccion() { return sinDireccion; }
    /** Primera sección del día: la salida del depósito Rabbit y los retiros. */
    public List<ParadaDTO> getSalidaYRetiros() {
        return paradas.stream().filter(p -> p.tipo != ParadaDTO.Tipo.ENTREGA).collect(java.util.stream.Collectors.toList());
    }
    /** Segunda sección: las entregas, desde el último retiro. */
    public List<ParadaDTO> getEntregas() {
        return paradas.stream().filter(p -> p.tipo == ParadaDTO.Tipo.ENTREGA).collect(java.util.stream.Collectors.toList());
    }
    public boolean isVacio() { return paradas.isEmpty(); }
}
