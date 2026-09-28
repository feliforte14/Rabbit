package com.rabbit.integracion.banco;

import jakarta.xml.ws.WebFault;

/**
 * Excepción de negocio del banco: el pago fue rechazado. @WebFault hace que
 * JAX-WS la viaje como un soap:Fault y no como un error de transporte; del
 * lado de Rabbit ({@link BancoClient}) llega como esta misma excepción.
 */
@WebFault(name = "PagoRechazado", targetNamespace = "http://rabbit.example/legado/banco")
public class PagoRechazadoException extends Exception {

    private final PagoRechazadoFaultInfo faultInfo;

    public PagoRechazadoException(String message, PagoRechazadoFaultInfo faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    public PagoRechazadoFaultInfo getFaultInfo() {
        return faultInfo;
    }
}
