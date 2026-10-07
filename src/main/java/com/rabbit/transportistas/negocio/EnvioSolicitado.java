package com.rabbit.transportistas.negocio;

import com.rabbit.transportistas.datos.model.TipoIntegracion;

/**
 * Evento CDI: un transportista tomó un envío. Si la transacción que lo
 * pidió se deshace, CancelacionesDeEnvios lo cancela en el transportista.
 */
public record EnvioSolicitado(Long idPedido, TipoIntegracion tipo, String endpoint, String codigoSeguimiento) {
}
