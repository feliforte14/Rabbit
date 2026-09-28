package com.rabbit.integracion.legado;

import jakarta.ejb.Stateless;
import javax.xml.namespace.QName;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CLIENTE SOAP del padrón fiscal — el lado consumidor de la integración
 * sincrónica (ver clase 9). ComercioService lo invoca en cada alta o
 * actualización de datos fiscales, ANTES de guardar en la base.
 *
 * Sin wsimport: como {@link PadronFiscalService} es una interfaz Java
 * compartida por proveedor y consumidor (ambos viven en Rabbit por ahora,
 * ver PadronFiscalServiceImpl), se arma un proxy dinámico con
 * Service.getPort(...) en vez de generar stubs a partir del WSDL. Si el
 * padrón fuera un sistema realmente externo con su propio WSDL, este
 * cliente se reemplazaría por uno generado con wsimport, sin tocar
 * ComercioService (que solo conoce IPadronFiscalClient).
 *
 * Timeout (clase 9, slide 42 — "¿qué pasa si el legado no responde en 5
 * segundos?"): connectionTimeout/receiveTimeout en 5s. Si vence, o el
 * servicio está caído, NO se bloquea el alta del comercio: es una falla de
 * infraestructura ajena, no una regla de negocio violada. Vuelve
 * SERVICIO_NO_DISPONIBLE y ComercioService decide qué hacer con eso.
 */
@Stateless
public class PadronFiscalClient implements IPadronFiscalClient {

    private static final Logger LOG = Logger.getLogger(PadronFiscalClient.class.getName());

    private static final String NAMESPACE = "http://rabbit.example/legado/padronfiscal";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "PadronFiscalService");
    private static final QName PORT_QNAME = new QName(NAMESPACE, "PadronFiscalPort");

    // Mismo WildFly que Rabbit en este alcance (ver PadronFiscalServiceImpl);
    // se llama igual por SOAP/HTTP que si fuera externo. Overrideable por
    // system property para apuntar a un despliegue real más adelante.
    private static final String WSDL_LOCATION = System.getProperty(
            "rabbit.padronFiscal.wsdl",
            "http://localhost:8080/Rabbit/PadronFiscalService?wsdl");

    private static final int TIMEOUT_MS = 5000;

    // Cachear el Service evita volver a bajar y parsear el WSDL en cada
    // consulta (Service.create no tiene timeout propio: si el legado está
    // realmente caído, este fetch inicial es el único punto que puede
    // demorar más de TIMEOUT_MS — las llamadas siguientes ya no lo pagan).
    private static volatile Service serviceCache;

    @Override
    public ResultadoConsultaCuit consultar(String cuit) {
        try {
            PadronFiscalService port = obtenerService().getPort(PORT_QNAME, PadronFiscalService.class);
            BindingProvider bp = (BindingProvider) port;
            bp.getRequestContext().put("jakarta.xml.ws.client.connectionTimeout", TIMEOUT_MS);
            bp.getRequestContext().put("jakarta.xml.ws.client.receiveTimeout", TIMEOUT_MS);

            EstadoContribuyenteDTO estado = port.consultarCuit(cuit);
            return ResultadoConsultaCuit.habilitado(estado.getRazonSocial());

        } catch (CuitInexistenteException e) {
            return ResultadoConsultaCuit.noEncontrado();

        } catch (WebServiceException | MalformedURLException e) {
            LOG.log(Level.WARNING, "[Comercios][SOAP] Padrón fiscal no disponible para CUIT " + cuit, e);
            return ResultadoConsultaCuit.noDisponible();
        }
    }

    private static Service obtenerService() throws MalformedURLException {
        Service local = serviceCache;
        if (local == null) {
            synchronized (PadronFiscalClient.class) {
                local = serviceCache;
                if (local == null) {
                    local = Service.create(new URL(WSDL_LOCATION), SERVICE_QNAME);
                    serviceCache = local;
                }
            }
        }
        return local;
    }
}
