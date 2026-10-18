package com.rabbit.ruteo.dto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO de salida: cuándo llega un pedido que va en camino (ADR-043). Lo ve
 * el cliente en el seguimiento: "sos la parada 4 de 9, llega entre las
 * 15:00 y las 15:30". No lleva direcciones ni nada de las otras paradas.
 */
public class LlegadaEstimadaDTO {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    // Posición de la parada del pedido en la hoja de ruta recomendada del
    // repartidor (1 = la próxima) y cuántas paradas tiene en total.
    public int numeroParada;
    public int totalParadas;
    // Franja aproximada, redondeada a cuartos de hora.
    public LocalTime desde;
    public LocalTime hasta;

    public int getNumeroParada() { return numeroParada; }
    public int getTotalParadas() { return totalParadas; }
    /** Paradas que el repartidor hace antes de la de este pedido. */
    public int getParadasAntes() { return numeroParada - 1; }
    public boolean isEsLaProxima() { return numeroParada == 1; }
    public LocalTime getDesde() { return desde; }
    public LocalTime getHasta() { return hasta; }
    public String getDesdeTexto() { return desde.format(HORA); }
    public String getHastaTexto() { return hasta.format(HORA); }
}
