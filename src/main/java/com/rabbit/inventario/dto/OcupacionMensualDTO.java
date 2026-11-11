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

    // Del total de unidades-día de cada tamaño: cuántas fueron gratis (el producto había ingresado hace poco) y
    // cuántas llevaron recargo (stock antiguo, sin movimiento por mucho tiempo).
    public long gratisPequeno;
    public long gratisMediano;
    public long gratisGrande;
    public long recargoPequeno;
    public long recargoMediano;
    public long recargoGrande;

    public OcupacionMensualDTO() {
    }

    public OcupacionMensualDTO(long pequeno, long mediano, long grande, int productosSinMedidas,
                               long gratisPequeno, long gratisMediano, long gratisGrande,
                               long recargoPequeno, long recargoMediano, long recargoGrande) {
        this(pequeno, mediano, grande, productosSinMedidas);
        this.gratisPequeno = gratisPequeno;
        this.gratisMediano = gratisMediano;
        this.gratisGrande = gratisGrande;
        this.recargoPequeno = recargoPequeno;
        this.recargoMediano = recargoMediano;
        this.recargoGrande = recargoGrande;
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
    public long getGratisPequeno() { return gratisPequeno; }
    public long getGratisMediano() { return gratisMediano; }
    public long getGratisGrande() { return gratisGrande; }
    public long getRecargoPequeno() { return recargoPequeno; }
    public long getRecargoMediano() { return recargoMediano; }
    public long getRecargoGrande() { return recargoGrande; }
    public long getTotalGratis() { return gratisPequeno + gratisMediano + gratisGrande; }
    public long getTotalConRecargo() { return recargoPequeno + recargoMediano + recargoGrande; }
}
