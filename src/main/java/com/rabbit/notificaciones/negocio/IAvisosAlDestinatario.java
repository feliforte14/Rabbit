package com.rabbit.notificaciones.negocio;

import com.rabbit.notificaciones.dto.AvisoDestinatarioDTO;
import jakarta.ejb.Local;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CONTRATO de los avisos a quien recibe el pedido (ADR-040), dentro del
 * componente ServicioDeNotificaciones. Lo implementa AvisoDestinatarioService.
 */
@Local
public interface IAvisosAlDestinatario {

    /**
     * Le avisa a quien recibe el pedido que cambió de estado, por mail y con
     * el link al seguimiento. La llama el suscriptor al tópico de estados.
     *
     * No hace nada si el ERP no informó el email de quien recibe, ni si ya
     * se avisó un cambio igual o más nuevo de ese pedido (llegada
     * desordenada o duplicada).
     *
     * @return true si se generó el aviso
     */
    boolean avisar(Long idPedido, Long idComercio, String estado, LocalDateTime fechaCambio);

    /** Últimos avisos a destinatarios, de todos los comercios (solo el personal). */
    List<AvisoDestinatarioDTO> listarRecientes(int cantidad);
}
