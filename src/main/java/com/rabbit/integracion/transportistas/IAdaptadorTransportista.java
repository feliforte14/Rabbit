package com.rabbit.integracion.transportistas;

/**
 * ADAPTER: el contrato común para hablar con cualquier transportista, sin
 * importar su tecnología. El componente Transportistas solo conoce esta
 * interfaz; cada implementación traduce al protocolo, al formato y al
 * vocabulario de estados de su transportista.
 *
 * Nunca lanza por problemas de comunicación: la falta de respuesta viaja en
 * el resultado (NO_DISPONIBLE, DESCONOCIDO, false).
 *
 * @see AdaptadorRestTransportista transportista moderno (REST/JSON)
 * @see AdaptadorSoapTransportista transportista legado (SOAP/WSDL)
 */
public interface IAdaptadorTransportista {

    /** @param endpoint URL base de la API (REST) o del WSDL (SOAP) del transportista */
    ResultadoSolicitud solicitarEnvio(String endpoint, SolicitudEnvio solicitud);

    /**
     * Cuánto cobraría y cuánto tardaría el envío, sin pedirlo. Un
     * transportista que no ofrece la operación (el legado) devuelve
     * NO_COTIZA sin llamar a nadie.
     */
    ResultadoCotizacion cotizarEnvio(String endpoint, SolicitudEnvio solicitud);

    EstadoExterno consultarEstado(String endpoint, String codigoSeguimiento);

    /** @return true si el transportista confirmó la cancelación */
    boolean cancelarEnvio(String endpoint, String codigoSeguimiento);
}
