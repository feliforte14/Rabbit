package com.rabbit.notificaciones.negocio;

/**
 * CAPA DE NEGOCIO — tercer suscriptor al tópico de estados del pedido
 * (Message-Driven Bean, publicación/suscripción, ADR-040): le avisa a quien
 * recibe el pedido.
 *
 * Es el mismo mensaje que reciben Pagos y los avisos al comercio. Sumarlo
 * no tocó a Pedidos (el publicador no sabe quién escucha) ni a los otros
 * dos suscriptores: es el argumento a favor de un tópico frente a una cola
 * o a llamadas directas (ver docs/integraciones/MENSAJERIA-ASINCRONICA.md).
 *
 * Suscripción durable propia ("rabbit-destinatarios"): si este MDB no está
 * activo, sus mensajes esperan, sin depender de que los otros suscriptores
 * ya los hayan consumido. El orden y los duplicados los resuelve
 * AvisoDestinatarioService con fechaCambio.
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
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "rabbit-destinatarios"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "destinatarios-estados-pedido"),
        @ActivationConfigProperty(propertyName = "shareSubscriptions", propertyValue = "true")
})
public class SuscriptorDestinatarioEstadoPedido implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorDestinatarioEstadoPedido.class.getName());

    @Inject
    private IAvisosAlDestinatario avisos;

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
            avisos.avisar(
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
