package com.rabbit.repartidores.datos.model;

/**
 * Disponibilidad de un repartidor.
 *
 *   [DISPONIBLE] --- asignarRepartidor() ---> [OCUPADO]
 *        ^                                        |
 *        +------------ liberarRepartidor() -------+
 *
 * Un repartidor OCUPADO tiene al menos un pedido a cargo (CargaAsignada);
 * vuelve a DISPONIBLE cuando se entrega o se cancela el último. Estar
 * OCUPADO no impide que le asignen otro si tiene capacidad cargada y le
 * entra (ADR-032).
 */
public enum EstadoRepartidor {
    DISPONIBLE,
    OCUPADO
}
