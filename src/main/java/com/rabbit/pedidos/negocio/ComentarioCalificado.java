package com.rabbit.pedidos.negocio;

/**
 * Evento CDI: quien recibió el pedido dejó un comentario al calificar la entrega. Lo dispara
 * PedidoService.calificarEntrega y lo observa ClasificacionDeComentarios, recién cuando la calificación ya está
 * guardada. Lleva solo el ID de la calificación y el texto del comentario (que es de un tercero y no va al log).
 */
public record ComentarioCalificado(Long idCalificacion, String comentario) {
}
