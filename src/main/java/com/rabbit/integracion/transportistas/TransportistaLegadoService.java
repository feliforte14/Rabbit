package com.rabbit.integracion.transportistas;

/**
 * CONTRATO SOAP (SEI) de un transportista legado: una empresa de envíos con
 * un sistema viejo que solo habla SOAP. Lo implementa el transportista
 * simulado (simulador.TransportistaLegadoServiceImpl) y lo consume
 * AdaptadorSoapTransportista, igual que BancoLegadoService con el banco.
 *
 * Habla su propio idioma: los estados de un envío son RECIBIDO, EN_VIAJE,
 * ENTREGADO y ANULADO. El adaptador los traduce al modelo de Rabbit.
 */

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

@WebService(name = "TransportistaLegadoPortType", targetNamespace = "http://rabbit.example/legado/transportista")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, use = SOAPBinding.Use.LITERAL, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public interface TransportistaLegadoService {

    /**
     * @return código de seguimiento del transportista
     * @throws EnvioRechazadoException si el transportista no toma el envío
     */
    @WebMethod(operationName = "registrarEnvio")
    @WebResult(name = "codigoSeguimiento")
    String registrarEnvio(@WebParam(name = "referencia") String referencia,
                          @WebParam(name = "direccionRetiro") String direccionRetiro,
                          @WebParam(name = "direccionEntrega") String direccionEntrega,
                          @WebParam(name = "bultos") int bultos) throws EnvioRechazadoException;

    /** @return RECIBIDO, EN_VIAJE, ENTREGADO o ANULADO; null si el código no existe */
    @WebMethod(operationName = "consultarEnvio")
    @WebResult(name = "estado")
    String consultarEnvio(@WebParam(name = "codigoSeguimiento") String codigoSeguimiento);

    /** Anula un envío que todavía no se entregó. Idempotente. */
    @WebMethod(operationName = "anularEnvio")
    void anularEnvio(@WebParam(name = "codigoSeguimiento") String codigoSeguimiento) throws EnvioRechazadoException;
}
