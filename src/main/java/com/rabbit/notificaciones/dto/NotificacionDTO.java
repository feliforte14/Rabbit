package com.rabbit.notificaciones.dto;

import com.rabbit.notificaciones.datos.model.Notificacion;
import java.time.format.DateTimeFormatter;

// DTO de salida: un aviso enviado, para listarlo en la vista.
public class NotificacionDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    public Long id;
    public Long idPedido;
    public Long idComercio;
    public String estadoPedido;
    public String fechaAviso;
    public String mensaje;

    public static NotificacionDTO desde(Notificacion n) {
        NotificacionDTO dto = new NotificacionDTO();
        dto.id = n.getId();
        dto.idPedido = n.getIdPedido();
        dto.idComercio = n.getIdComercio();
        dto.estadoPedido = n.getEstadoPedido();
        dto.fechaAviso = n.getFechaAviso() != null ? n.getFechaAviso().format(FORMATO) : null;
        dto.mensaje = n.getMensaje();
        return dto;
    }

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public Long getIdComercio() { return idComercio; }
    public String getEstadoPedido() { return estadoPedido; }
    public String getFechaAviso() { return fechaAviso; }
    public String getMensaje() { return mensaje; }
}
