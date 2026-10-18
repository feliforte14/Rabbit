package com.rabbit.notificaciones.negocio;

/**
 * CAPA DE NEGOCIO — componente ServicioDeNotificaciones (EJB @Stateless).
 *
 * Arma y registra el aviso al comercio por cada cambio de estado de un
 * pedido. En el alcance del TP el "envío" es simulado: el aviso queda
 * guardado (y se ve en pedidos.xhtml) y en el log, en vez de salir por
 * email o push.
 *
 * ORDEN DE LLEGADA: un MDB no garantiza que los mensajes lleguen en el
 * orden en que se publicaron. Si llegara CONFIRMADO después de EN_CAMINO,
 * avisarle al comercio "tu pedido fue confirmado" sería información vieja.
 * Por eso se compara la fechaCambio del evento con la última ya avisada
 * para ese pedido y se descartan los eventos más viejos o repetidos.
 */

import com.rabbit.notificaciones.datos.NotificacionRepository;
import com.rabbit.notificaciones.datos.model.Notificacion;
import com.rabbit.notificaciones.dto.NotificacionDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.IntentosDeEntrega;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// SEGURIDAD: @PermitAll de clase porque los avisos los genera el suscriptor
// del tópico, sin usuario. Un COMERCIO lee solo los avisos de su comercio.
@Stateless
@DeclareRoles({"COMERCIO"})
@PermitAll
public class NotificacionService implements INotificaciones {

    @Inject
    private IContextoUsuario contextoUsuario;

    // Para el código de seguimiento y el remito que se mencionan en el aviso.
    @Inject
    private ISeguimientoPedido pedidos;


    private static final Logger LOG = Logger.getLogger(NotificacionService.class.getName());

    @Inject
    private NotificacionRepository repository;

    // Para el canal de mail (AvisosPorMail), que se manda después del commit.
    @Inject
    private Event<AvisoRegistrado> avisoRegistrado;

    @Inject
    private IConsultaComercios comercios;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public boolean avisarCambioDeEstado(Long idPedido, Long idComercio, String estado, LocalDateTime fechaCambio) {
        LocalDateTime ultima = repository.ultimaFechaCambio(idPedido);
        // El aviso de una visita fallida no se descarta aunque llegue después
        // del de la reasignación (pasa en menos de un segundo): es un hecho,
        // no el estado actual (ADR-045).
        if (ultima != null && !fechaCambio.isAfter(ultima) && !esAvisoDeFalla(idPedido, estado, fechaCambio)) {
            LOG.info("[Notificaciones] Descartado " + estado + " del pedido " + idPedido
                    + ": ya se avisó un cambio más nuevo");
            return false;
        }

        PedidoDTO fallido = "PENDIENTE".equals(estado) || "DEVUELTO_AL_COMERCIO".equals(estado)
                ? pedidoConEntregaFallida(idPedido) : null;
        // Un PENDIENTE de alta que llega tarde (después de una falla) no es la falla.
        if (fallido != null && "PENDIENTE".equals(estado)
                && !AvisosDeFalla.esDeLaFalla(fechaCambio, fallido.fechaUltimaFalla)) {
            fallido = null;
        }
        String texto;
        if (fallido != null) {
            texto = TextosAlComercio.entregaFallida(idPedido, estado, fallido.motivoEntregaFallida,
                    fallido.motivoDevolucion, fallido.ultimaFallaContoComoVisita, fallido.intentosFallidos,
                    IntentosDeEntrega.maximo());
        } else {
            texto = "Tu pedido #" + idPedido + " " + describir(estado) + extra(idPedido, estado);
        }
        Notificacion notificacion = new Notificacion();
        notificacion.setIdPedido(idPedido);
        notificacion.setIdComercio(idComercio);
        notificacion.setEstadoPedido(estado);
        notificacion.setFechaCambio(fechaCambio);
        notificacion.setFechaAviso(LocalDateTime.now());
        // La columna es de 255 (hbm2ddl update no la agranda): en el portal
        // va recortado; el mail lleva el texto completo.
        notificacion.setMensaje(texto.length() <= 255 ? texto : texto.substring(0, 252) + "...");
        repository.guardar(notificacion);
        LOG.info("[Notificaciones] Aviso al comercio " + idComercio + ": " + texto);
        avisoRegistrado.fire(new AvisoRegistrado(idComercio, emailDe(idComercio), idPedido, texto));
        return true;
    }

