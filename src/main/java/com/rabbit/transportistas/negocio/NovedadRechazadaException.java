package com.rabbit.transportistas.negocio;

/**
 * Una novedad de un envío que no se pudo aplicar porque el pedido o el
 * envío cambiaron al mismo tiempo (por ejemplo, el personal lo canceló):
 * se deshace todo y el seguimiento la reintenta en la próxima pasada.
 */
public class NovedadRechazadaException extends ValidacionException {

    public NovedadRechazadaException(String mensaje) {
        super(mensaje);
    }
}
