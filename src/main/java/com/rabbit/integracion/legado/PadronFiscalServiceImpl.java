package com.rabbit.integracion.legado;

import jakarta.jws.WebService;

/**
 * MOCK DEL SISTEMA LEGADO (padrón fiscal tipo ARCA/AFIP).
 *
 * En producción esto NO sería parte de Rabbit — es el reemplazo, para el
 * alcance del TP, de un servicio externo real (ver clase 9, slide 37).
 * Vive en el mismo WAR solo por simplicidad de despliegue: WildFly lo
 * publica automáticamente por ser un POJO @WebService empaquetado en la
 * aplicación, sin tocar standalone.xml ni un WAR aparte. Rabbit lo
 * consume igual que si fuera externo: por SOAP/HTTP, con timeout, nunca
 * por una llamada Java directa (ver PadronFiscalClient).
 *
 * Regla determinística para la demo: el CUIT 20-00000000-0 siempre "no
 * existe" (dispara el Fault); cualquier otro CUIT con formato válido está
 * habilitado. Nada de aleatoriedad — así el camino de Fault es reproducible
 * a pedido en vez de depender de la suerte.
 */
@WebService(
        endpointInterface = "com.rabbit.integracion.legado.PadronFiscalService",
        serviceName = "PadronFiscalService",
        portName = "PadronFiscalPort",
        targetNamespace = "http://rabbit.example/legado/padronfiscal")
public class PadronFiscalServiceImpl implements PadronFiscalService {

    /** CUIT reservado para demostrar el camino de Fault en la demo. */
    public static final String CUIT_DEMO_NO_ENCONTRADO = "20-00000000-0";

    @Override
    public EstadoContribuyenteDTO consultarCuit(String cuit) throws CuitInexistenteException {
        String limpio = cuit == null ? "" : cuit.trim();

        if (limpio.equals(CUIT_DEMO_NO_ENCONTRADO)) {
            throw new CuitInexistenteException(
                    "No existe contribuyente registrado con CUIT " + limpio,
                    new CuitInexistenteFaultInfo(limpio));
        }

        return new EstadoContribuyenteDTO(limpio, "Contribuyente " + limpio, true);
    }
}
