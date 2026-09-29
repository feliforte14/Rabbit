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
 * El lock evita que dos hilos modifiquen el estado a la vez, pero no que
 * el resultado de una llamada vieja se aplique sobre un estado nuevo: para
 * eso cada llamada lleva un ticket con la generación en la que empezó
 * (ver intentarLlamada).
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

    /** Lo que devuelve {@link #intentarLlamada()} cuando no se puede llamar al banco. */
    public static final long SIN_PERMISO = -1;

    private Estado estado = Estado.CERRADO;
    private int fallasSeguidas;
    // Cuándo se abrió el circuito o cuándo salió la llamada de prueba.
    private long desde;
    // Sube con cada cambio de estado. Cada llamada lleva la generación en
    // la que empezó (su "ticket"); el resultado de una llamada que empezó
    // en una generación anterior se ignora. Sin esto, una llamada lenta
    // lanzada con el circuito CERRADO que termina con éxito mientras está
    // SEMIABIERTO lo cerraría antes de que responda la llamada de prueba,
    // y una falla vieja podría volver a abrirlo.
    private long generacion;

    /**
     * @return el ticket de la llamada (a pasar a registrarExito o
     *         registrarFalla), o {@link #SIN_PERMISO} si el circuito está
     *         abierto y hay que contestar NO_DISPONIBLE sin llamar al banco
     */
    public long intentarLlamada() {
        switch (estado) {
            case CERRADO:
                return generacion;
            case ABIERTO:
                if (System.currentTimeMillis() - desde < ESPERA_MS) {
                    return SIN_PERMISO;
                }
                LOG.info("[Pagos][Circuito] Pasaron " + ESPERA_MS + " ms: SEMIABIERTO, se prueba una llamada al banco");
                cambiarA(Estado.SEMIABIERTO);
                return generacion;
            default:
                // SEMIABIERTO: ya hay una prueba en curso. Si su resultado
                // nunca se registró (el hilo murió), se permite otra y la
                // anterior queda invalidada.
                if (System.currentTimeMillis() - desde < ESPERA_MS) {
                    return SIN_PERMISO;
                }
                cambiarA(Estado.SEMIABIERTO);
                return generacion;
        }
    }

    /** El banco respondió (aprobó, rechazó o confirmó una reversa). */
    public void registrarExito(long ticket) {
        if (ticket != generacion) {
            return;
        }
        if (estado != Estado.CERRADO) {
            LOG.info("[Pagos][Circuito] El banco volvió a responder: CERRADO");
            cambiarA(Estado.CERRADO);
        }
        fallasSeguidas = 0;
    }

    /** El banco no respondió (timeout, conexión rechazada, error del servidor). */
    public void registrarFalla(long ticket) {
        if (ticket != generacion) {
            return;
        }
        fallasSeguidas++;
        if (estado == Estado.SEMIABIERTO || fallasSeguidas >= UMBRAL_FALLAS) {
            LOG.warning("[Pagos][Circuito] " + fallasSeguidas + " fallas seguidas del banco: ABIERTO por "
                    + ESPERA_MS + " ms, las llamadas se cortan sin esperar el timeout");
            cambiarA(Estado.ABIERTO);
        }
    }

    private void cambiarA(Estado nuevo) {
        estado = nuevo;
        generacion++;
        desde = System.currentTimeMillis();
        if (nuevo == Estado.CERRADO) {
            fallasSeguidas = 0;
        }
    }

    @Lock(LockType.READ)
    public Estado getEstado() {
        return estado;
    }
}
