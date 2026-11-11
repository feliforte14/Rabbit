package com.rabbit.pedidos.dto;

/**
 * DTO de salida: la imagen del remito de un pedido, para mostrarla
 * (ver RemitoServlet). Solo lo arma PedidoService después de verificar que
 * quien lo pide puede verlo.
 */
public class RemitoDTO {

    public Long idPedido;
    public String tipoContenido;
    public byte[] imagen;

    public RemitoDTO(Long idPedido, String tipoContenido, byte[] imagen) {
        this.idPedido = idPedido;
        this.tipoContenido = tipoContenido;
        this.imagen = imagen;
    }
}
