package com.rabbit.pedidos.datos.model;

/** Entregado al destinatario (o retirado en el depósito). Estado final. */
public final class Entregado implements EstadoDelPedido {

    public static final Entregado INSTANCIA = new Entregado();

    private Entregado() {
    }

    @Override
    public EstadoPedido codigo() {
        return EstadoPedido.ENTREGADO;
    }
}
