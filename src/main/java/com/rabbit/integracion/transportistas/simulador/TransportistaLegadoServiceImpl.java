package com.rabbit.integracion.transportistas.simulador;

/**
 * TRANSPORTISTA SIMULADO con sistema legado SOAP (el que consume
 * AdaptadorSoapTransportista). WSDL:
 *   http://localhost:8080/Rabbit/TransportistaLegadoService?wsdl
 * Habla su propio vocabulario: RECIBIDO, EN_VIAJE, ENTREGADO, ANULADO.
 * Mismas reglas que el transportista REST (ver SimuladorDeEnvios).
 */

import com.rabbit.integracion.transportistas.EnvioRechazadoException;
import com.rabbit.integracion.transportistas.EnvioRechazadoFaultInfo;
import com.rabbit.integracion.transportistas.TransportistaLegadoService;
import jakarta.jws.WebService;

@WebService(
        endpointInterface = "com.rabbit.integracion.transportistas.TransportistaLegadoService",
        serviceName = "TransportistaLegadoService",
        portName = "TransportistaLegadoPort",
        targetNamespace = "http://rabbit.example/legado/transportista")
public class TransportistaLegadoServiceImpl implements TransportistaLegadoService {

    private static final SimuladorDeEnvios SIMULADOR = new SimuladorDeEnvios("Transportista legado", "TL-");

    @Override
    public String registrarEnvio(String referencia, String direccionRetiro, String direccionEntrega, int bultos)
            throws EnvioRechazadoException {
        String motivo = SIMULADOR.motivoDeRechazo(bultos);
        if (motivo != null) {
            throw new EnvioRechazadoException(motivo, new EnvioRechazadoFaultInfo(motivo));
        }
        return SIMULADOR.registrar(referencia, direccionEntrega);
    }

    @Override
    public String consultarEnvio(String codigoSeguimiento) {
        SimuladorDeEnvios.Estado estado = SIMULADOR.estado(codigoSeguimiento);
        if (estado == null) {
            return null;
        }
        switch (estado) {
            case SOLICITADO: return "RECIBIDO";
            case EN_TRANSITO: return "EN_VIAJE";
            case ENTREGADO: return "ENTREGADO";
            default: return "ANULADO";
        }
    }

    @Override
    public void anularEnvio(String codigoSeguimiento) throws EnvioRechazadoException {
        if (SIMULADOR.cancelar(codigoSeguimiento) == SimuladorDeEnvios.ResultadoCancelacion.YA_ENTREGADO) {
            String motivo = "El envío ya fue entregado: no se puede anular";
            throw new EnvioRechazadoException(motivo, new EnvioRechazadoFaultInfo(motivo));
        }
    }
}
