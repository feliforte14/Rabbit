package com.rabbit.pagos.negocio;

/**
 * CAPA DE NEGOCIO — devoluciones de plata en el banco legado.
 *
 * EL PROBLEMA QUE RESUELVE
 * El rollback de una transacción de Rabbit deshace lo que Rabbit escribió
 * en SU base, pero no lo que ya hizo un sistema externo: si el banco
 * autorizó un pago y después la confirmación del pedido falla (por
 * ejemplo, no hay repartidor), el cliente quedaría cobrado por un pedido
 * que no se confirmó. La única forma de deshacerlo es pedirle al banco
 * la operación inversa: una TRANSACCIÓN COMPENSATORIA.
 *
 * CUÁNDO SE REVERSA
 *   - PagoAutorizado + AFTER_FAILURE: la transacción que cobró se deshizo
 *     → se devuelve la plata (compensación).
 *   - CobroAnulado + AFTER_SUCCESS: se canceló un pedido ya cobrado y la
 *     cancelación quedó confirmada → se devuelve la plata.
 *
 * Mismo mecanismo que los publicadores JMS (observers transaccionales),
 * pero reaccionando al fracaso en vez de al éxito. NOT_SUPPORTED: la
 * llamada al banco corre fuera de toda transacción (la de Rabbit ya
 * terminó).
 *
 * Si el banco no responde a la reversa, se loguea para devolverla a mano:
 * es el límite de este diseño simple (lo resolvería un reintento
 * programado).
 */

import com.rabbit.integracion.banco.IBancoClient;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@Stateless
public class ReversasBancarias {

    private static final Logger LOG = Logger.getLogger(ReversasBancarias.class.getName());

    @Inject
    private IBancoClient banco;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void compensarPagoDeshecho(@Observes(during = TransactionPhase.AFTER_FAILURE) PagoAutorizado evento) {
        LOG.info("[Pagos] La confirmación del pedido " + evento.idPedido()
                + " se deshizo después de cobrar: se reversa " + evento.codigoAutorizacion());
        reversar(evento.idPedido(), evento.codigoAutorizacion());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void devolverCobroAnulado(@Observes(during = TransactionPhase.AFTER_SUCCESS) CobroAnulado evento) {
        LOG.info("[Pagos] Cobro del pedido " + evento.idPedido() + " anulado: se reversa " + evento.codigoAutorizacion());
        reversar(evento.idPedido(), evento.codigoAutorizacion());
    }

    private void reversar(Long idPedido, String codigo) {
        if (!banco.reversar(codigo)) {
            LOG.severe("[Pagos] El banco no confirmó la reversa " + codigo + " del pedido " + idPedido
                    + ": hay que devolverla a mano");
        }
    }
}
