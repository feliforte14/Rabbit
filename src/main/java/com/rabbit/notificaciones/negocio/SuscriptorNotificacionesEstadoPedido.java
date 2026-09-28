package com.rabbit.notificaciones.negocio;

/**
 * CAPA DE NEGOCIO — suscriptor de Notificaciones al tópico de estados del
 * pedido (Message-Driven Bean, publicación/suscripción)
 *
 * A diferencia de Pagos, no filtra: todo cambio de estado genera un aviso
 * al comercio. Es el mismo mensaje que recibe SuscriptorPagosEstadoPedido
 * — eso es lo que hace un tópico (1 → N) y una cola no.
 *
 * Suscripción durable ("rabbit-notificaciones"): un aviso publicado
 * mientras este MDB no está activo se entrega cuando vuelve. El orden y
 * los duplicados los resuelve NotificacionService con fechaCambio.
 */

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/EstadosPedido"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "rabbit-notificaciones"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "notificaciones-estados-pedido"),
        @ActivationConfigProperty(propertyName = "shareSubscriptions", propertyValue = "true")
})
public class SuscriptorNotificacionesEstadoPedido implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorNotificacionesEstadoPedido.class.getName());

    @Inject
    private INotificaciones notificaciones;

    @Override
    public void onMessage(Message mensaje) {
        JsonObject json;
        try {
            if (!(mensaje instanceof TextMessage texto)) {
                LOG.severe("[Notificaciones][JMS] Mensaje de tipo inesperado: " + mensaje.getClass().getName());
                return;
            }
            try (StringReader reader = new StringReader(texto.getText())) {
                json = Json.createReader(reader).readObject();
            }
            notificaciones.avisarCambioDeEstado(
                    json.getJsonNumber("idPedido").longValue(),
                    json.getJsonNumber("idComercio").longValue(),
                    json.getString("estado"),
                    LocalDateTime.parse(json.getString("fechaCambio")));
        } catch (JMSException | JsonException | ClassCastException | NullPointerException | DateTimeParseException e) {
            // Mensaje ilegible: reintentarlo no lo arregla, se consume y se loguea.
            LOG.log(Level.SEVERE, "[Notificaciones][JMS] Mensaje ilegible en topico.pedidos.estado", e);
        }
    }
}
