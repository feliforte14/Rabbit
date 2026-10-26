package com.rabbit.pedidos.datos.model;

/**
 * Ya salió con el repartidor (o el transportista lo tomó): no se cancela desde Rabbit, solo se entrega,
 * vuelve al depósito para otra visita (ADR-037, ADR-041) o vuelve al comercio.
 */
public final class EnCamino implements EstadoDelPedido {

    public static final EnCamino INSTANCIA = new EnCamino();

    private EnCamino() {
    }

    @Override
    public EstadoPedido codigo() {
        return EstadoPedido.EN_CAMINO;
    }

    @Override
    public EstadoDelPedido entregar(Pedido pedido) {
        return Entregado.INSTANCIA;
    }

    @Override
    public EstadoDelPedido devolverADeposito(Pedido pedido) {
        return Pendiente.INSTANCIA;
    }

    @Override
    public EstadoDelPedido devolverAlComercio(Pedido pedido) {
        return DevueltoAlComercio.INSTANCIA;
    }

    /** Desde acá ya no se puede cancelar (ReglaDeCancelacion): queda marcado que salió a reparto. */
    @Override
    public void alEntrar(Pedido pedido) {
        EstadoDelPedido.super.alEntrar(pedido);
        pedido.setSalioAReparto(true);
    }
}
