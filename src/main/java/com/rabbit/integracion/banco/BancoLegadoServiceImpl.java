package com.rabbit.integracion.banco;

/**
 * BANCO SIMULADO (el sistema legado).
 *
 * En producción esto NO sería parte de Rabbit: es el reemplazo, para el
 * alcance del TP, del sistema de un banco real. Vive en el mismo WAR solo
 * por simplicidad de despliegue (WildFly publica automáticamente un POJO
 * @WebService), pero Rabbit lo consume igual que si fuera externo: por
 * SOAP/HTTP y con timeout, nunca con una llamada Java directa.
 *
 * Guarda sus movimientos en memoria (se pierden al redesplegar): alcanza
 * para mostrar en el log qué se autorizó y qué se reversó.
 *
 * Regla determinística para la demo: un pago de más de $500.000 se
 * rechaza (supera el límite); cualquier otro se autoriza.
 */

import jakarta.jws.WebService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

@WebService(
        endpointInterface = "com.rabbit.integracion.banco.BancoLegadoService",
        serviceName = "BancoLegadoService",
        portName = "BancoLegadoPort",
        targetNamespace = "http://rabbit.example/legado/banco")
public class BancoLegadoServiceImpl implements BancoLegadoService {

    private static final Logger LOG = Logger.getLogger(BancoLegadoServiceImpl.class.getName());

    public static final BigDecimal LIMITE = new BigDecimal("500000");

    private static final AtomicLong SECUENCIA = new AtomicLong();

    // código de autorización -> "referencia importe AUTORIZADO|REVERSADO"
    private static final Map<String, String> MOVIMIENTOS = new ConcurrentHashMap<>();

    @Override
    public String autorizarPago(String referencia, BigDecimal importe) throws PagoRechazadoException {
        if (importe == null || importe.compareTo(LIMITE) > 0) {
            String motivo = "El importe supera el límite de $" + LIMITE.toPlainString();
            LOG.info("[Banco legado] Rechazado el pago de " + referencia + ": " + motivo);
            throw new PagoRechazadoException(motivo, new PagoRechazadoFaultInfo(motivo));
        }
        String codigo = "AUT-" + SECUENCIA.incrementAndGet();
        MOVIMIENTOS.put(codigo, referencia + " $" + importe.toPlainString() + " AUTORIZADO");
        LOG.info("[Banco legado] Autorizado " + codigo + ": " + referencia + " $" + importe.toPlainString());
        return codigo;
    }

    @Override
    public void reversarPago(String codigoAutorizacion) {
        String movimiento = MOVIMIENTOS.get(codigoAutorizacion);
        if (movimiento == null || movimiento.endsWith("REVERSADO")) {
            return;
        }
        MOVIMIENTOS.put(codigoAutorizacion, movimiento.replace("AUTORIZADO", "REVERSADO"));
        LOG.info("[Banco legado] Reversado " + codigoAutorizacion + " (" + movimiento.replace(" AUTORIZADO", "") + ")");
    }
}
