package com.rabbit.transportistas.negocio;

/**
 * Un aviso al webhook de novedades que Rabbit no acepta. El motivo define
 * qué responde la API: clave inválida (401), envío desconocido para ese
 * transportista (404), estado que no existe (422) o pedido que no se pudo
 * mover con esa novedad porque cambió al mismo tiempo (409: reintentar).
 */
public class NovedadRechazadaException extends ValidacionException {

    public enum Motivo { CLAVE_INVALIDA, ENVIO_DESCONOCIDO, ESTADO_DESCONOCIDO, PEDIDO_EN_CONFLICTO }

    private final Motivo motivo;

    public NovedadRechazadaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
