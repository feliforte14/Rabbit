package com.rabbit.inventario.datos.model;

/**
 * Estados por los que pasa una reserva de stock.
 *
 *                    reservarStock()
 *                          |
 *                          v
 *                      [VIGENTE] <---- extenderReserva()
 *              ____________|____________
 *             |            |            |
 *      confirmarReserva  liberar    vence el plazo
 *             |            |            |
 *             v            v            v
 *       [CONFIRMADA]  [LIBERADA]   [EXPIRADA]
 *
 * Los tres estados finales son terminales: una vez que la reserva sale de
 * VIGENTE, no vuelve. Para reservar de nuevo hay que crear otra reserva.
 */
public enum EstadoReserva {

    /** Stock comprometido pero todavia no descontado. Tiene fecha de vencimiento. */
    VIGENTE,

    /** El cliente confirmo: el stock se descuenta definitivamente del deposito. */
    CONFIRMADA,

    /** El cliente desistio antes de vencer: el stock vuelve a estar libre. */
    LIBERADA,

    /** Se vencio el plazo sin confirmar. La libera el barredor automatico. */
    EXPIRADA
}
