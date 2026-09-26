package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — integración asincrónica del componente Pedidos (EJB
 * @Stateless, productor JMS punto a punto)
 *
 * QUÉ RESUELVE
 * Hasta esta entrega, un pedido externo (fila de "pedidos_externos", ver
 * PedidoExterno) quedaba pendiente hasta que SincronizadorDePedidos pasaba
 * a revisarlo — en el peor caso, casi un minuto entero de demora, y
 * siempre por polling: Rabbit pregunta "¿hay algo nuevo?" en vez de que el
 * ERP avise "llegó un pedido".
 *
 * Esta clase invierte eso: en cuanto se confirma la fila que guardó
 * registrarPedidoExterno() (ver PedidoService y el evento
 * PedidoExternoRegistrado), publica un mensaje en la cola
 * "cola.pedidos.externos" con el ID recién creado. Es el Productor del
 * modelo punto a punto (JMS) — el mismo rol que PedidoProducer en el
 * ejemplo de la cátedra, aplicado al dominio real de Rabbit.
 *
 * POR QUÉ PUNTO A PUNTO (Queue) Y NO PUBLICACIÓN/SUSCRIPCIÓN (Topic)
 * Cada pedido externo tiene que sincronizarse exactamente UNA vez — dos
 * consumidores procesando el mismo pedido en paralelo duplicaría la
 * reserva de stock. Ese "cada mensaje lo resuelve un único responsable"
 * es la definición misma de punto a punto; pub/sub (difusión 1 a N)
 * hubiera sido la elección incorrecta acá.
 *
 * POR QUÉ SIGUE EXISTIENDO SincronizadorDePedidos
 * El envío del mensaje y el guardado de la fila NO comparten transacción
 * (ver comentario de método): si el mensaje no sale por lo que sea, el
 * @Schedule de SincronizadorDePedidos lo va a encontrar igual en su
 * próxima pasada. Pasa de ser el camino principal a ser la red de
 * contención — el mensaje es el camino rápido, el polling es el que
 * garantiza que nada quede huérfano.
 */

import com.rabbit.pedidos.datos.model.OrigenPedido;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConnectionFactoryDefinition;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSConnectionFactoryDefinitions;
import jakarta.jms.JMSDestinationDefinitions;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import jakarta.json.Json;

import java.util.logging.Level;
import java.util.logging.Logger;

// Recursos JMS declarados por la propia aplicación (portables, no dependen
// de que el admin de WildFly haya creado nada a mano por consola): el
// contenedor los provisiona al desplegar, contra el broker ActiveMQ
// Artemis embebido (requiere el perfil "standalone-full.xml" de WildFly,
// que trae activo el subsistema messaging-activemq).
@JMSConnectionFactoryDefinitions({
        @JMSConnectionFactoryDefinition(
                name = "java:/jms/rabbit/ConnectionFactory",
                // Conector in-vm: el broker corre dentro del mismo WildFly.
                // Sin esto WildFly usa el http-connector (conexión remota),
                // que exige usuario/contraseña y el envío falla con
                // AMQ229031 "Unable to validate user ... Username: null".
                properties = {"connectors=in-vm"}
        )
})
@JMSDestinationDefinitions({
        @JMSDestinationDefinition(
                name = "java:/jms/queue/PedidosExternos",
                interfaceName = "jakarta.jms.Queue",
                destinationName = "cola.pedidos.externos"
        )
})
@Stateless
public class PublicadorPedidosExternos {

    private static final Logger LOG = Logger.getLogger(PublicadorPedidosExternos.class.getName());

    @Resource(lookup = "java:/jms/rabbit/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(lookup = "java:/jms/queue/PedidosExternos")
    private Queue colaPedidosExternos;

    /**
     * Publica el aviso de un pedido externo recién registrado.
     *
     * NO VA DENTRO DE LA TRANSACCIÓN DE registrarPedidoExterno A PROPÓSITO
     * Llamarlo directo desde PedidoService lo metería en esa transacción
     * (REQUIRED): la connection factory es transaccional, así que el envío
     * se enlistaría con el INSERT y una falla del broker al confirmar
     * voltearía también un pedido externo válido. Por eso:
     *
     * - AFTER_SUCCESS: el observer corre recién cuando la transacción de
     *   registrarPedidoExterno ya confirmó. El listener nunca recibe un ID
     *   que todavía no existe en la base, y si el INSERT se deshace, el
     *   mensaje directamente no sale.
     * - NOT_SUPPORTED: el envío corre fuera de toda transacción, así que
     *   sale en el momento y su falla no toca nada más.
     *
     * Si el envío falla (broker caído, por ejemplo), el pedido externo
     * existe igual y SincronizadorDePedidos lo va a levantar por polling
     * como red de contención. Publicar el mensaje es una notificación
     * best-effort de "hay algo nuevo, andá a mirar", no la única fuente de
     * verdad.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void publicarPedidoExternoRegistrado(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) PedidoExternoRegistrado evento) {
        Long idPedidoExterno = evento.idPedidoExterno();
        OrigenPedido origen = evento.origen();
        try (JMSContext contexto = connectionFactory.createContext()) {
            String payload = Json.createObjectBuilder()
                    .add("idPedidoExterno", idPedidoExterno)
                    .build()
                    .toString();
            TextMessage mensaje = contexto.createTextMessage(payload);
            // Property JMS (no forma parte del body): permite en el futuro
            // que un consumidor filtre por tipo de origen sin deserializar
            // el body — mismo concepto de "Properties" visto en la clase.
            mensaje.setStringProperty("origen", origen.name());

            contexto.createProducer().send(colaPedidosExternos, mensaje);
            LOG.info("[Pedidos] Publicado en cola.pedidos.externos: pedido externo " + idPedidoExterno);
        } catch (RuntimeException | JMSException e) {
            // No se relanza: ver el comentario del método. El polling de
            // SincronizadorDePedidos es la red de contención para este caso.
            LOG.log(Level.WARNING,
                    "[Pedidos] No se pudo publicar el pedido externo " + idPedidoExterno
                            + " en la cola; queda pendiente para el polling de SincronizadorDePedidos.",
                    e);
        }
    }
}
