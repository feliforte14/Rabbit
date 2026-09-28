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
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
public class NotificacionService implements INotificaciones {

    private static final Logger LOG = Logger.getLogger(NotificacionService.class.getName());

    @Inject
    private NotificacionRepository repository;

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
        return true;
    }

    @Override
    public List<NotificacionDTO> listarRecientes(int cantidad) {
        return repository.listarRecientes(cantidad).stream().map(NotificacionDTO::desde).collect(Collectors.toList());
    }

    private static String describir(String estado) {
        return switch (estado) {
            case "PENDIENTE" -> "fue recibido por Rabbit";
            case "CONFIRMADO" -> "fue confirmado y tiene repartidor asignado";
            case "EN_CAMINO" -> "está en camino";
            case "ENTREGADO" -> "fue entregado";
            case "CANCELADO" -> "fue cancelado";
            default -> "cambió de estado a " + estado;
        };
    }
}
