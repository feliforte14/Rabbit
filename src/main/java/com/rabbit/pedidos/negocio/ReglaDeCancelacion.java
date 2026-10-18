package com.rabbit.pedidos.negocio;

/**
 * Cuándo se puede cancelar un pedido (18/10/2026, pedido del equipo).
 *
 * Una vez que el pedido salió a reparto, Rabbit ya no lo cancela: solo
 * queda entregarlo. Si no se puede, el repartidor registra la entrega
 * fallida ("Devolver a depósito") y Rabbit lo vuelve a intentar; al agotar
 * las visitas, con un rechazo o con el paquete dañado, el pedido termina
 * DEVUELTO_AL_COMERCIO (ADR-041) y la devolución con el cliente la
 * gestiona el comercio, no Rabbit.
 *
 * "Salió a reparto" no es solo el estado EN_CAMINO: un pedido que salió,
 * falló la visita y volvió al depósito para otra (PENDIENTE en espera)
 * tampoco se cancela. Un pedido que se devolvió sin haber salido (por
 * ejemplo, no entraba en el vehículo, desde CONFIRMADO) sí.
 *
 * Vale para el personal (web) y para el ERP (API). Sin estado ni
 * contenedor: se prueba con tests unitarios.
 */

import com.rabbit.pedidos.datos.model.EstadoPedido;

public final class ReglaDeCancelacion {

    private ReglaDeCancelacion() {
    }

    /**
     * Por qué no se puede cancelar, o null si se puede.
     *
     * @param salioAReparto si alguna vez estuvo EN_CAMINO
     */
    public static String motivoParaNoCancelar(Long idPedido, EstadoPedido estado, boolean salioAReparto) {
        String pedido = "El pedido " + idPedido;
        switch (estado) {
            case EN_CAMINO:
                return pedido + " ya salió a reparto y no se puede cancelar: solo queda entregarlo. Si no se"
                        + " puede entregar, vuelve al comercio, que gestiona la devolución con su cliente.";
            case PENDIENTE:
            case CONFIRMADO:
                return salioAReparto
                        ? pedido + " ya salió a reparto (volvió al depósito para otra visita) y no se puede"
                                + " cancelar. Si tampoco se puede entregar, vuelve al comercio, que gestiona la"
                                + " devolución con su cliente."
                        : null;
            case ENTREGADO:
                return pedido + " ya fue entregado: no se puede cancelar.";
            case DEVUELTO_AL_COMERCIO:
                return pedido + " ya volvió al comercio: no se puede cancelar.";
            case CANCELADO:
                return pedido + " ya está cancelado.";
            default:
                return null;
        }
    }
}
