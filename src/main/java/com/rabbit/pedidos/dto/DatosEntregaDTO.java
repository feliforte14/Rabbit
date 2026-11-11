package com.rabbit.pedidos.dto;

/**
 * Lo que acompaña a la foto del remito al registrar una entrega (ADR-055): el código de 4
 * dígitos que le dijo quien recibe y, si el celular lo dio, dónde estaba el repartidor.
 * La ubicación es opcional: sin permiso del navegador la entrega se registra igual, sin ella.
 */
public class DatosEntregaDTO {
    public String codigo;
    public Double latitud;
    public Double longitud;
    public Double precisionMetros;

    public DatosEntregaDTO() {
    }

    public DatosEntregaDTO(String codigo, Double latitud, Double longitud, Double precisionMetros) {
        this.codigo = codigo;
        this.latitud = latitud;
        this.longitud = longitud;
        this.precisionMetros = precisionMetros;
    }
}
