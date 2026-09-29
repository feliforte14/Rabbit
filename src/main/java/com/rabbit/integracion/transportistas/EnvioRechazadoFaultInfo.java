package com.rabbit.integracion.transportistas;

/** Detalle del soap:Fault EnvioRechazado: el motivo del rechazo. */
public class EnvioRechazadoFaultInfo {

    private String motivo;

    public EnvioRechazadoFaultInfo() {}

    public EnvioRechazadoFaultInfo(String motivo) {
        this.motivo = motivo;
    }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
