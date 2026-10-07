package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — integración asincrónica del componente Pedidos
 * (EJB @Stateless, productor JMS publicación/suscripción)
 *
 * QUÉ RESUELVE
 * Cada cambio de estado de un pedido le interesa a más de un componente,
 * cada uno por su motivo: Pagos (con ENTREGADO y CONTRA_ENTREGA efectiviza
 * el cobro) y Notificaciones (avisa al comercio). Esta clase publica cada
 * EstadoPedidoCambiado en "topico.pedidos.estado"; Pedidos no sabe quién
 * lo escucha, así que sumar un suscriptor no toca este componente.
 *
 * POR QUÉ TOPIC Y NO QUEUE
 * Es 1 → N: el MISMO evento lo tienen que recibir Pagos Y Notificaciones.
 * Con una cola, cada mensaje lo consumiría uno solo de ellos. Es el caso
 * opuesto a cola.pedidos.externos (PublicadorPedidosExternos), donde cada
 * pedido lo tiene que procesar exactamente un consumidor.
 *
 * TRANSACCIÓN: mismo esquema que PublicadorPedidosExternos (ver su
 * comentario de método): AFTER_SUCCESS + NOT_SUPPORTED. El aviso sale
 * recién cuando el cambio de estado ya está confirmado en la base, y una
 * falla del broker no deshace el cambio.
 *
 * MENSAJE: TextMessage con el JSON {idPedido, idComercio, estado,
 * fechaCambio} y dos propiedades JMS (estado, idPedido): los suscriptores
 * pueden filtrar con un messageSelector sin leer el body — Pagos, por
 * ejemplo, solo se suscribe a estado = 'ENTREGADO'.
 */

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.json.Json;
import java.util.logging.Level;
import java.util.logging.Logger;

// El tópico lo declara la aplicación, como la cola; la connection factory
// es la misma (declarada en PublicadorPedidosExternos, conector in-vm).
@JMSDestinationDefinition(
        name = "java:/jms/topic/EstadosPedido",
        interfaceName = "jakarta.jms.Topic",
        destinationName = "topico.pedidos.estado"
)
@Stateless
public class PublicadorEstadosPedido {

    private static final Logger LOG = Logger.getLogger(PublicadorEstadosPedido.class.getName());

    @Resource(lookup = "java:/jms/rabbit/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(lookup = "java:/jms/topic/EstadosPedido")
    private Topic topicoEstados;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void publicarCambioDeEstado(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) EstadoPedidoCambiado evento) {
        try (JMSContext contexto = connectionFactory.createContext()) {
            String payload = Json.createObjectBuilder()
                    .add("idPedido", evento.idPedido())
                    .add("idComercio", evento.idComercio())
                    .add("estado", evento.estado().name())
                    .add("fechaCambio", evento.fechaCambio().toString())
                    .build()
                    .toString();
            TextMessage mensaje = contexto.createTextMessage(payload);
            mensaje.setStringProperty("estado", evento.estado().name());
            mensaje.setLongProperty("idPedido", evento.idPedido());

            contexto.createProducer().send(topicoEstados, mensaje);
            LOG.info("[Pedidos] Publicado en topico.pedidos.estado: pedido " + evento.idPedido()
                    + " -> " + evento.estado());
        } catch (RuntimeException | JMSException e) {
            // Mismo criterio que la cola: el cambio de estado ya está guardado;
            // lo que se pierde es el aviso (ver docs/integraciones/MENSAJERIA-ASINCRONICA.md).
            LOG.log(Level.WARNING, "[Pedidos] No se pudo publicar el cambio de estado del pedido "
                    + evento.idPedido() + " en el tópico", e);
        }
    }
}
