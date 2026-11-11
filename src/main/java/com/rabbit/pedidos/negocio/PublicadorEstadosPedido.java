package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — integración asincrónica del componente Pedidos
 * (EJB @Stateless, productor JMS publicación/suscripción)
 *
 * QUÉ RESUELVE
 * Cada cambio de estado de un pedido le interesa a más de un componente,
 * cada uno por su motivo: Pagos (con ENTREGADO y CONTRA_ENTREGA efectiviza
 * el cobro; con DEVUELTO_AL_COMERCIO lo anula) y Notificaciones (avisa al
 * comercio y, con otro suscriptor, a quien recibe: ADR-040). Esta clase publica cada
 * EstadoPedidoCambiado en "topico.pedidos.estado"; Pedidos no sabe quién
 * lo escucha, así que sumar un suscriptor no toca este componente.
 *
 * POR QUÉ TOPIC Y NO QUEUE
 * Es 1 → N: el MISMO evento lo tienen que recibir los tres suscriptores.
 * Con una cola, cada mensaje lo consumiría uno solo de ellos. Es el caso
 * opuesto a cola.pedidos.externos (PublicadorPedidosExternos), donde cada
 * pedido lo tiene que procesar exactamente un consumidor.
 *
 * TRANSACCIÓN Y BANDEJA DE SALIDA (outbox): el aviso se guarda en la tabla
 * eventos_estado_pedido DENTRO de la transacción del cambio de estado (si
 * ésta se deshace, el aviso también) y recién después del commit se manda
 * (AFTER_SUCCESS). Si el broker está caído, el aviso queda pendiente y
 * EnvioDeEstadosPendientes lo reenvía cada 15 segundos: no se pierde.
 * Antes solo se publicaba después del commit y, con el broker caído, el
 * aviso se perdía (y con él el cobro contra entrega y la notificación).
 *
 * MENSAJE: TextMessage con el JSON {idPedido, idComercio, estado,
 * fechaCambio} y dos propiedades JMS (estado, idPedido): los suscriptores
 * pueden filtrar con un messageSelector sin leer el body — Pagos, por
 * ejemplo, solo se suscribe a estado IN ('ENTREGADO', 'DEVUELTO_AL_COMERCIO').
 */

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.json.Json;

// El tópico lo declara la aplicación, como la cola; la connection factory
// es la misma (declarada en PublicadorPedidosExternos, conector in-vm).
@JMSDestinationDefinition(
        name = "java:/jms/topic/EstadosPedido",
        interfaceName = "jakarta.jms.Topic",
        destinationName = "topico.pedidos.estado"
)
@Stateless
public class PublicadorEstadosPedido {

    // El registro corre antes que el envío si no hay transacción (ahí los
    // dos observadores se llaman en el momento): primero se guarda, después se manda.
    static final int PRIORIDAD_REGISTRO = 100;
    static final int PRIORIDAD_ENVIO = 200;

    @Inject
    private BandejaDeEstados bandeja;

    @Inject
    private EnvioDeEstadosPendientes envio;

    /** Guarda el aviso en la bandeja, en la misma transacción que el cambio de estado. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void registrarCambioDeEstado(@Observes @Priority(PRIORIDAD_REGISTRO) EstadoPedidoCambiado evento) {
        bandeja.registrar(evento);
    }

    /** Ya confirmado el cambio de estado: manda lo pendiente (este aviso y, si los hay, los que quedaron atrás). */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void publicarCambioDeEstado(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) @Priority(PRIORIDAD_ENVIO) EstadoPedidoCambiado evento) {
        envio.enviarPendientes();
    }

    /**
     * Cuerpo del mensaje del tópico. SEC-08: solo lo mínimo para que cada
     * suscriptor sepa qué pedido cambió y a qué estado (IDs, estado y fecha);
     * ningún dato personal ni importe. Separado del envío para probarlo sin broker.
     */
    static String construirPayload(EstadoPedidoCambiado evento) {
        return Json.createObjectBuilder()
                .add("idPedido", evento.idPedido())
                .add("idComercio", evento.idComercio())
                .add("estado", evento.estado().name())
                .add("fechaCambio", evento.fechaCambio().toString())
                .build()
                .toString();
    }
}
