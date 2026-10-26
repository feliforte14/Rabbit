package com.rabbit.pedidos.datos.model;

/** No se pudo entregar (intentos agotados, rechazo o paquete dañado) y volvió al comercio (ADR-041, ADR-045). Estado final. */
public final class DevueltoAlComercio implements EstadoDelPedido {

    public static final DevueltoAlComercio INSTANCIA = new DevueltoAlComercio();

    private DevueltoAlComercio() {
    }

    @Override
    public EstadoPedido codigo() {
        return EstadoPedido.DEVUELTO_AL_COMERCIO;
    }
}
