package com.rabbit.pedidos.datos.model;

/**
 * Cómo recibe el pedido quien lo compró (ADR-053).
 *
 *  - DOMICILIO: lo lleva un repartidor de Rabbit o un transportista a la dirección de entrega.
 *  - RETIRO_EN_DEPOSITO: quien recibe lo busca en un depósito de Rabbit. No se asigna repartidor
 *    ni transportista y no pasa por Ruteo; se entrega mostrando el código de seguimiento.
 *
 * Los pedidos anteriores a este cambio no tienen valor guardado y cuentan como DOMICILIO.
 */
public enum TipoEntrega {
    DOMICILIO,
    RETIRO_EN_DEPOSITO
}
