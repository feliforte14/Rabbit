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
        if (ultima != null && !fechaCambio.isAfter(ultima)) {
            LOG.info("[Notificaciones] Descartado " + estado + " del pedido " + idPedido
                    + ": ya se avisó un cambio más nuevo");
            return false;
        }

        String texto = "Tu pedido #" + idPedido + " " + describir(estado);
        Notificacion notificacion = new Notificacion();
        notificacion.setIdPedido(idPedido);
        notificacion.setIdComercio(idComercio);
        notificacion.setEstadoPedido(estado);
        notificacion.setFechaCambio(fechaCambio);
        notificacion.setFechaAviso(LocalDateTime.now());
        notificacion.setMensaje(texto);
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

    private static String describir(String estado) {
        return switch (estado) {
            case "PENDIENTE" -> "fue recibido por Rabbit";
            // Vale para los dos casos: un repartidor propio o un transportista.
            case "CONFIRMADO" -> "fue confirmado y ya tiene quién lo lleve";
            case "EN_CAMINO" -> "está en camino";
            case "ENTREGADO" -> "fue entregado";
            case "CANCELADO" -> "fue cancelado";
            default -> "cambió de estado a " + estado;
        };
    }
}
