package com.rabbit.pedidos.dto;

/** Dónde estaba el repartidor al registrar la entrega (ADR-055), para el personal. */
public class UbicacionEntregaDTO {
    public double latitud;
    public double longitud;
    public Integer precisionMetros;

    public UbicacionEntregaDTO(double latitud, double longitud, Integer precisionMetros) {
        this.latitud = latitud;
        this.longitud = longitud;
        this.precisionMetros = precisionMetros;
    }

    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public Integer getPrecisionMetros() { return precisionMetros; }

    /** "-34.603700, -58.381600 (±12 m)" */
    public String getTexto() {
        return String.format(java.util.Locale.ROOT, "%.6f, %.6f", latitud, longitud)
                + (precisionMetros != null ? " (±" + precisionMetros + " m)" : "");
    }

    /** Enlace para verlo en un mapa. */
    public String getEnlaceMapa() {
        return String.format(java.util.Locale.ROOT, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=18/%.6f/%.6f",
                latitud, longitud, latitud, longitud);
    }
}
