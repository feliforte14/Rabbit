package com.rabbit.repartidores.datos.model;

/**
 * Disponibilidad de un repartidor.
 *
 *   [DISPONIBLE] --- asignarRepartidor() ---> [OCUPADO]
 *        ^                                        |
 *        +------------ liberarRepartidor() -------+
 *
 * Un repartidor OCUPADO tiene exactamente un pedido a cargo
 * (Repartidor.idPedidoActual); se libera cuando ese pedido se entrega o
 * se cancela.
 */
public enum EstadoRepartidor {
    DISPONIBLE,
    OCUPADO
}
