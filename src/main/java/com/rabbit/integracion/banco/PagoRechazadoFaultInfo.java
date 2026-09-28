package com.rabbit.integracion.banco;

/**
 * Detalle que viaja dentro de &lt;soap:Fault&gt;&lt;soap:Detail&gt; cuando el
 * banco rechaza un pago: el motivo del rechazo.
 */
public class PagoRechazadoFaultInfo {

    private String motivo;

    public PagoRechazadoFaultInfo() {}

    public PagoRechazadoFaultInfo(String motivo) {
        this.motivo = motivo;
    }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
