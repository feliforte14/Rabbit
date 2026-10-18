package com.rabbit.pedidos.negocio;

/**
 * Cuántas veces se intenta entregar un pedido antes de devolverlo al
 * comercio (ADR-041). Antes, "Devolver a depósito" lo mandaba a PENDIENTE
 * sin límite: un cliente que nunca estaba hacía dar vueltas al pedido para
 * siempre.
 *
 * Una visita cuenta como intento si el pedido estaba EN_CAMINO (el
 * repartidor ya lo había retirado) y el motivo es de los que implican ir a
 * la dirección (ver MotivoEntregaFallida). Al llegar al máximo, o si el
 * cliente lo rechazó, el pedido pasa a DEVUELTO_AL_COMERCIO. Un paquete
 * dañado vuelve al comercio aunque no haya salido (ADR-045): no se
 * reintenta la entrega de algo roto.
 *
 * El máximo por defecto son 2 visitas (como Andreani antes de mandarlo a
 * sucursal). Se cambia con una system property de WildFly, leída en cada
 * uso, sin redesplegar:
 *   rabbit.entregas.max-intentos   de 1 a 10
 * Un valor inválido se ignora y rige el de por defecto.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import java.util.logging.Logger;

public final class IntentosDeEntrega {

    public static final String PROPIEDAD = "rabbit.entregas.max-intentos";
    public static final int POR_DEFECTO = 2;

    private static final Logger LOG = Logger.getLogger(IntentosDeEntrega.class.getName());

    private IntentosDeEntrega() {
    }

    /** Lo que pasa con el pedido después de una entrega fallida. */
    record Desenlace(int intentos, boolean devolverAlComercio) {
    }

    /** El máximo configurado. */
    public static int maximo() {
        return interpretar(System.getProperty(PROPIEDAD));
    }

    static int interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return POR_DEFECTO;
        }
        try {
            int maximo = Integer.parseInt(valor.trim());
            if (maximo >= 1 && maximo <= 10) {
                return maximo;
            }
        } catch (NumberFormatException e) {
            // Cae al aviso de abajo.
        }
        LOG.warning("[Pedidos] " + PROPIEDAD + "=" + valor + " no es un número de 1 a 10: se usa " + POR_DEFECTO);
        return POR_DEFECTO;
    }

    /**
     * @param estabaEnCamino si el repartidor ya lo había retirado
     * @param motivo por qué no se entregó
     * @param intentosPrevios visitas fallidas anteriores
     * @param maximo visitas antes de devolverlo al comercio
     */
    static Desenlace evaluar(boolean estabaEnCamino, MotivoEntregaFallida motivo, int intentosPrevios, int maximo) {
        boolean visita = estabaEnCamino && motivo.isCuentaComoVisita();
        int intentos = intentosPrevios + (visita ? 1 : 0);
        // Un rechazo necesita haber ido; un paquete dañado (no es visita) no.
        boolean sinSentidoReintentar = motivo.isDevuelveAlComercio() && (visita || !motivo.isCuentaComoVisita());
        boolean devolver = sinSentidoReintentar || (visita && intentos >= maximo);
        return new Desenlace(intentos, devolver);
    }
}
