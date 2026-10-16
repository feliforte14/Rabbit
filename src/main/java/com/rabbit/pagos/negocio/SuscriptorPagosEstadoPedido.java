package com.rabbit.pagos.negocio;

/**
 * CAPA DE NEGOCIO — suscriptor de Pagos al tópico de estados del pedido
 * (Message-Driven Bean, publicación/suscripción)
 *
 * Efectiviza el cobro CONTRA_ENTREGA cuando el pedido pasa a ENTREGADO:
 * el repartidor cobró al entregar. Delega en
 * IRegistroCobros.registrarCobroContraEntrega, que es idempotente (si el
 * cobro ya está ACREDITADO no hace nada) — un tópico puede entregar el
 * mismo evento más de una vez.
 *
 * Con DEVUELTO_AL_COMERCIO (ADR-041) anula el cobro: el pedido no se
 * entregó. También idempotente (anularCobroPorDevolucion).
 *
 * messageSelector: el broker solo le entrega los mensajes con la propiedad
 * JMS estado ENTREGADO o DEVUELTO_AL_COMERCIO. Los otros cambios de estado
 * ni llegan a este MDB (los recibe igual Notificaciones, que no filtra).
 *
 * SUSCRIPCIÓN DURABLE: si Rabbit se redespliega o este MDB está caído
 * cuando se publica un ENTREGADO, el broker guarda el mensaje para esta
 * suscripción ("rabbit-pagos" / "pagos-estados-pedido") y lo entrega al
 * volver. Con una suscripción común ese cobro se perdería.
 * shareSubscriptions permite que las varias instancias del pool del MDB
 * consuman de la misma suscripción durable.
 *
 * ORDEN: no importa acá. ENTREGADO y DEVUELTO_AL_COMERCIO son finales, así
 * que no puede llegar "después" un evento más nuevo que los contradiga.
 */

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import java.util.logging.Level;
import java.util.logging.Logger;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/EstadosPedido"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "estado IN ('ENTREGADO', 'DEVUELTO_AL_COMERCIO')"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "rabbit-pagos"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "pagos-estados-pedido"),
        @ActivationConfigProperty(propertyName = "shareSubscriptions", propertyValue = "true")
})
public class SuscriptorPagosEstadoPedido implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorPagosEstadoPedido.class.getName());

    @Inject
    private IRegistroCobros cobros;

    @Override
    public void onMessage(Message mensaje) {
        Long idPedido;
        String estado;
        try {
            // Las propiedades alcanzan: no hace falta parsear el JSON del body.
            idPedido = mensaje.getLongProperty("idPedido");
            estado = mensaje.getStringProperty("estado");
        } catch (JMSException | NumberFormatException e) {
            LOG.log(Level.SEVERE, "[Pagos][JMS] Mensaje ilegible en topico.pedidos.estado", e);
            return;
        }
        // Una RuntimeException (por ejemplo, la base caída) se deja propagar:
        // el contenedor no confirma el mensaje y el broker lo reintenta.
        if ("DEVUELTO_AL_COMERCIO".equals(estado)) {
            cobros.anularCobroPorDevolucion(idPedido);
        } else {
            cobros.registrarCobroContraEntrega(idPedido);
        }
        LOG.info("[Pagos][JMS] Procesado " + estado + " del pedido " + idPedido);
    }
}
