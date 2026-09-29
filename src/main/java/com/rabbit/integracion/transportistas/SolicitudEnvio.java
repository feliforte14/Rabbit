package com.rabbit.integracion.transportistas;

import java.math.BigDecimal;

/**
 * Lo que Rabbit le pide a un transportista, igual para todos: cada
 * adaptador lo traduce al formato de su transportista.
 *
 * @param referencia       identificador del lado de Rabbit (PEDIDO-n)
 * @param direccionRetiro  de dónde se retira
 * @param direccionEntrega adónde se entrega
 * @param bultos           cantidad de unidades
 * @param cobrarAlEntregar importe a cobrar al entregar; null si ya está pagado
 */
public record SolicitudEnvio(String referencia, String direccionRetiro, String direccionEntrega,
                             int bultos, BigDecimal cobrarAlEntregar) {
}
