package com.rabbit.pagos.negocio;

/**
 * Evento CDI que dispara PagoService.anularCobro sobre un cobro que el
 * banco ya había autorizado. Lo observa ReversasBancarias con
 * TransactionPhase.AFTER_SUCCESS: la reversa se le pide al banco recién
 * cuando la cancelación del pedido quedó confirmada en Rabbit.
 */
public record CobroAnulado(Long idPedido, String codigoAutorizacion) {
}
