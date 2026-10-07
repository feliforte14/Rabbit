package com.rabbit.integracion.transportistas;

/**
 * Resultado de pedirle un envío a un transportista. Tres desenlaces, como
 * con el banco (ResultadoAutorizacion): lo tomó, lo rechazó (con motivo) o
 * no respondió.
 */
public final class ResultadoSolicitud {

    public enum Estado { ACEPTADO, RECHAZADO, NO_DISPONIBLE }

    private final Estado estado;
    private final String codigoSeguimiento;
    private final String motivo;

    private ResultadoSolicitud(Estado estado, String codigoSeguimiento, String motivo) {
        this.estado = estado;
        this.codigoSeguimiento = codigoSeguimiento;
        this.motivo = motivo;
    }

    public static ResultadoSolicitud aceptado(String codigoSeguimiento) {
        return new ResultadoSolicitud(Estado.ACEPTADO, codigoSeguimiento, null);
    }

    public static ResultadoSolicitud rechazado(String motivo) {
        return new ResultadoSolicitud(Estado.RECHAZADO, null, motivo);
    }

    public static ResultadoSolicitud noDisponible() {
        return new ResultadoSolicitud(Estado.NO_DISPONIBLE, null, null);
    }

    public Estado getEstado() { return estado; }
    public String getCodigoSeguimiento() { return codigoSeguimiento; }
    public String getMotivo() { return motivo; }
}
