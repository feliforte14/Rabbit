package com.rabbit.inventario.dto;

/** Lo que llegó de una línea (ADR-058), cargado por el personal del depósito. */
public class LineaRecibidaDTO {
    public Long idLinea;
    public Integer cantidadRecibida;

    public LineaRecibidaDTO() {
    }

    public LineaRecibidaDTO(Long idLinea, Integer cantidadRecibida) {
        this.idLinea = idLinea;
        this.cantidadRecibida = cantidadRecibida;
    }

    public Long getIdLinea() { return idLinea; }
    public void setIdLinea(Long idLinea) { this.idLinea = idLinea; }
    public Integer getCantidadRecibida() { return cantidadRecibida; }
    public void setCantidadRecibida(Integer cantidadRecibida) { this.cantidadRecibida = cantidadRecibida; }
}
