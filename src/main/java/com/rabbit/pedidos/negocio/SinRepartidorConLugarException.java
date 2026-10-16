package com.rabbit.pedidos.negocio;

/**
 * La asignación falló porque, justo en ese momento, otro pedido se quedó
 * con el último lugar (la consulta previa decía que había). No es un error
 * del pedido: en la lista de espera, AsignadorDeEspera lo vuelve a
 * intentar en la próxima pasada.
 */
public class SinRepartidorConLugarException extends ValidacionException {
    public SinRepartidorConLugarException(String mensaje) {
        super(mensaje);
    }
}
