package com.rabbit.repartidores.negocio;

/**
 * No se puede asignar el pedido ahora: ningún repartidor tiene lugar, no
 * hay repartidores o ya pasó la hora de corte. A diferencia de los demás
 * errores, se arregla solo con el tiempo: Pedidos lo usa para mandar el
 * pedido a la lista de espera en vez de rechazarlo.
 *
 * Hereda @ApplicationException(rollback = true) de ValidacionException.
 */
public class SinLugarException extends ValidacionException {
    public SinLugarException(String mensaje) {
        super(mensaje);
    }
}