    @Override
    public List<NotificacionDTO> listarRecientes(int cantidad) {
        return repository.listarRecientes(cantidad).stream().map(NotificacionDTO::desde).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed("COMERCIO")
    public List<NotificacionDTO> listarDelComercioActual(int cantidad) {
        return repository.listarDeComercio(contextoUsuario.idComercioActual(), cantidad).stream()
                .map(NotificacionDTO::desde)
                .collect(Collectors.toList());
    }

    // El email del comercio, para el canal de mail. Si no se puede leer, el
    // aviso igual queda en el portal (no se manda mail).
    private String emailDe(Long idComercio) {
        try {
            ComercioDTO comercio = comercios.obtenerComercio(idComercio);
            return comercio != null && comercio.email != null && !comercio.email.isBlank() ? comercio.email.trim() : null;
        } catch (RuntimeException e) {
            LOG.warning("[Notificaciones] No se pudo leer el email del comercio " + idComercio + ": " + e.getMessage());
            return null;
        }
    }

    // En camino: con qué código lo sigue el cliente en el mapa (ADR-035).
    // Entregado: dónde está la prueba (ADR-033). Si no se puede leer el
    // pedido, el aviso sale igual, sin el agregado.
    private String extra(Long idPedido, String estado) {
        try {
            if ("EN_CAMINO".equals(estado)) {
                String codigo = pedidos.consultarEstadoPedido(idPedido).getCodigoSeguimiento();
                return codigo == null ? "" : ". Tu cliente lo puede seguir en vivo con el código " + codigo
                        + " en la página de seguimiento de Rabbit";
            }
            if ("ENTREGADO".equals(estado) && pedidos.consultarEstadoPedido(idPedido).isConRemito()) {
                return ". El remito firmado está en tu portal, en Mis pedidos";
            }
        } catch (RuntimeException e) {
            LOG.warning("[Notificaciones] No se pudo leer el pedido " + idPedido + ": " + e.getMessage());
        }
        return "";
    }

    // Si este cambio es el de la última visita fallida del pedido.
    private boolean esAvisoDeFalla(Long idPedido, String estado, LocalDateTime fechaCambio) {
        if (!"PENDIENTE".equals(estado)) {
            return false;
        }
        try {
            return AvisosDeFalla.esDeLaFalla(fechaCambio, pedidos.consultarEstadoPedido(idPedido).fechaUltimaFalla);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // Un PENDIENTE que ya tiene motivo de entrega fallida no es un pedido
    // recibido: es uno que el repartidor devolvió al depósito (ADR-037). Con
    // DEVUELTO_AL_COMERCIO, el motivo de la última visita (ADR-041). Null si
    // no es el caso o no se pudo leer.
    private PedidoDTO pedidoConEntregaFallida(Long idPedido) {
        try {
            PedidoDTO pedido = pedidos.consultarEstadoPedido(idPedido);
            return pedido.motivoEntregaFallida != null ? pedido : null;
        } catch (RuntimeException e) {
            LOG.warning("[Notificaciones] No se pudo leer el pedido " + idPedido + ": " + e.getMessage());
            return null;
        }
    }

    private static String describir(String estado) {
        return switch (estado) {
            case "PENDIENTE" -> "fue recibido por Rabbit";
            // Vale para los dos casos: un repartidor propio o un transportista.
            case "CONFIRMADO" -> "fue confirmado y ya tiene quién lo lleve";
            case "EN_CAMINO" -> "está en camino";
            case "ENTREGADO" -> "fue entregado";
            case "CANCELADO" -> "fue cancelado";
            case "DEVUELTO_AL_COMERCIO" -> "no se pudo entregar y vuelve a tu comercio";
            default -> "cambió de estado a " + estado;
        };
    }
}
