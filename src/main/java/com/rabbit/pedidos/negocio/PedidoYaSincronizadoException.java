package com.rabbit.pedidos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * El pedido externo ya fue procesado (sincronizado o descartado) por otro
 * camino. No es una falla: con dos disparadores (PedidoExternoListener por
 * JMS y SincronizadorDePedidos por polling) y la redelivery de JMS, que un
 * mismo pedido llegue dos veces es esperable.
 *
 * NO extiende ValidacionException a propósito: los dos disparadores tratan
 * ValidacionException descartando la fila (errorSincronizacion = motivo), y
 * hacer eso sobre un pedido que se sincronizó bien le pondría un motivo de
 * descarte falso. Siendo otra clase, cada disparador la ignora explícitamente.
 */
@ApplicationException(rollback = true)
public class PedidoYaSincronizadoException extends RuntimeException {
    public PedidoYaSincronizadoException(Long idPedidoExterno) {
        super("El pedido externo " + idPedidoExterno + " ya fue sincronizado");
    }
}
