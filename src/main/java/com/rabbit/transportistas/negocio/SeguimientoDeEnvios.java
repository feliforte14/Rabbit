package com.rabbit.transportistas.negocio;

/**
 * CAPA DE NEGOCIO — seguimiento de los envíos derivados (EJB @Singleton).
 *
 * Cada 15 segundos le pregunta a cada transportista por sus envíos activos
 * y registra los cambios (ISeguimientoEnvios.registrarNovedad), que Pedidos
 * traduce en EN_CAMINO / ENTREGADO. Polling y no webhook: un transportista
 * legado no avisa, hay que preguntarle (ver ADR-016).
 *
 * @Singleton: una sola pasada a la vez, igual que SincronizadorDePedidos.
 * NOT_SUPPORTED: las consultas a los transportistas corren fuera de toda
 * transacción; cada novedad se guarda en la suya.
 *
 * @RunAs("OPERADOR"): el timer no tiene usuario, pero mover un pedido
 * (despacharPedido, registrarEntrega) es una operación del personal de
 * Rabbit. Corre con esa identidad de sistema.
 */

import com.rabbit.integracion.transportistas.EstadoExterno;
import com.rabbit.transportistas.datos.model.EstadoEnvio;
import com.rabbit.transportistas.datos.model.TipoIntegracion;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.EJBException;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Singleton
@Startup
@DeclareRoles("OPERADOR")
@RunAs("OPERADOR")
public class SeguimientoDeEnvios {

    private static final Logger LOG = Logger.getLogger(SeguimientoDeEnvios.class.getName());

    @Inject
    private ISeguimientoEnvios seguimiento;

    @Inject
    private IGestionTransportistas transportistas;

    @Inject
    private AdaptadoresTransportista adaptadores;

    @Schedule(hour = "*", minute = "*", second = "*/15", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void consultarEnviosActivos() {
        List<EnvioDTO> activos = seguimiento.listarEnviosActivos();
        if (activos.isEmpty()) {
            return;
        }
        Map<Long, TransportistaDTO> porId = transportistas.listarTodos().stream()
                .collect(Collectors.toMap(TransportistaDTO::getId, Function.identity()));
        for (EnvioDTO envio : activos) {
            TransportistaDTO t = porId.get(envio.getIdTransportista());
            EstadoExterno externo;
            // Cada envío por separado: un transportista que responde algo
            // inesperado no puede frenar el seguimiento de todos los demás.
            try {
                externo = adaptadores.para(TipoIntegracion.valueOf(t.getTipoIntegracion()))
                        .consultarEstado(t.getEndpoint(), envio.getCodigoSeguimiento());
            } catch (RuntimeException e) {
                LOG.warning("[Transportistas] No se pudo consultar el envío " + envio.getCodigoSeguimiento()
                        + " (pedido " + envio.getIdPedido() + "): " + e + ". Se reintenta en la próxima pasada.");
                continue;
            }
            EstadoEnvio nuevo = traducir(externo);
            if (nuevo == null || nuevo.name().equals(envio.getEstado())) {
                continue;
            }
            try {
                seguimiento.registrarNovedad(envio.getId(), nuevo);
            } catch (EJBException | ValidacionException e) {
                LOG.warning("[Transportistas] No se pudo registrar " + nuevo + " del envío "
                        + envio.getCodigoSeguimiento() + " (pedido " + envio.getIdPedido() + "): "
                        + e.getMessage() + ". Se reintenta en la próxima pasada.");
            }
        }
    }

    private static EstadoEnvio traducir(EstadoExterno externo) {
        switch (externo) {
            case SOLICITADO: return EstadoEnvio.SOLICITADO;
            case EN_TRANSITO: return EstadoEnvio.EN_TRANSITO;
            case ENTREGADO: return EstadoEnvio.ENTREGADO;
            case CANCELADO: return EstadoEnvio.CANCELADO;
            default: return null;
        }
    }
}
