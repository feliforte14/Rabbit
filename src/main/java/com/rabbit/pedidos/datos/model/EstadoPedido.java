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
 * Devolución al depósito (devolverADeposito): un pedido CONFIRMADO o
 * EN_CAMINO que el repartidor no pudo entregar (cliente ausente, no entra
 * en el vehículo...) vuelve a PENDIENTE, en la lista de espera, para que
 * se le asigne a otro repartidor. Conserva su cobro.
 *
 * Entrega fallida (ADR-041): cada visita EN_CAMINO que no termina en
 * entrega cuenta como intento. Al agotar los intentos, o si el cliente lo
 * rechazó, no vuelve a PENDIENTE sino que pasa a DEVUELTO_AL_COMERCIO
 * (final): el stock consignado vuelve al stock del comercio y el cobro se
 * anula.
 *
 *       [EN_CAMINO] --- intentos agotados o rechazo ---> [DEVUELTO_AL_COMERCIO]
 *
 * Una vez EN_CAMINO el pedido ya salió con el repartidor: no se puede
 * cancelar desde Rabbit (la mercadería no está en el depósito para
 * devolver el stock). ENTREGADO, CANCELADO y DEVUELTO_AL_COMERCIO son
 * finales.
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
    CANCELADO,
    DEVUELTO_AL_COMERCIO;

    /**
     * Máquina de estados del pedido en un solo lugar: PedidoService la
     * consulta antes de cada cambio en vez de repetir los if en cada
     * operación. También protege contra eventos o clics que llegan fuera
     * de orden (por ejemplo, "entregar" sobre un pedido que se canceló).
     */
    public boolean puedePasarA(EstadoPedido destino) {
        return switch (this) {
            case PENDIENTE -> Set.of(CONFIRMADO, CANCELADO).contains(destino);
            case CONFIRMADO -> Set.of(EN_CAMINO, CANCELADO, PENDIENTE).contains(destino);
            case EN_CAMINO -> Set.of(ENTREGADO, PENDIENTE, DEVUELTO_AL_COMERCIO).contains(destino);
            case ENTREGADO, CANCELADO, DEVUELTO_AL_COMERCIO -> false;
        };
    }
}
