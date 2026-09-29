package com.rabbit.notificaciones.negocio;

import com.rabbit.notificaciones.dto.NotificacionDTO;
import jakarta.ejb.Local;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CONTRATO del componente ServicioDeNotificaciones.
 */
@Local
public interface INotificaciones {

    /**
     * Genera el aviso al comercio por un cambio de estado de su pedido. La
     * llama el suscriptor al tópico de estados.
     *
     * Descarta el evento si ya se avisó un cambio igual o más nuevo de ese
     * pedido (llegada desordenada o duplicada).
     *
     * @return true si se generó el aviso, false si se descartó
     */
    boolean avisarCambioDeEstado(Long idPedido, Long idComercio, String estado, LocalDateTime fechaCambio);

    List<NotificacionDTO> listarRecientes(int cantidad);

    /** Últimos avisos del comercio que representa el usuario que llama (rol COMERCIO). */
    List<NotificacionDTO> listarDelComercioActual(int cantidad);
}
