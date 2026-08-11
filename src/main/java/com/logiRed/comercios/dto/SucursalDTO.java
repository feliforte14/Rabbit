package com.logiRed.comercios.dto;

/**
 * DTO de Sucursal.
 * Representa los datos de una sucursal tal como se exponen en la API.
 * Se usa dentro de ComercioDTO para listar las sucursales de un comercio.
 */

import com.logiRed.comercios.model.Sucursal;

public class SucursalDTO {

    public Long id;
    public String nombre;
    public String direccion;
    public boolean activa;

    // Convierte una entidad Sucursal en un DTO para la respuesta JSON
    public static SucursalDTO desde(Sucursal s) {
        SucursalDTO dto = new SucursalDTO();
        dto.id = s.getId();
        dto.nombre = s.getNombre();
        dto.direccion = s.getDireccion();
        dto.activa = s.isActiva();
        return dto;
    }
}
