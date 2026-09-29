package com.rabbit.ruteo.dto;

import com.rabbit.ruteo.datos.model.CoberturaZona;

/** DTO de entrada: alta de una zona de reparto. */
public class DatosZonaDTO {

    public String nombre;
    public Integer codigoPostalDesde;
    public Integer codigoPostalHasta;
    public CoberturaZona cobertura = CoberturaZona.PROPIA;
    public Long idTransportista;

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Integer getCodigoPostalDesde() { return codigoPostalDesde; }
    public void setCodigoPostalDesde(Integer codigoPostalDesde) { this.codigoPostalDesde = codigoPostalDesde; }
    public Integer getCodigoPostalHasta() { return codigoPostalHasta; }
    public void setCodigoPostalHasta(Integer codigoPostalHasta) { this.codigoPostalHasta = codigoPostalHasta; }
    public CoberturaZona getCobertura() { return cobertura; }
    public void setCobertura(CoberturaZona cobertura) { this.cobertura = cobertura; }
    public Long getIdTransportista() { return idTransportista; }
    public void setIdTransportista(Long idTransportista) { this.idTransportista = idTransportista; }
}
