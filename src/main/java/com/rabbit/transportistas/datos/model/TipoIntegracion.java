package com.rabbit.transportistas.datos.model;

/** Con qué tecnología se integra un transportista (define qué adaptador se usa). */
public enum TipoIntegracion {
    /** API REST moderna con JSON: el endpoint es la URL base de la API. */
    REST,
    /** Sistema legado SOAP: el endpoint es la URL del WSDL. */
    SOAP_LEGADO
}
