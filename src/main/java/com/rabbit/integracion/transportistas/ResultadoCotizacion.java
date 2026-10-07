package com.rabbit.integracion.transportistas;

/**
 * Resultado de pedirle una cotización a un transportista: cuánto cobraría
 * y en cuánto tiempo entregaría un envío, sin pedirlo todavía.
 *
 * Cuatro desenlaces: cotizó, lo rechazaría (con motivo, por ejemplo por
 * capacidad), no cotiza (un transportista legado no ofrece la operación) o
 * no respondió.
 */

import java.math.BigDecimal;

public final class ResultadoCotizacion {

    public enum Estado { COTIZADO, RECHAZADO, NO_COTIZA, NO_DISPONIBLE }

    private final Estado estado;
    private final BigDecimal precio;
    private final Integer plazoHoras;
    private final String motivo;

    private ResultadoCotizacion(Estado estado, BigDecimal precio, Integer plazoHoras, String motivo) {
        this.estado = estado;
        this.precio = precio;
        this.plazoHoras = plazoHoras;
        this.motivo = motivo;
    }

    public static ResultadoCotizacion cotizado(BigDecimal precio, Integer plazoHoras) {
        return new ResultadoCotizacion(Estado.COTIZADO, precio, plazoHoras, null);
    }

    public static ResultadoCotizacion rechazado(String motivo) {
        return new ResultadoCotizacion(Estado.RECHAZADO, null, null, motivo);
    }

    public static ResultadoCotizacion noCotiza() {
        return new ResultadoCotizacion(Estado.NO_COTIZA, null, null, null);
    }

    public static ResultadoCotizacion noDisponible() {
        return new ResultadoCotizacion(Estado.NO_DISPONIBLE, null, null, null);
    }

    public Estado getEstado() { return estado; }
    public BigDecimal getPrecio() { return precio; }
    public Integer getPlazoHoras() { return plazoHoras; }
    public String getMotivo() { return motivo; }
}
