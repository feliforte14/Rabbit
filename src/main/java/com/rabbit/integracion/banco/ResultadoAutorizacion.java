package com.rabbit.integracion.banco;

/**
 * Resultado de {@link IBancoClient#autorizar}. Tres desenlaces, no dos:
 * distingue "el banco dijo que no" (RECHAZADO, con motivo) de "el banco no
 * contestó" (NO_DISPONIBLE: timeout o banco caído). Para confirmar el
 * pedido los dos terminan igual, pero el mensaje al usuario no.
 */
public class ResultadoAutorizacion {

    public enum Estado { APROBADO, RECHAZADO, NO_DISPONIBLE }

    private final Estado estado;
    private final String codigoAutorizacion;
    private final String motivo;

    private ResultadoAutorizacion(Estado estado, String codigoAutorizacion, String motivo) {
        this.estado = estado;
        this.codigoAutorizacion = codigoAutorizacion;
        this.motivo = motivo;
    }

    public static ResultadoAutorizacion aprobado(String codigoAutorizacion) {
        return new ResultadoAutorizacion(Estado.APROBADO, codigoAutorizacion, null);
    }

    public static ResultadoAutorizacion rechazado(String motivo) {
        return new ResultadoAutorizacion(Estado.RECHAZADO, null, motivo);
    }

    public static ResultadoAutorizacion noDisponible() {
        return new ResultadoAutorizacion(Estado.NO_DISPONIBLE, null, null);
    }

    public Estado getEstado() { return estado; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public String getMotivo() { return motivo; }
}
