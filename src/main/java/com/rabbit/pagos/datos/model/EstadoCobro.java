package com.rabbit.pagos.datos.model;

/**
 * Estados de un cobro.
 *
 *   registrarCobro()
 *     PREPAGO ---------> [ACREDITADO]
 *     CONTRA_ENTREGA --> [PENDIENTE] --- registrarCobroContraEntrega() ---> [ACREDITADO]
 *
 *   anularCobro(): PENDIENTE o ACREDITADO ---> [ANULADO]
 *
 * Un pago rechazado por la pasarela no llega a guardarse: la excepción
 * deshace toda la confirmación del pedido (ver PagoService).
 */
public enum EstadoCobro {
    PENDIENTE,
    ACREDITADO,
    ANULADO
}
