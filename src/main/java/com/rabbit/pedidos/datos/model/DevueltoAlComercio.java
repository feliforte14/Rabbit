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

    @Override
    public String motivoParaNoCancelar(Long idPedido, boolean salioAReparto) {
        return "El pedido " + idPedido + " ya volvió al comercio: no se puede cancelar.";
    }

    @Override
    public String queLeQuedaAlErp() {
        return "No se pudo entregar y ya volvió al comercio.";
    }
}
