package com.rabbit.pagos.dto;

/**
 * Cómo se cobra un pedido. Es parte del CONTRATO de ServicioDePagosYCobranzas
 * (por eso vive en su paquete dto/): Pedidos lo guarda tal como lo mandó el
 * ERP del comercio y se lo pasa a IRegistroCobros al confirmar.
 *
 *   PREPAGO:        el cliente ya pagó al comprar; al confirmar el pedido
 *                   se cobra en el banco legado (SOAP).
 *   CONTRA_ENTREGA: el repartidor cobra al entregar; al confirmar el
 *                   pedido el cobro queda PENDIENTE y se efectiviza cuando
 *                   el pedido pasa a ENTREGADO (ver EstadoPedido).
 */
public enum MedioPago {
    PREPAGO,
    CONTRA_ENTREGA
}
