package com.rabbit.transportistas.dto;

import com.rabbit.transportistas.datos.model.TipoIntegracion;

/** DTO de entrada: alta de un transportista (formulario de transportistas.xhtml). */
public class DatosTransportistaDTO {

    public String nombre;
    public TipoIntegracion tipoIntegracion = TipoIntegracion.REST;
    public String endpoint;

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public TipoIntegracion getTipoIntegracion() { return tipoIntegracion; }
    public void setTipoIntegracion(TipoIntegracion tipoIntegracion) { this.tipoIntegracion = tipoIntegracion; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
}
