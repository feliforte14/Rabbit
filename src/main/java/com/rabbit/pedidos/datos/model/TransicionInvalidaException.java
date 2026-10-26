package com.rabbit.pedidos.datos.model;

/**
 * Un estado del pedido no permite la operación pedida (por ejemplo, cancelar un pedido EN_CAMINO).
 *
 * Es del modelo y no de una regla de negocio: la lanzan los estados (ver {@link EstadoDelPedido}) y
 * PedidoService la traduce a TransicionNoPermitidaException, que es la que el contenedor y la API conocen.
 * No guarda la traza de la pila: también se usa para "probar" qué transiciones permite un estado
 * ({@link EstadoDelPedido#puedePasarA}) y no tiene sentido pagar la traza por cada intento.
 */
public class TransicionInvalidaException extends RuntimeException {

    private final EstadoPedido estado;
    private final EstadoPedido destino;

    /**
     * @param estado estado en el que está el pedido
     * @param destino estado al que habría llegado la operación pedida
     */
    public TransicionInvalidaException(EstadoPedido estado, EstadoPedido destino) {
        super("Un pedido " + estado + " no puede pasar a " + destino, null, false, false);
        this.estado = estado;
        this.destino = destino;
    }

    public EstadoPedido getEstado() { return estado; }

    public EstadoPedido getDestino() { return destino; }
}
