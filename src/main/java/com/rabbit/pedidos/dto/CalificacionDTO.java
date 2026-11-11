package com.rabbit.pedidos.dto;

import com.rabbit.pedidos.datos.model.CalificacionEntrega;
import java.time.format.DateTimeFormatter;

/** DTO de salida: la calificación que dejó quien recibió el pedido (ADR-052). */
public class CalificacionDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public Long idPedido;
    public int estrellas;
    public String comentario;
    public String fecha;

    public static CalificacionDTO desde(CalificacionEntrega c) {
        CalificacionDTO dto = new CalificacionDTO();
        dto.idPedido = c.getIdPedido();
        dto.estrellas = c.getEstrellas();
        dto.comentario = c.getComentario();
        dto.fecha = c.getFecha() != null ? c.getFecha().format(FORMATO) : null;
        return dto;
    }

    public Long getIdPedido() { return idPedido; }
    public int getEstrellas() { return estrellas; }
    public String getComentario() { return comentario; }
    public String getFecha() { return fecha; }
}
