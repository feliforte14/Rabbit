package com.rabbit.ruteo.dto;

import com.rabbit.ruteo.datos.model.Zona;

/** DTO de salida: una zona de reparto. */
public class ZonaDTO {

    public Long id;
    public String nombre;
    public int codigoPostalDesde;
    public int codigoPostalHasta;
    public String cobertura;
    public Long idTransportista;
    public boolean activa;

    public static ZonaDTO desde(Zona z) {
        ZonaDTO dto = new ZonaDTO();
        dto.id = z.getId();
        dto.nombre = z.getNombre();
        dto.codigoPostalDesde = z.getCodigoPostalDesde();
        dto.codigoPostalHasta = z.getCodigoPostalHasta();
        dto.cobertura = z.getCobertura() != null ? z.getCobertura().name() : null;
        dto.idTransportista = z.getIdTransportista();
        dto.activa = z.isActiva();
        return dto;
    }

    public boolean contiene(String codigoPostal) {
        if (codigoPostal == null) {
            return false;
        }
        int cp = Integer.parseInt(codigoPostal);
        return cp >= codigoPostalDesde && cp <= codigoPostalHasta;
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public int getCodigoPostalDesde() { return codigoPostalDesde; }
    public int getCodigoPostalHasta() { return codigoPostalHasta; }
    public String getCobertura() { return cobertura; }
    public Long getIdTransportista() { return idTransportista; }
    public boolean isActiva() { return activa; }
}
