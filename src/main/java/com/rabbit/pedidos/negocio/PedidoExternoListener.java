package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — integración asincrónica del componente Pedidos
 * (Message-Driven Bean, consumidor JMS punto a punto)
 *
 * QUÉ ES Y POR QUÉ UN MDB
 * Un @MessageDriven es el consumidor administrado por el contenedor: no
 * hay un @Schedule que "vaya a preguntar" a la cola — WildFly invoca
 * onMessage() automáticamente en cuanto llega un mensaje a
 * "cola.pedidos.externos" (la misma cola que declara y publica
 * PublicadorPedidosExternos). Es el Consumidor del modelo punto a punto
 * visto en la clase, con la misma garantía: si se desplegaran varias
 * instancias de este MDB, compiten por los mensajes — cada pedido externo
 * lo procesa una sola de ellas, nunca dos a la vez.
 *
 * QUÉ HACE ACÁ ADENTRO
 * No reimplementa la lógica de sincronización: delega a
 * IGestionPedidos.sincronizarPedidoExterno(...), el mismo Facade que ya
 * usa SincronizadorDePedidos. El MDB es solo el disparador asincrónico —
 * toda la regla de negocio (validar comercio, reservar stock, armar el
 * pedido real) sigue viviendo en un único lugar (PedidoService), evitando
 * duplicar lógica entre el camino "por mensaje" y el camino "por polling".
 *
 * MANEJO DE ERRORES: ACK DEL MENSAJE VS. RESULTADO DE NEGOCIO
 * Con transacciones gestionadas por el contenedor (el default de un MDB),
 * el consumo del mensaje forma parte de la transacción de onMessage(): si
 * onMessage() termina normalmente, el mensaje se da por entregado y se
 * borra de la cola; si lanza una RuntimeException, la transacción se
 * deshace y el broker lo vuelve a entregar más tarde.
 *
 * - PedidoYaSincronizadoException: el polling ya lo procesó, o es una
 *   redelivery de un mensaje cuyo procesamiento ya se había confirmado. Se
 *   ignora; NO se descarta la fila, porque eso le pondría un motivo de
 *   descarte a un pedido que se sincronizó bien.
 * - ValidacionException (comercio de baja, sin stock): resultado de
 *   negocio esperable, no una falla del transporte. Se loguea y se
 *   descarta la fila con IGestionPedidos.descartarPedidoExterno(...),
 *   igual que hace SincronizadorDePedidos, y el mensaje se consume igual:
 *   reintentarlo no lo va a arreglar solo.
 * - Mensaje ilegible (no es TextMessage, JSON roto, sin idPedidoExterno):
 *   se loguea y se devuelve al broker. Reintentarlo no lo arregla, pero
 *   justamente por eso, después de los intentos del broker
 *   (max-delivery-attempts, docker/wildfly/configurar.cli) pasa a la DLQ: queda
 *   guardado para inspeccionarlo y reprocesarlo, en vez de perderse.
 * - Conflicto de stock (otra sesión tocó el mismo ítem a la vez): también
 *   se devuelve al broker, que lo reintenta a los 2 segundos.
 * - Cualquier otra RuntimeException (por ejemplo la base caída un
 *   instante) SÍ se relanza para que el broker reintente la entrega, y
 *   aunque esa redelivery fallara para siempre, SincronizadorDePedidos
 *   igual va a encontrar la fila sin sincronizar en su próxima pasada.
 *
 * CÓMO SE DEVUELVE UN MENSAJE AL BROKER
 * -------------------------------------
 * Los dos casos esperados (conflicto de stock y mensaje ilegible) no lanzan
 * una excepción: marcan la transacción del mensaje para rollback con
 * MessageDrivenContext.setRollbackOnly(). Para el broker es lo mismo (no se
 * confirma la entrega, cuenta un intento, reintenta y al quinto lo manda a
 * la DLQ), pero el adaptador de JMS no escribe un ERROR con el stack trace
 * entero por cada intento. Bajo carga, varios ERP dando de alta pedidos del
 * mismo ítem chocan seguido: eran decenas de trazas por minuto para algo
 * que se resuelve solo en el reintento.
 * Las fallas inesperadas (cualquier otra RuntimeException) sí se relanzan,
 * con su traza: ahí hace falta para diagnosticar.
 */

