package com.rabbit.pedidos.datos.model;

import java.util.Set;

/**
 * Estados por los que pasa un pedido dentro de Rabbit.
 *
 *   sincronizarPedidoExterno()
 *            |
 *            v
 *       [PENDIENTE] --- confirmarPedido() ---> [CONFIRMADO] --- despacharPedido() ---> [EN_CAMINO]
 *            |                                      |                                      |
 *      cancelarPedido()                       cancelarPedido()                      registrarEntrega()
 *            |                                      |                                      |
 *            v                                      v                                      v
 *       [CANCELADO] <-------------------------------+                                [ENTREGADO]
 *
 * Una vez EN_CAMINO el pedido ya salió con el repartidor: no se puede
 * cancelar desde Rabbit (la mercadería no está en el depósito para
 * devolver el stock). ENTREGADO y CANCELADO son finales.
 *
 * Con origen STOCK_CONSIGNADO (ver OrigenPedido), el stock ya queda
 * comprometido (reservado y confirmado en ServicioDeInventario) desde que
 * el pedido entra en PENDIENTE — no hay, en el alcance de esta entrega, un
 * usuario en vivo decidiendo si confirmar o no la reserva de stock: esa
 * decisión ya la tomó el comercio al generar el pedido en su ERP. Con
 * origen PUNTO_PICKING no hay stock que comprometer (Rabbit no lo
 * gestiona): PENDIENTE ahí solo significa "a retirar del comercio".
 * Los estados reflejan el avance del pedido dentro de la orquestación de
 * Rabbit (confirmación, reparto, entrega), no el compromiso de stock.
 */
public enum EstadoPedido {
    PENDIENTE,
    CONFIRMADO,
    EN_CAMINO,
    ENTREGADO,
    CANCELADO;

    /**
     * Máquina de estados del pedido en un solo lugar: PedidoService la
     * consulta antes de cada cambio en vez de repetir los if en cada
     * operación. También protege contra eventos o clics que llegan fuera
     * de orden (por ejemplo, "entregar" sobre un pedido que se canceló).
     */
    public boolean puedePasarA(EstadoPedido destino) {
        return switch (this) {
            case PENDIENTE -> Set.of(CONFIRMADO, CANCELADO).contains(destino);
            case CONFIRMADO -> Set.of(EN_CAMINO, CANCELADO).contains(destino);
            case EN_CAMINO -> destino == ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };
    }
}
