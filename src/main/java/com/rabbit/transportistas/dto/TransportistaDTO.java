package com.rabbit.transportistas.dto;

import com.rabbit.transportistas.datos.model.Transportista;

/** DTO de salida: un transportista tal como lo muestra la vista. */
public class TransportistaDTO {

    public Long id;
    public String nombre;
    public String tipoIntegracion;
    public String endpoint;
    public boolean activo;
    // Solo si tiene clave, nunca la clave: se muestra una única vez, al generarla.
    public boolean tieneClaveWebhook;

    public static TransportistaDTO desde(Transportista t) {
        TransportistaDTO dto = new TransportistaDTO();
        dto.id = t.getId();
        dto.nombre = t.getNombre();
        dto.tipoIntegracion = t.getTipoIntegracion() != null ? t.getTipoIntegracion().name() : null;
        dto.endpoint = t.getEndpoint();
        dto.activo = t.isActivo();
        dto.tieneClaveWebhook = t.getClaveWebhook() != null;
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipoIntegracion() { return tipoIntegracion; }
    public String getEndpoint() { return endpoint; }
    public boolean isActivo() { return activo; }
    public boolean isTieneClaveWebhook() { return tieneClaveWebhook; }
}
