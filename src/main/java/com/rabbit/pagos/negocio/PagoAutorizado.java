package com.rabbit.pagos.negocio;

/**
 * Evento CDI que dispara PagoService cuando el banco autorizó un cobro
 * dentro de la transacción de confirmarPedido. Lo observa
 * ReversasBancarias con TransactionPhase.AFTER_FAILURE: si esa transacción
 * termina deshaciéndose, hay que devolverle la plata al cliente en el
 * banco, porque el rollback de Rabbit no llega hasta ahí.
 */
public record PagoAutorizado(Long idPedido, String codigoAutorizacion) {
}
