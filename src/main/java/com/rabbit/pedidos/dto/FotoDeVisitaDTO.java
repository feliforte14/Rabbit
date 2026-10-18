package com.rabbit.pedidos.dto;

/**
 * DTO de salida: la imagen de una foto de visita fallida (ADR-045), para
 * mostrarla (ver FotoVisitaServlet). Solo lo arma PedidoService después de
 * verificar que quien lo pide puede verla.
 */
public class FotoDeVisitaDTO {

    public Long id;
    public Long idPedido;
    public String tipoContenido;
    public byte[] imagen;

    public FotoDeVisitaDTO(Long id, Long idPedido, String tipoContenido, byte[] imagen) {
        this.id = id;
        this.idPedido = idPedido;
        this.tipoContenido = tipoContenido;
        this.imagen = imagen;
    }
}
