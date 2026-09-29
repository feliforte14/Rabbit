package com.rabbit.ruteo.dto;

/** Qué pasó al despachar un pedido según su zona. */
public class ResultadoDespachoDTO {

    public enum Resultado {
        /** Confirmado con un repartidor propio. */
        REPARTIDOR,
        /** Derivado a un transportista (el de la zona o el de respaldo). */
        DERIVADO,
        /** Sin código postal o fuera de toda zona: queda para decidir a mano. */
        SIN_ZONA,
        /** No se pudo despachar (sin repartidores, banco o transportista rechazó...). */
        ERROR
    }

    public Long idPedido;
    public Resultado resultado;
    public String detalle;

    public static ResultadoDespachoDTO de(Long idPedido, Resultado resultado, String detalle) {
        ResultadoDespachoDTO r = new ResultadoDespachoDTO();
        r.idPedido = idPedido;
        r.resultado = resultado;
        r.detalle = detalle;
        return r;
    }

    public Long getIdPedido() { return idPedido; }
    public Resultado getResultado() { return resultado; }
    public String getDetalle() { return detalle; }
}
