package com.rabbit.pedidos.datos.model;

/**
 * Estados por los que pasa un pedido dentro de Rabbit.
 *
 *   sincronizarPedidoExterno()
 *            |
 *            v
 *       [PENDIENTE] --- confirmarPedido() ---> [CONFIRMADO]
 *            |
 *      cancelarPedido()
 *            |
 *            v
 *       [CANCELADO]
 *
 * El stock ya queda comprometido (reservado y confirmado en
 * ServicioDeInventario) desde que el pedido entra en PENDIENTE — no hay,
 * en el alcance de esta entrega, un usuario en vivo decidiendo si
 * confirmar o no la reserva de stock: esa decisión ya la tomó el comercio
 * al generar el pedido en su ERP. PENDIENTE/CONFIRMADO acá reflejan el
 * avance del pedido dentro de la orquestación de Rabbit (ruteo, entrega),
 * no el compromiso de stock en sí.
 */
public enum EstadoPedido {
    PENDIENTE,
    CONFIRMADO,
    CANCELADO
}
