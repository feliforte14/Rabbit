package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — envío al tópico de los avisos de cambio de estado que
 * están en la bandeja (ver BandejaDeEstados).
 *
 * Lo llaman dos caminos, con la misma lógica:
 * - PublicadorEstadosPedido, justo después del commit del cambio de estado
 *   (el camino rápido: el aviso sale en el momento);
 * - un timer cada 15 segundos (la red de contención): reenvía lo que no pudo
 *   salir porque el broker estaba caído, apenas vuelve.
 *
 * @Singleton: con la concurrencia por defecto (WRITE) los dos caminos no se
 * pisan dentro del mismo servidor, así que no se manda dos veces el mismo
 * pendiente. Entre servidores distintos podría repetirse un aviso: es "al
 * menos una vez", y los suscriptores lo toleran (Pagos solo acredita un
 * cobro pendiente; Notificaciones descarta lo que no es más nuevo que lo ya
 * avisado).
 *
 * Se envía en orden. Si un envío falla, se corta la pasada (si el broker
 * está caído, los demás también fallarían) y el resto espera a la próxima.
 */

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
public class EnvioDeEstadosPendientes {

    private static final Logger LOG = Logger.getLogger(EnvioDeEstadosPendientes.class.getName());

    /** Cuántos pendientes se mandan por pasada. */
    static final int LOTE = 200;

    /** Días que se conservan los ya publicados (sirven para auditar). */
    static final int DIAS_DE_CONSERVACION = 7;

    @Resource(lookup = "java:/jms/rabbit/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(lookup = "java:/jms/topic/EstadosPedido")
    private Topic topicoEstados;

    @Inject
    private BandejaDeEstados bandeja;

    @PostConstruct
    public void alArrancar() {
        LOG.info("[Pedidos] Reenvío de avisos de estado activo: cada 15 s manda los pendientes de la bandeja");
    }

    /** Red de contención: lo que no salió (broker caído) sale cuando el broker vuelve. */
    @Schedule(hour = "*", minute = "*", second = "*/15", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void reenviarPendientes() {
        enviarPendientes();
    }

    /** Los ya publicados se borran a los 7 días. */
    @Schedule(hour = "3", minute = "20", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void limpiarPublicados() {
        int borrados = bandeja.limpiarPublicadosAnterioresA(LocalDateTime.now().minusDays(DIAS_DE_CONSERVACION));
        if (borrados > 0) {
            LOG.info("[Pedidos] Bandeja de estados: " + borrados + " avisos publicados borrados");
        }
    }

    /**
     * Manda al tópico los avisos pendientes y marca cada uno como publicado.
     * Devuelve cuántos salieron. Sin transacción: el envío sale en el momento
     * y cada marca abre la suya (ver BandejaDeEstados).
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int enviarPendientes() {
        List<BandejaDeEstados.Pendiente> pendientes;
        try {
            pendientes = bandeja.pendientes(LOTE);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[Pedidos] No se pudo leer la bandeja de avisos de estado", e);
            return 0;
        }
        if (pendientes.isEmpty()) {
            return 0;
        }
        int enviados = 0;
        try (JMSContext contexto = connectionFactory.createContext()) {
            for (BandejaDeEstados.Pendiente pendiente : pendientes) {
                try {
                    enviar(contexto, pendiente.evento());
                } catch (RuntimeException | JMSException e) {
                    bandeja.registrarFallo(pendiente.id());
                    LOG.log(Level.WARNING, "[Pedidos] No se pudo publicar el cambio de estado del pedido "
                            + pendiente.evento().idPedido() + " (queda pendiente, se reintenta)", e);
                    break;
                }
                bandeja.marcarPublicado(pendiente.id());
                enviados++;
            }
        } catch (RuntimeException e) {
            // No se pudo ni abrir la conexión con el broker: todo queda pendiente.
            LOG.log(Level.WARNING, "[Pedidos] Broker no disponible: " + pendientes.size()
                    + " avisos de estado quedan pendientes y se reintentan", e);
        }
        return enviados;
    }

    private void enviar(JMSContext contexto, EstadoPedidoCambiado evento) throws JMSException {
        TextMessage mensaje = contexto.createTextMessage(PublicadorEstadosPedido.construirPayload(evento));
        mensaje.setStringProperty("estado", evento.estado().name());
        mensaje.setLongProperty("idPedido", evento.idPedido());
        contexto.createProducer().send(topicoEstados, mensaje);
        LOG.info("[Pedidos] Publicado en topico.pedidos.estado: pedido " + evento.idPedido()
                + " -> " + evento.estado());
    }
}
