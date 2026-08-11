package com.logiRed.comercios.dto;

import com.logiRed.comercios.model.Sucursal;

public class SucursalDTO {

    public Long id;
    public String nombre;
    public String direccion;
    public boolean activa;

    public static SucursalDTO desde(Sucursal s) {
        SucursalDTO dto = new SucursalDTO();
        dto.id = s.getId();
        dto.nombre = s.getNombre();
        dto.direccion = s.getDireccion();
        dto.activa = s.isActiva();
        return dto;
    }
}
