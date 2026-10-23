package com.rabbit.inventario.dto;

/**
 * Unidades-día que ocupó un comercio en un mes, por categoría de tamaño (ADR-059), y cuántos de sus productos
 * todavía no tienen medidas (se cuentan como medianos hasta que se carguen).
 */
public class OcupacionMensualDTO {
    public long pequeno;
    public long mediano;
    public long grande;
    public int productosSinMedidas;

    public OcupacionMensualDTO() {
    }

    public OcupacionMensualDTO(long pequeno, long mediano, long grande, int productosSinMedidas) {
        this.pequeno = pequeno;
        this.mediano = mediano;
        this.grande = grande;
        this.productosSinMedidas = productosSinMedidas;
    }

    public long getPequeno() { return pequeno; }
    public long getMediano() { return mediano; }
    public long getGrande() { return grande; }
    public int getProductosSinMedidas() { return productosSinMedidas; }
    public long getTotal() { return pequeno + mediano + grande; }
}