import com.rabbit.infraestructura.MensajeIlegibleException;
import com.rabbit.infraestructura.Trazas;

import jakarta.annotation.Resource;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.MessageDrivenContext;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/PedidosExternos"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue")
})
public class PedidoExternoListener implements MessageListener {

    private static final Logger LOG = Logger.getLogger(PedidoExternoListener.class.getName());

    // Instance<IReservaStock> no hace falta acá: este bean no reserva
    // stock directo, delega todo a PedidoService (mismo Facade que ya
    // resuelve ese detalle internamente).
    @Inject
    private IGestionPedidos gestionPedidos;

    // La transacción del mensaje: ver "Cómo se devuelve un mensaje al broker".
    @Resource
    private MessageDrivenContext contexto;

    @Override
    public void onMessage(Message mensaje) {
        Long idPedidoExterno;
        try {
            idPedidoExterno = leerIdPedidoExterno(mensaje);
        } catch (MensajeIlegibleException e) {
            // Sin traza: lo que importa es que llegó roto, no por dónde pasó.
            LOG.severe("[Pedidos][JMS] " + e.getMessage() + ": vuelve al broker y, agotados los intentos, "
                    + "queda en la DLQ (scripts/dlq.sh). " + Trazas.resumen(e.getCause()));
            contexto.setRollbackOnly();
            return;
        }
        try {
            Long idPedido = gestionPedidos.sincronizarPedidoExterno(idPedidoExterno);
            LOG.info("[Pedidos][JMS] Pedido externo " + idPedidoExterno
                    + " sincronizado en tiempo real -> pedido " + idPedido);
        } catch (PedidoYaSincronizadoException e) {
            // Ver comentario de clase: ya procesado por otro camino.
            LOG.fine("[Pedidos][JMS] Pedido externo " + idPedidoExterno + " ya estaba sincronizado");
        } catch (ConflictoDeStockException e) {
            // Choque pasajero con otra sesión sobre el mismo item: se
            // devuelve al broker para que reintente la entrega.
            LOG.warning("[Pedidos][JMS] Conflicto de stock con el pedido externo "
                    + idPedidoExterno + ", el broker va a reintentar la entrega: " + e.getMessage());
            contexto.setRollbackOnly();
        } catch (ValidacionException e) {
            // Ver comentario de clase: falla de negocio, no de transporte.
            LOG.warning("[Pedidos][JMS] Pedido externo " + idPedidoExterno
                    + " descartado por regla de negocio: " + e.getMessage());
            gestionPedidos.descartarPedidoExterno(idPedidoExterno, e.getMessage());
        } catch (RuntimeException e) {
            // Falla potencialmente transitoria (ver comentario de clase):
            // se relanza para que el contenedor reintente la entrega.
            LOG.log(Level.WARNING,
                    "[Pedidos][JMS] Error transitorio sincronizando el pedido externo "
                            + idPedidoExterno + ", el contenedor va a reintentar la entrega.",
                    e);
            throw e;
        }
    }

    /**
     * Extrae el ID del body JSON. Si el mensaje no se puede interpretar lanza
     * MensajeIlegibleException: no hay ID confiable para reintentar ni para
     * descartar contra la base, así que la entrega se deshace y, agotados los
     * reintentos del broker, el mensaje va a la DLQ (no se pierde).
     */
    private Long leerIdPedidoExterno(Message mensaje) {
        try {
            if (!(mensaje instanceof TextMessage texto)) {
                throw new MensajeIlegibleException("Mensaje de tipo inesperado en cola.pedidos.externos: "
                        + mensaje.getClass().getName());
            }
            try (StringReader reader = new StringReader(texto.getText());
                    JsonReader lector = Json.createReader(reader)) {
                JsonObject json = lector.readObject();
                return json.getJsonNumber("idPedidoExterno").longValue();
            }
        } catch (JMSException | JsonException | ClassCastException | NullPointerException e) {
            throw new MensajeIlegibleException("Mensaje ilegible en cola.pedidos.externos", e);
        }
    }
}
