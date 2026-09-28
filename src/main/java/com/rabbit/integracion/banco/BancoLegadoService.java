package com.rabbit.integracion.banco;

/**
 * CONTRATO DEL SERVICIO SOAP del banco (SEI — Service Endpoint Interface).
 *
 * Simula el sistema legado de un banco con el que Rabbit cobra los pedidos
 * PREPAGO. Dos operaciones:
 *   - autorizarPago: el banco cobra y devuelve un código de autorización.
 *   - reversarPago: el banco devuelve la plata de una autorización.
 *
 * La implementa {@link BancoLegadoServiceImpl} (el banco simulado,
 * desplegado en el mismo WAR) y la usa {@link BancoClient} (Rabbit) para
 * armar el proxy SOAP con Service.getPort(...), sin generar stubs con
 * wsimport: al compartir la interfaz compilada no hace falta.
 *
 * document/literal wrapped: el default de JAX-WS y el que exige WS-I
 * Basic Profile para máxima interoperabilidad.
 */

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;
import java.math.BigDecimal;

@WebService(name = "BancoLegadoPortType", targetNamespace = "http://rabbit.example/legado/banco")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, use = SOAPBinding.Use.LITERAL, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public interface BancoLegadoService {

    /**
     * @param referencia identificador del pago del lado de Rabbit (el pedido)
     * @param importe    importe a cobrar
     * @return código de autorización del banco
     * @throws PagoRechazadoException si el banco rechaza el pago
     */
    @WebMethod(operationName = "autorizarPago")
    @WebResult(name = "codigoAutorizacion")
    String autorizarPago(@WebParam(name = "referencia") String referencia,
                         @WebParam(name = "importe") BigDecimal importe) throws PagoRechazadoException;

    /**
     * Devuelve la plata de una autorización. Idempotente: reversar un
     * código ya reversado (o inexistente) no hace nada.
     *
     * @param codigoAutorizacion el código que devolvió autorizarPago
     */
    @WebMethod(operationName = "reversarPago")
    void reversarPago(@WebParam(name = "codigoAutorizacion") String codigoAutorizacion);
}
