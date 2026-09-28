package com.rabbit.integracion.legado;

import jakarta.xml.ws.WebFault;

/**
 * Excepción de negocio del servicio SOAP: el CUIT consultado no existe en
 * el padrón. @WebFault hace que el runtime JAX-WS la traduzca a un
 * soap:Fault (Sender) en vez de un error de transporte — ver clase 9,
 * slide 20. Del lado cliente ({@link PadronFiscalClient}) llega como esta
 * misma excepción chequeada, sin necesidad de generar stubs con wsimport
 * porque cliente y proveedor comparten la interfaz {@link PadronFiscalService}.
 */
@WebFault(name = "CuitInexistente", targetNamespace = "http://rabbit.example/legado/padronfiscal")
public class CuitInexistenteException extends Exception {

    private final CuitInexistenteFaultInfo faultInfo;

    public CuitInexistenteException(String message, CuitInexistenteFaultInfo faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    public CuitInexistenteFaultInfo getFaultInfo() {
        return faultInfo;
    }
}
