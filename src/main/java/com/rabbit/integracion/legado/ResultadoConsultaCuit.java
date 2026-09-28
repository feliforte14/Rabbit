package com.rabbit.integracion.legado;

/**
 * Resultado de {@link IPadronFiscalClient#consultar}. Tres desenlaces
 * posibles, no dos: distingue "el padrón contestó que no existe" (falla de
 * negocio, bloquea el alta) de "el padrón no contestó" (falla de
 * infraestructura ajena — ver el desafío del timeout en clase 9, slide 42).
 * Modelarlo así evita sobrecargar una excepción para las dos cosas.
 */
public class ResultadoConsultaCuit {

    public enum Estado { HABILITADO, NO_ENCONTRADO, SERVICIO_NO_DISPONIBLE }

    private final Estado estado;
    private final String razonSocial;

    private ResultadoConsultaCuit(Estado estado, String razonSocial) {
        this.estado = estado;
        this.razonSocial = razonSocial;
    }

    public static ResultadoConsultaCuit habilitado(String razonSocial) {
        return new ResultadoConsultaCuit(Estado.HABILITADO, razonSocial);
    }

    public static ResultadoConsultaCuit noEncontrado() {
        return new ResultadoConsultaCuit(Estado.NO_ENCONTRADO, null);
    }

    public static ResultadoConsultaCuit noDisponible() {
        return new ResultadoConsultaCuit(Estado.SERVICIO_NO_DISPONIBLE, null);
    }

    public Estado getEstado() { return estado; }
    public String getRazonSocial() { return razonSocial; }
}
