package com.rabbit.integracion.transportistas;

/**
 * Estado de un envío traducido al modelo de Rabbit: cada adaptador mapea el
 * vocabulario de su transportista a estos valores. DESCONOCIDO cuando el
 * transportista no respondió o no conoce el código.
 */
public enum EstadoExterno {
    SOLICITADO,
    EN_TRANSITO,
    ENTREGADO,
    CANCELADO,
    DESCONOCIDO
}
