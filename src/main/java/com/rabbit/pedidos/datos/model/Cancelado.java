package com.rabbit.pedidos.datos.model;

/** Cancelado por el personal o por el ERP antes de salir a reparto. Estado final. */
public final class Cancelado implements EstadoDelPedido {

    public static final Cancelado INSTANCIA = new Cancelado();

    private Cancelado() {
    }

    @Override
    public EstadoPedido codigo() {
        return EstadoPedido.CANCELADO;
    }

    @Override
    public String motivoParaNoCancelar(Long idPedido, boolean salioAReparto) {
        return "El pedido " + idPedido + " ya está cancelado.";
    }
}
