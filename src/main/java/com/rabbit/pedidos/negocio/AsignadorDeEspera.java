package com.rabbit.pedidos.negocio;

/**
 * La lista de espera de pedidos (ADR-037): asigna solos, por orden de
 * llegada, los pedidos PENDIENTE que se quisieron confirmar cuando ningún
 * repartidor tenía lugar (o después de la hora de corte).
 *
 * CUÁNDO CORRE
 *   - Enseguida que algo puede haber liberado lugar: un pedido entregado,
 *     cancelado o devuelto al depósito. Lo avisa AvisosDeEspera (ver ahí
 *     por qué es otro bean).
 *   - Cada minuto, de respaldo: cubre lo que no pasa por un cambio de
 *     estado (se dio de alta un repartidor, se le subió la capacidad, se
 *     movió la hora de corte, empezó el día).
 *
 * Fuera de la ventana del día (después de la hora de corte) no hace nada:
 * la fila se procesa al día siguiente, antes del corte.
 *
 * Cada pedido se asigna en su propia transacción (REQUIRES_NEW en
 * PedidoService.asignarDesdeEspera): uno que falla no frena a los demás.
 * Un pedido grande que no entra no tapa la fila: se prueba con los
 * siguientes, que quizás sí entran.
 *
 * @RunAs("OPERADOR"): confirmar un pedido es una operación del personal y
 * el timer no tiene usuario (mismo criterio que SeguimientoDeEnvios).
 *
 * Concurrencia BEAN: un aviso no tiene que esperar a que termine una
 * pasada en curso; si llega mientras se recorre la fila, se hace otra
 * vuelta al terminar.
 */

import com.rabbit.pedidos.datos.PedidoRepository;
import com.rabbit.repartidores.negocio.IAsignacionRepartidores;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.EJBException;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@RunAs("OPERADOR")
@PermitAll
public class AsignadorDeEspera {

    private static final Logger LOG = Logger.getLogger(AsignadorDeEspera.class.getName());

    @Inject
    private PedidoRepository repository;

    @Inject
    private IGestionPedidos gestion;

    @Inject
    private IAsignacionRepartidores repartidores;

    private final AtomicBoolean corriendo = new AtomicBoolean();
    private final AtomicBoolean otraVuelta = new AtomicBoolean();

    @Schedule(hour = "*", minute = "*", second = "45", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void cadaMinuto() {
        procesar();
    }

    /** Una pasada por la fila (o, si ya hay una en curso, otra vuelta al terminar). */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void procesar() {
        if (!corriendo.compareAndSet(false, true)) {
            otraVuelta.set(true);
            return;
        }
        try {
            do {
                otraVuelta.set(false);
                recorrerFila();
            } while (otraVuelta.get());
        } finally {
            corriendo.set(false);
        }
    }

    private void recorrerFila() {
        if (!repartidores.antesDelCorte()) {
            return;
        }
        List<Long> enEspera = repository.listarIdsEnEspera();
        for (Long idPedido : enEspera) {
            try {
                Long idRepartidor = gestion.asignarDesdeEspera(idPedido);
                if (idRepartidor != null) {
                    LOG.info("[Espera] Pedido " + idPedido + " asignado al repartidor " + idRepartidor);
                }
            } catch (SinRepartidorConLugarException | ConflictoDeStockException e) {
                // Otro ganó el lugar justo ahora: sigue en la fila.
            } catch (ValidacionException e) {
                // No se arregla esperando (por ejemplo, el banco rechazó el
                // pago): sale de la fila con el motivo, para el personal.
                sacar(idPedido, e.getMessage());
            } catch (EJBException e) {
                LOG.warning("[Espera] No se pudo asignar el pedido " + idPedido + ", se reintenta: " + e.getMessage());
            }
        }
    }

    private void sacar(Long idPedido, String motivo) {
        try {
            gestion.sacarDeEspera(idPedido, motivo);
        } catch (RuntimeException e) {
            LOG.warning("[Espera] No se pudo sacar de la fila el pedido " + idPedido + ": " + e.getMessage());
        }
    }
}
