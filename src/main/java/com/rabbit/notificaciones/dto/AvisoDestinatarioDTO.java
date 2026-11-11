package com.rabbit.notificaciones.dto;

import com.rabbit.notificaciones.datos.model.AvisoDestinatario;
import java.time.format.DateTimeFormatter;

// DTO de salida: un aviso a quien recibe el pedido (ADR-040), para el personal.
public class AvisoDestinatarioDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    public Long id;
    public Long idPedido;
    public String estadoPedido;
    public String fechaAviso;
    public String email;
    public String asunto;
    public String mensaje;

    public static AvisoDestinatarioDTO desde(AvisoDestinatario a) {
        AvisoDestinatarioDTO dto = new AvisoDestinatarioDTO();
        dto.id = a.getId();
        dto.idPedido = a.getIdPedido();
        dto.estadoPedido = a.getEstadoPedido();
        dto.fechaAviso = a.getFechaAviso() != null ? a.getFechaAviso().format(FORMATO) : null;
        dto.email = a.getEmail();
        dto.asunto = a.getAsunto();
        dto.mensaje = a.getMensaje();
        return dto;
    }

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public String getEstadoPedido() { return estadoPedido; }
    public String getFechaAviso() { return fechaAviso; }
    public String getEmail() { return email; }
    public String getAsunto() { return asunto; }
    public String getMensaje() { return mensaje; }
}
