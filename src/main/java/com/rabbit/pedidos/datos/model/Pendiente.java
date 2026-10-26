package com.rabbit.pedidos.datos.model;

/** Recién llegado del ERP, o devuelto al depósito para otra visita (lista de espera). Se confirma o se cancela. */
public final class Pendiente implements EstadoDelPedido {

    public static final Pendiente INSTANCIA = new Pendiente();

    private Pendiente() {
    }

    @Override
    public EstadoPedido codigo() {
        return EstadoPedido.PENDIENTE;
    }

    @Override
    public EstadoDelPedido confirmar(Pedido pedido) {
        return Confirmado.INSTANCIA;
    }

    @Override
    public EstadoDelPedido cancelar(Pedido pedido) {
        return Cancelado.INSTANCIA;
    }

    /** No limpia la espera: quien manda un pedido a la lista de espera carga el motivo y la zona. */
    @Override
    public void alEntrar(Pedido pedido) {
    }
}
