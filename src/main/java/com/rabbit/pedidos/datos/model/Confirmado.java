package com.rabbit.pedidos.datos.model;

/**
 * Cobrado (si era prepago) y con repartidor asignado, o derivado a un transportista.
 *
 * Con RETIRO_EN_DEPOSITO (ADR-053) es "listo para retirar": no sale a reparto, así que solo se entrega al
 * retirarlo o se cancela. A domicilio puede salir, cancelarse o volver (al depósito para otra visita, o al
 * comercio si el paquete llegó dañado, ADR-045).
 */
public final class Confirmado implements EstadoDelPedido {

    public static final Confirmado INSTANCIA = new Confirmado();

    private Confirmado() {
    }

    @Override
    public EstadoPedido codigo() {
        return EstadoPedido.CONFIRMADO;
    }

    @Override
    public EstadoDelPedido despachar(Pedido pedido) {
        exigirADomicilio(pedido, EstadoPedido.EN_CAMINO);
        return EnCamino.INSTANCIA;
    }

    /** Solo el retiro en depósito se entrega desde acá; a domicilio hay que pasar por EN_CAMINO. */
    @Override
    public EstadoDelPedido entregar(Pedido pedido) {
        if (!pedido.isRetiroEnDeposito()) {
            return EstadoDelPedido.super.entregar(pedido);
        }
        return Entregado.INSTANCIA;
    }

    @Override
    public EstadoDelPedido cancelar(Pedido pedido) {
        return Cancelado.INSTANCIA;
    }

    @Override
    public EstadoDelPedido devolverADeposito(Pedido pedido) {
        exigirADomicilio(pedido, EstadoPedido.PENDIENTE);
        return Pendiente.INSTANCIA;
    }

    @Override
    public EstadoDelPedido devolverAlComercio(Pedido pedido) {
        exigirADomicilio(pedido, EstadoPedido.DEVUELTO_AL_COMERCIO);
        return DevueltoAlComercio.INSTANCIA;
    }

    private void exigirADomicilio(Pedido pedido, EstadoPedido destino) {
        if (pedido.isRetiroEnDeposito()) {
            throw new TransicionInvalidaException(codigo(), destino);
        }
    }
}
