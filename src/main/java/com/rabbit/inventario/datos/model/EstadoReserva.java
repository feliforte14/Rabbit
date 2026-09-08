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
 *             |
 *      registrarDevolucion()   (el pedido que la confirmo se cancelo)
 *             |
 *             v
 *        [DEVUELTA]
 *
 * VIGENTE es el unico estado no terminal. Los demas no vuelven atras: para
 * comprometer stock otra vez hay que crear una reserva nueva. DEVUELTA es
 * el unico que se alcanza desde otro estado final (CONFIRMADA), y solo por
 * esa via: es la contracara contable de la confirmacion, no un paso normal
 * del flujo.
 */
public enum EstadoReserva {

    /** Stock comprometido pero todavia no descontado. Tiene fecha de vencimiento. */
    VIGENTE,

    /** El cliente confirmo: el stock se descuenta definitivamente del deposito. */
    CONFIRMADA,

    /** El cliente desistio antes de vencer: el stock vuelve a estar libre. */
    LIBERADA,

    /** Se vencio el plazo sin confirmar. La libera el barredor automatico. */
    EXPIRADA,

    /**
     * La reserva estaba CONFIRMADA (stock ya descontado) pero el pedido que
     * la origino se cancelo: la cantidad se devolvio al stock disponible.
     * Ver InventarioService.registrarDevolucion.
     */
    DEVUELTA
}
