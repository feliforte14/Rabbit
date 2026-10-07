package com.rabbit.pedidos.negocio;

/**
 * El ERP pidió cancelar un pedido que ya avanzó demasiado (confirmado, en
 * camino o entregado). No son datos inválidos sino un conflicto con el
 * estado actual: la API REST responde 409.
 */
public class CancelacionNoPermitidaException extends ValidacionException {
    public CancelacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
