package com.rabbit.integracion.transportistas;

/**
 * El transportista legado no toma el envío. @WebFault hace que viaje como
 * un soap:Fault tipado (con el motivo), igual que PagoRechazadoException
 * con el banco.
 */

import jakarta.xml.ws.WebFault;

@WebFault(name = "EnvioRechazado", targetNamespace = "http://rabbit.example/legado/transportista")
public class EnvioRechazadoException extends Exception {

    private final EnvioRechazadoFaultInfo faultInfo;

    public EnvioRechazadoException(String message, EnvioRechazadoFaultInfo faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    public EnvioRechazadoFaultInfo getFaultInfo() {
        return faultInfo;
    }
}
