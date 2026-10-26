package com.rabbit.pedidos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * El estado actual del pedido no permite la operación: por ejemplo, entregar un pedido que se
 * canceló mientras el clic estaba en camino. No son datos inválidos sino un conflicto con el estado, así que
 * la API REST responde 409. Sigue siendo una ValidacionException: quien ya la captura no cambia.
 */
@ApplicationException(rollback = true)
public class TransicionNoPermitidaException extends ValidacionException {
    public TransicionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
