package com.rabbit.transportistas.negocio;

/**
 * Un aviso al webhook de novedades que Rabbit no acepta. El motivo define
 * qué responde la API: clave inválida (401), envío desconocido para ese
 * transportista (404) o estado que no existe (422).
 */
public class NovedadRechazadaException extends ValidacionException {

    public enum Motivo { CLAVE_INVALIDA, ENVIO_DESCONOCIDO, ESTADO_DESCONOCIDO }

    private final Motivo motivo;

    public NovedadRechazadaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
