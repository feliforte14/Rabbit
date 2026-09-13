package com.rabbit.pedidos.datos.model;

/**
 * De dónde sale la mercadería que un pedido compromete.
 *
 * STOCK_CONSIGNADO: el pedido se cumple con stock que el comercio ya
 * consignó en un depósito de Rabbit (ver ItemInventario/Deposito). Es el
 * flujo original: reserva y confirma stock en ServicioDeInventario.
 *
 * PUNTO_PICKING: el pedido ya fue armado y validado por el propio
 * comercio en uno de sus puntos de picking (ver PuntoPicking) — Rabbit no
 * gestiona ni conoce el detalle de ese stock, así que no hay nada que
 * reservar. El rol de Rabbit se reduce a retirarlo y transportarlo.
 */
public enum OrigenPedido {
    STOCK_CONSIGNADO,
    PUNTO_PICKING
}
