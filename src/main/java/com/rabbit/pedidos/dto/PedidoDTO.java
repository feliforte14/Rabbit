package com.rabbit.pedidos.dto;

/**
 * DTO de salida: representa un pedido tal como se muestra en la vista.
 * Nunca se persiste (ver Pedido para la entidad).
 */

import com.rabbit.pedidos.datos.model.Pedido;
import java.time.format.DateTimeFormatter;

public class PedidoDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public Long id;
    public Long idComercio;
    public String producto;
    public int cantidad;
    public String estado;
    public String fechaCreacion;

    public static PedidoDTO desde(Pedido p) {
        PedidoDTO dto = new PedidoDTO();
        dto.id = p.getId();
        dto.idComercio = p.getIdComercio();
        dto.producto = p.getProducto();
        dto.cantidad = p.getCantidad();
        dto.estado = p.getEstado() != null ? p.getEstado().name() : null;
        dto.fechaCreacion = p.getFechaCreacion() != null ? p.getFechaCreacion().format(FORMATO) : null;
        return dto;
    }

    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public String getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
    public String getEstado() { return estado; }
    public String getFechaCreacion() { return fechaCreacion; }
}
