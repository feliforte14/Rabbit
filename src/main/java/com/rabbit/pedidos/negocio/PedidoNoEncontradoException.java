package com.rabbit.pedidos.negocio;

/**
 * El pedido (o el pedido externo) pedido no existe — o existe pero es de
 * otro comercio y quien llama es un ERP: para el ERP es lo mismo, no tiene
 * por qué enterarse de que existe.
 *
 * Extiende ValidacionException para que todo lo que ya la atrapa (las
 * pantallas, los dos disparadores de la sincronización) siga igual; la API
 * REST la distingue para responder 404 en vez de 422.
 */
public class PedidoNoEncontradoException extends ValidacionException {
    public PedidoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
