package com.rabbit.pedidos.negocio;

/**
 * Evento CDI: quien recibe el pedido dejó o cambió las instrucciones de entrega. Lo dispara
 * PedidoService.modificarEntrega y lo observa EtiquetadoDeInstrucciones, recién cuando el cambio ya está guardado.
 * Lleva el ID del pedido y el texto (que es de un tercero y no va al log).
 */
public record InstruccionesModificadas(Long idPedido, String instrucciones) {
}
