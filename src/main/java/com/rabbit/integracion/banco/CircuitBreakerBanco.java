package com.rabbit.integracion.banco;

/**
 * CIRCUIT BREAKER de la integración con el banco legado (EJB @Singleton).
 *
 * EL PROBLEMA QUE RESUELVE
 * Si el banco está colgado, cada confirmación de un pedido PREPAGO espera
 * el timeout completo de BancoClient (5 s) para terminar igual: sin
 * confirmar. Con muchos usuarios confirmando a la vez, esos hilos
 * bloqueados esperando al banco agotan el pool de WildFly y la caída del
 * banco termina tirando al resto de Rabbit (falla en cascada).
 *
 * CÓMO FUNCIONA
 *   - CERRADO: las llamadas pasan. Cuenta las fallas SEGUIDAS (timeout o
 *     banco caído); al llegar a UMBRAL_FALLAS se abre.
 *   - ABIERTO: no se llama al banco. BancoClient contesta NO_DISPONIBLE al
 *     instante, sin esperar el timeout. Pasado ESPERA_MS pasa a SEMIABIERTO.
 *   - SEMIABIERTO: deja pasar UNA llamada de prueba (las demás se siguen
 *     cortando). Si el banco responde, se cierra; si falla, se vuelve a
 *     abrir por otros ESPERA_MS.
 *
 * Un rechazo de negocio (soap:Fault PagoRechazado) NO es una falla: el
 * banco respondió. Solo cuenta que el banco no conteste.
 *
 * POR QUÉ @Singleton
 * El estado del circuito tiene que ser uno solo y compartido por todas
 * las llamadas: si cada instancia del pool de BancoClient (stateless)
 * llevara su propia cuenta, ninguna llegaría al umbral. La concurrencia
 * gestionada por el contenedor (LockType.WRITE) serializa las
 * transiciones; los métodos solo tocan memoria, así que el lock dura
 * microsegundos y nunca se retiene durante la llamada al banco.
 *
 * Alcance: el estado vive en memoria de ESTE servidor. En un cluster cada
 * nodo tendría su propio circuito, lo cual es aceptable (cada nodo
 * descubre la caída por su cuenta tras UMBRAL_FALLAS intentos).
 *
 * Se descartó MicroProfile Fault Tolerance (@CircuitBreaker): no viene en
 * la configuración standalone-full de WildFly que usa Rabbit, y la lógica
 * es lo bastante chica para escribirla y explicarla completa (ADR-011).
 */

import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import java.util.logging.Logger;

@Singleton
@Lock(LockType.WRITE)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CircuitBreakerBanco {

    private static final Logger LOG = Logger.getLogger(CircuitBreakerBanco.class.getName());

    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    // Configurables con system properties, como rabbit.banco.wsdl.
    private static final int UMBRAL_FALLAS = Integer.getInteger("rabbit.banco.cb.umbral", 3);
    private static final long ESPERA_MS = Long.getLong("rabbit.banco.cb.espera.ms", 30_000L);

    private Estado estado = Estado.CERRADO;
    private int fallasSeguidas;
    // Cuándo se abrió el circuito o cuándo salió la llamada de prueba.
    private long desde;

    /**
     * @return true si se puede llamar al banco; false si el circuito está
     *         abierto y hay que contestar NO_DISPONIBLE sin llamarlo
     */
    public boolean permitirLlamada() {
        switch (estado) {
            case CERRADO:
                return true;
            case ABIERTO:
                if (System.currentTimeMillis() - desde < ESPERA_MS) {
                    return false;
                }
                LOG.info("[Pagos][Circuito] Pasaron " + ESPERA_MS + " ms: SEMIABIERTO, se prueba una llamada al banco");
                estado = Estado.SEMIABIERTO;
                desde = System.currentTimeMillis();
                return true;
            default:
                // SEMIABIERTO: ya hay una prueba en curso. Si su resultado
                // nunca se registró (el hilo murió), se permite otra.
                if (System.currentTimeMillis() - desde < ESPERA_MS) {
                    return false;
                }
                desde = System.currentTimeMillis();
                return true;
        }
    }

    /** El banco respondió (aprobó, rechazó o confirmó una reversa). */
    public void registrarExito() {
        if (estado != Estado.CERRADO) {
            LOG.info("[Pagos][Circuito] El banco volvió a responder: CERRADO");
        }
        estado = Estado.CERRADO;
        fallasSeguidas = 0;
    }

    /** El banco no respondió (timeout, conexión rechazada, error del servidor). */
    public void registrarFalla() {
        fallasSeguidas++;
        if (estado == Estado.SEMIABIERTO || fallasSeguidas >= UMBRAL_FALLAS) {
            if (estado != Estado.ABIERTO) {
                LOG.warning("[Pagos][Circuito] " + fallasSeguidas + " fallas seguidas del banco: ABIERTO por "
                        + ESPERA_MS + " ms, las llamadas se cortan sin esperar el timeout");
            }
            estado = Estado.ABIERTO;
            desde = System.currentTimeMillis();
        }
    }

    @Lock(LockType.READ)
    public Estado getEstado() {
        return estado;
    }
}
