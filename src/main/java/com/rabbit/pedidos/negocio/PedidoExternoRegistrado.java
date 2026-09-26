package com.rabbit.pedidos.negocio;

import com.rabbit.pedidos.datos.model.OrigenPedido;

/**
 * Evento CDI que dispara PedidoService.registrarPedidoExterno() al guardar
 * la fila. Lo observa PublicadorPedidosExternos con
 * TransactionPhase.AFTER_SUCCESS: el mensaje JMS sale recién cuando el
 * INSERT ya está confirmado (ver ese comentario de clase).
 */
public record PedidoExternoRegistrado(Long idPedidoExterno, OrigenPedido origen) {
}
