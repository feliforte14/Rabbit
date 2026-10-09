package com.rabbit.inventario.dto;

/**
 * DTO de salida: representa un depósito tal como se muestra en la vista
 * JSF. Nunca se persiste en la BD (ver Deposito para la entidad).
 */

import com.rabbit.inventario.datos.model.Deposito;

public class DepositoDTO {

    public Long id;
    public String nombre;
    public String direccion;
    public String provincia;
    public String localidad;
    public String codigoPostal;

    // Convierte una entidad Deposito en un DTO listo para mostrar en la vista.
    public static DepositoDTO desde(Deposito d) {
        DepositoDTO dto = new DepositoDTO();
        dto.id = d.getId();
        dto.nombre = d.getNombre();
        dto.direccion = d.getDireccion();
        dto.provincia = d.getProvincia();
        dto.localidad = d.getLocalidad();
        dto.codigoPostal = d.getCodigoPostal();
        return dto;
    }

    /** Nombre para mostrar: "Depósito Sur" (o "deposito sur") ya dice qué es, no se le antepone otro "Depósito". */
    public String getEtiqueta() {
        if (nombre == null || nombre.isBlank()) {
            return "Depósito";
        }
        String normalizado = nombre.trim().toLowerCase().replace('ó', 'o');
        return normalizado.startsWith("deposito") ? nombre.trim() : "Depósito " + nombre.trim();
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getProvincia() { return provincia; }
    public String getLocalidad() { return localidad; }
    public String getCodigoPostal() { return codigoPostal; }
}
