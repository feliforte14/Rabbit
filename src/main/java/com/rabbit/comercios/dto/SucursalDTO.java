package com.rabbit.comercios.dto;

/**
 * DTO de Sucursal.
 * Representa los datos de una sucursal tal como se muestran en la vista.
 * Se usa dentro de ComercioDTO para listar las sucursales de un comercio.
 */

import com.rabbit.comercios.datos.model.Sucursal;

public class SucursalDTO {

    public Long id;
    public String nombre;
    public String direccion;
    public boolean activa;

    // Convierte una entidad Sucursal en un DTO para mostrar en la vista
    public static SucursalDTO desde(Sucursal s) {
        SucursalDTO dto = new SucursalDTO();
        dto.id = s.getId();
        dto.nombre = s.getNombre();
        dto.direccion = s.getDireccion();
        dto.activa = s.isActiva();
        return dto;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public boolean isActiva() { return activa; }
}
