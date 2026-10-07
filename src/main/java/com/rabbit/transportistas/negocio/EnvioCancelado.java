package com.rabbit.transportistas.negocio;

import com.rabbit.transportistas.datos.model.TipoIntegracion;

/**
 * Evento CDI: Rabbit canceló un envío (se canceló el pedido). Cuando esa
 * transacción se confirma, CancelacionesDeEnvios se lo avisa al transportista.
 */
public record EnvioCancelado(Long idPedido, TipoIntegracion tipo, String endpoint, String codigoSeguimiento) {
}
