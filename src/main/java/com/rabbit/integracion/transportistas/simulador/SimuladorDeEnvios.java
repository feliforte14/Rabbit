package com.rabbit.integracion.transportistas.simulador;

/**
 * TRANSPORTISTAS SIMULADOS: la lógica que comparten los dos (el REST y el
 * legado SOAP). En producción cada transportista sería un sistema de otra
 * empresa; acá viven en el mismo WAR, como el banco simulado, pero Rabbit
 * les habla siempre por HTTP a través de sus adaptadores.
 *
 * Reglas determinísticas para la demo:
 *   - Un envío de más de 50 bultos se rechaza (excede la capacidad).
 *   - Un envío aceptado avanza solo con el tiempo: SOLICITADO durante el
 *     primer paso, EN_TRANSITO durante el segundo, y después ENTREGADO.
 *     El paso dura 20 s (system property
 *     rabbit.transportista.simulador.segundos).
 *   - Se puede cancelar mientras no esté entregado.
 * Los envíos viven en memoria: se pierden al redesplegar.
 */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

final class SimuladorDeEnvios {

    private static final Logger LOG = Logger.getLogger(SimuladorDeEnvios.class.getName());

    static final int MAX_BULTOS = 50;

    enum Estado { SOLICITADO, EN_TRANSITO, ENTREGADO, CANCELADO }

    private record Envio(String referencia, long creado, boolean cancelado) {}

    private final String nombre;
    private final String prefijo;
    private final AtomicLong secuencia = new AtomicLong();
    private final Map<String, Envio> envios = new ConcurrentHashMap<>();

    SimuladorDeEnvios(String nombre, String prefijo) {
        this.nombre = nombre;
        // Los envíos viven en memoria y la numeración vuelve a empezar en cada
        // despliegue: con un sello del arranque, un código nuevo nunca repite
        // el de un envío anterior que Rabbit todavía tenga guardado.
        this.prefijo = prefijo + Long.toString(System.currentTimeMillis() / 1000, 36).toUpperCase() + "-";
    }

    /** @return el motivo del rechazo, o null si el envío se puede tomar */
    String motivoDeRechazo(int bultos) {
        if (bultos < 1) {
            return "El envío no tiene bultos";
        }
        if (bultos > MAX_BULTOS) {
            return "Excede la capacidad del vehículo (" + MAX_BULTOS + " bultos)";
        }
        return null;
    }

    String registrar(String referencia, String entrega) {
        String codigo = prefijo + secuencia.incrementAndGet();
        envios.put(codigo, new Envio(referencia, System.currentTimeMillis(), false));
        LOG.info("[" + nombre + "] Envío " + codigo + " tomado: " + referencia + " -> " + entrega);
        return codigo;
    }

    /** @return el estado actual, o null si el código no existe */
    Estado estado(String codigo) {
        Envio envio = codigo != null ? envios.get(codigo) : null;
        if (envio == null) {
            return null;
        }
        if (envio.cancelado()) {
            return Estado.CANCELADO;
        }
        long pasos = (System.currentTimeMillis() - envio.creado()) / (segundosPorPaso() * 1000L);
        return pasos == 0 ? Estado.SOLICITADO : pasos == 1 ? Estado.EN_TRANSITO : Estado.ENTREGADO;
    }

    /** @return false si el código no existe */
    boolean cancelar(String codigo) {
        Estado estado = estado(codigo);
        if (estado == null) {
            return false;
        }
        if (estado != Estado.ENTREGADO && estado != Estado.CANCELADO) {
            Envio envio = envios.get(codigo);
            envios.put(codigo, new Envio(envio.referencia(), envio.creado(), true));
            LOG.info("[" + nombre + "] Envío " + codigo + " cancelado (" + envio.referencia() + ")");
        }
        return true;
    }

    private static long segundosPorPaso() {
        return Math.max(1, Long.getLong("rabbit.transportista.simulador.segundos", 20L));
    }
}
