package com.rabbit.integracion.transportistas;

/**
 * ADAPTER de un transportista legado: SOAP con WSDL
 * (TransportistaLegadoService).
 *
 * Mismo esquema que BancoClient: proxy dinámico armado desde el WSDL
 * (Service.create + getPort, sin wsimport), timeout de 5 s y un Service
 * cacheado por WSDL. Traduce el vocabulario del legado (RECIBIDO,
 * EN_VIAJE, ENTREGADO, ANULADO) al modelo de Rabbit, y el soap:Fault
 * EnvioRechazado a un rechazo con motivo.
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;

@ApplicationScoped
public class AdaptadorSoapTransportista implements IAdaptadorTransportista {

    private static final Logger LOG = Logger.getLogger(AdaptadorSoapTransportista.class.getName());

    private static final String NAMESPACE = "http://rabbit.example/legado/transportista";
    private static final QName SERVICIO = new QName(NAMESPACE, "TransportistaLegadoService");
    private static final QName PUERTO = new QName(NAMESPACE, "TransportistaLegadoPort");
    private static final int TIMEOUT_MS = 5000;

    // WSDL -> Service: descargar y parsear el WSDL es lo caro; se hace una vez.
    private final Map<String, Service> servicios = new ConcurrentHashMap<>();

    @Override
    public ResultadoSolicitud solicitarEnvio(String endpoint, SolicitudEnvio s) {
        try {
            String codigo = puerto(endpoint).registrarEnvio(s.referencia(), s.direccionRetiro(),
                    s.direccionEntrega(), s.bultos());
            return ResultadoSolicitud.aceptado(codigo);
        } catch (EnvioRechazadoException e) {
            return ResultadoSolicitud.rechazado(e.getFaultInfo() != null ? e.getFaultInfo().getMotivo() : e.getMessage());
        } catch (WebServiceException | MalformedURLException e) {
            LOG.log(Level.WARNING, "[Transportistas][SOAP] El transportista no respondió al solicitar " + s.referencia(), e);
            return ResultadoSolicitud.noDisponible();
        }
    }

    // El WSDL del legado no tiene una operación de cotización: no se le
    // pregunta. Rabbit lo muestra como "no cotiza" y se puede derivar igual.
    @Override
    public ResultadoCotizacion cotizarEnvio(String endpoint, SolicitudEnvio s) {
        return ResultadoCotizacion.noCotiza();
    }

    @Override
    public EstadoExterno consultarEstado(String endpoint, String codigo) {
        try {
            return traducir(puerto(endpoint).consultarEnvio(codigo));
        } catch (WebServiceException | MalformedURLException e) {
            LOG.log(Level.FINE, "[Transportistas][SOAP] Sin respuesta al consultar " + codigo, e);
            return EstadoExterno.DESCONOCIDO;
        }
    }

    @Override
    public boolean cancelarEnvio(String endpoint, String codigo) {
        try {
            puerto(endpoint).anularEnvio(codigo);
            return true;
        } catch (EnvioRechazadoException e) {
            // Por ejemplo, ya lo entregó: no hay cancelación que confirmar.
            LOG.warning("[Transportistas][SOAP] No anuló " + codigo + ": "
                    + (e.getFaultInfo() != null ? e.getFaultInfo().getMotivo() : e.getMessage()));
            return false;
        } catch (WebServiceException | MalformedURLException e) {
            LOG.log(Level.WARNING, "[Transportistas][SOAP] Sin respuesta al anular " + codigo, e);
            return false;
        }
    }

    // Vocabulario del transportista legado -> modelo de Rabbit.
    private static EstadoExterno traducir(String estado) {
        if (estado == null) {
            return EstadoExterno.DESCONOCIDO;
        }
        switch (estado) {
            case "RECIBIDO": return EstadoExterno.SOLICITADO;
            case "EN_VIAJE": return EstadoExterno.EN_TRANSITO;
            case "ENTREGADO": return EstadoExterno.ENTREGADO;
            case "ANULADO": return EstadoExterno.CANCELADO;
            default: return EstadoExterno.DESCONOCIDO;
        }
    }

    private TransportistaLegadoService puerto(String wsdl) throws MalformedURLException {
        Service servicio = servicios.get(wsdl);
        if (servicio == null) {
            servicio = Service.create(new URL(wsdl), SERVICIO);
            servicios.put(wsdl, servicio);
        }
        TransportistaLegadoService puerto = servicio.getPort(PUERTO, TransportistaLegadoService.class);
        BindingProvider bp = (BindingProvider) puerto;
        bp.getRequestContext().put("jakarta.xml.ws.client.connectionTimeout", TIMEOUT_MS);
        bp.getRequestContext().put("jakarta.xml.ws.client.receiveTimeout", TIMEOUT_MS);
        return puerto;
    }
}
