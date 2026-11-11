package com.rabbit.inventario.dto;

import java.math.BigDecimal;

/** Una línea del anuncio de mercadería (ADR-058): producto, cantidad y peso de una unidad. */
public class DatosLineaRecepcionDTO {
    public String producto;
    public int cantidad;
    public BigDecimal pesoKg;

    public DatosLineaRecepcionDTO() {
    }

    public DatosLineaRecepcionDTO(String producto, int cantidad, BigDecimal pesoKg) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.pesoKg = pesoKg;
    }

    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public void setPesoKg(BigDecimal pesoKg) { this.pesoKg = pesoKg; }
}
