package com.rabbit.integracion.legado;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

/**
 * CONTRATO DEL SERVICIO SOAP (SEI — Service Endpoint Interface).
 *
 * Simula un padrón fiscal tipo ARCA/AFIP (ver clase 9, slide 37: caso real
 * de integración SOAP en Argentina): antes de dar de alta o actualizar los
 * datos fiscales de un Comercio, Rabbit consulta si el CUIT existe y está
 * habilitado para operar — es la validación sincrónica que la Entrega 4
 * exige contra "un sistema legado", análoga a como MediConecta valida
 * cobertura con la obra social antes de confirmar un turno.
 *
 * Esta misma interfaz la implementa {@link PadronFiscalServiceImpl} (el
 * proveedor — acá, un mock desplegado en el propio WAR de Rabbit) y la usa
 * {@link PadronFiscalClient} (el consumidor) para armar el proxy dinámico
 * con {@code Service.getPort(...)}, sin generar stubs con wsimport: al
 * compartir clase compilada no hace falta.
 *
 * document/literal wrapped (ver clase 9, slide 22): el default de JAX-WS,
 * el que exige WS-I Basic Profile para máxima interoperabilidad.
 */
@WebService(name = "PadronFiscalPortType", targetNamespace = "http://rabbit.example/legado/padronfiscal")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, use = SOAPBinding.Use.LITERAL, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public interface PadronFiscalService {

    /**
     * @param cuit CUIT a consultar, formato XX-XXXXXXXX-X
     * @return estado del contribuyente si existe en el padrón
     * @throws CuitInexistenteException si no hay contribuyente con ese CUIT
     */
    @WebMethod(operationName = "consultarCuit")
    @WebResult(name = "estado")
    EstadoContribuyenteDTO consultarCuit(@WebParam(name = "cuit") String cuit) throws CuitInexistenteException;
}
