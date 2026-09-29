package com.rabbit.integracion.banco;

/**
 * CLIENTE SOAP del banco legado: el lado consumidor de la integración
 * sincrónica. PagoService lo usa para cobrar un pedido PREPAGO al
 * confirmarlo y para devolver la plata cuando hace falta.
 *
 * Sin wsimport: como {@link BancoLegadoService} es una interfaz Java
 * compartida por el banco simulado y Rabbit, se arma un proxy dinámico con
 * Service.getPort(...). Si el banco fuera realmente externo con su propio
 * WSDL, este cliente se generaría con wsimport, sin tocar PagoService.
 *
 * TIMEOUT de 5 s (conexión y respuesta). Sin respuesta del banco no se
 * puede saber si cobró o no, así que el pedido NO se confirma
 * (NO_DISPONIBLE). Limitación conocida: si el banco cobró pero la
 * respuesta se perdió por el timeout, ese cobro queda huérfano en el
 * banco; lo resolvería una clave de idempotencia más una consulta de
 * estado antes de reintentar.
 *
 * CIRCUIT BREAKER: antes de cada llamada se consulta a
 * {@link CircuitBreakerBanco}. Con el circuito abierto (el banco viene
 * fallando) se contesta NO_DISPONIBLE al instante, sin esperar el timeout.
 */

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;

@Stateless
public class BancoClient implements IBancoClient {

    private static final Logger LOG = Logger.getLogger(BancoClient.class.getName());

    private static final String NAMESPACE = "http://rabbit.example/legado/banco";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "BancoLegadoService");
    private static final QName PORT_QNAME = new QName(NAMESPACE, "BancoLegadoPort");

    // Por defecto, el banco simulado en Java del mismo WildFly; se llama
    // igual por SOAP/HTTP que si fuera externo. Con la system property
    // rabbit.banco.wsdl se apunta a otro banco con el mismo contrato, por
    // ejemplo el banco en Node.js de banco-legado/ (heterogeneidad
    // tecnológica, ver docs/DESAFIOS-OPCIONALES.md). Se lee al desplegar.
    private static final String WSDL_LOCATION = System.getProperty(
            "rabbit.banco.wsdl",
            "http://localhost:8080/Rabbit/BancoLegadoService?wsdl");

    private static final int TIMEOUT_MS = 5000;

    // El WSDL se descarga y parsea una sola vez. Service.create no tiene
    // timeout propio: es el único punto que puede demorar más de TIMEOUT_MS.
    private static volatile Service serviceCache;

    @Inject
    private CircuitBreakerBanco circuito;

    @Override
    public ResultadoAutorizacion autorizar(Long idPedido, BigDecimal importe) {
        long ticket = circuito.intentarLlamada();
        if (ticket == CircuitBreakerBanco.SIN_PERMISO) {
            LOG.info("[Pagos][SOAP] Circuito abierto: no se llama al banco para el pedido " + idPedido);
            return ResultadoAutorizacion.noDisponible();
        }
        try {
            String codigo = puerto().autorizarPago("PEDIDO-" + idPedido, importe);
            circuito.registrarExito(ticket);
            return ResultadoAutorizacion.aprobado(codigo);
        } catch (PagoRechazadoException e) {
            // Un rechazo es una respuesta del banco: no es una falla.
            circuito.registrarExito(ticket);
            return ResultadoAutorizacion.rechazado(e.getFaultInfo() != null ? e.getFaultInfo().getMotivo() : e.getMessage());
        } catch (WebServiceException | MalformedURLException e) {
            circuito.registrarFalla(ticket);
            LOG.log(Level.WARNING, "[Pagos][SOAP] El banco no respondió al autorizar el pedido " + idPedido, e);
            return ResultadoAutorizacion.noDisponible();
        }
    }

    @Override
    public boolean reversar(String codigoAutorizacion) {
        // Con el circuito abierto la reversa tampoco se intenta: devuelve
        // false y ReversasBancarias la deja logueada para hacerla a mano.
        long ticket = circuito.intentarLlamada();
        if (ticket == CircuitBreakerBanco.SIN_PERMISO) {
            LOG.info("[Pagos][SOAP] Circuito abierto: no se llama al banco para reversar " + codigoAutorizacion);
            return false;
        }
        try {
            puerto().reversarPago(codigoAutorizacion);
            circuito.registrarExito(ticket);
            return true;
        } catch (WebServiceException | MalformedURLException e) {
            circuito.registrarFalla(ticket);
            LOG.log(Level.WARNING, "[Pagos][SOAP] El banco no respondió al reversar " + codigoAutorizacion, e);
            return false;
        }
    }

    private BancoLegadoService puerto() throws MalformedURLException {
        BancoLegadoService port = obtenerService().getPort(PORT_QNAME, BancoLegadoService.class);
        BindingProvider bp = (BindingProvider) port;
        bp.getRequestContext().put("jakarta.xml.ws.client.connectionTimeout", TIMEOUT_MS);
        bp.getRequestContext().put("jakarta.xml.ws.client.receiveTimeout", TIMEOUT_MS);
        return port;
    }

    private static Service obtenerService() throws MalformedURLException {
        Service local = serviceCache;
        if (local == null) {
            synchronized (BancoClient.class) {
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
