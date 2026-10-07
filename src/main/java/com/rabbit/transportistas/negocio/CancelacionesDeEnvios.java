package com.rabbit.transportistas.negocio;

/**
 * CAPA DE NEGOCIO — cancelaciones de envíos en los transportistas.
 *
 * Mismo problema y misma solución que ReversasBancarias con el banco: un
 * rollback de Rabbit no deshace lo que ya hizo un sistema externo. Si el
 * transportista tomó el envío y después la derivación se deshace, hay que
 * pedirle que lo cancele (transacción compensatoria).
 *
 *   - EnvioSolicitado + AFTER_FAILURE: la transacción que lo pidió se
 *     deshizo -> se cancela en el transportista.
 *   - EnvioCancelado + AFTER_SUCCESS: se canceló el pedido y quedó
 *     confirmado -> se cancela en el transportista.
 *
 * NOT_SUPPORTED: la llamada al transportista corre fuera de toda
 * transacción (la de Rabbit ya terminó). Si no responde, se loguea para
 * resolverlo a mano.
 */

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@Stateless
public class CancelacionesDeEnvios {

    private static final Logger LOG = Logger.getLogger(CancelacionesDeEnvios.class.getName());

    @Inject
    private AdaptadoresTransportista adaptadores;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void compensarEnvioDeshecho(@Observes(during = TransactionPhase.AFTER_FAILURE) EnvioSolicitado e) {
        LOG.info("[Transportistas] La derivación del pedido " + e.idPedido()
                + " se deshizo después de pedir el envío: se cancela " + e.codigoSeguimiento());
        cancelar(e.idPedido(), e.tipo(), e.endpoint(), e.codigoSeguimiento());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void avisarCancelacion(@Observes(during = TransactionPhase.AFTER_SUCCESS) EnvioCancelado e) {
        LOG.info("[Transportistas] Pedido " + e.idPedido() + " cancelado: se cancela el envío " + e.codigoSeguimiento());
        cancelar(e.idPedido(), e.tipo(), e.endpoint(), e.codigoSeguimiento());
    }

    private void cancelar(Long idPedido, com.rabbit.transportistas.datos.model.TipoIntegracion tipo,
                          String endpoint, String codigo) {
        if (!adaptadores.para(tipo).cancelarEnvio(endpoint, codigo)) {
            LOG.severe("[Transportistas] El transportista no confirmó la cancelación de " + codigo
                    + " (pedido " + idPedido + "): hay que cancelarlo a mano");
        }
    }
}
