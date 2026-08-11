package com.logiRed.comercios.dto;

import com.logiRed.comercios.model.Comercio;
import java.util.List;
import java.util.stream.Collectors;

public class ComercioDTO {

    public Long id;
    public String nombre;
    public String razonSocial;
    public String cuit;
    public String email;
    public String telefono;
    public boolean activo;
    public List<SucursalDTO> sucursales;

    public static ComercioDTO desde(Comercio c) {
        ComercioDTO dto = new ComercioDTO();
        dto.id = c.getId();
        dto.nombre = c.getNombre();
        dto.razonSocial = c.getRazonSocial();
        dto.cuit = c.getCuit();
        dto.email = c.getEmail();
        dto.telefono = c.getTelefono();
        dto.activo = c.isActivo();
        if (c.getSucursales() != null) {
            dto.sucursales = c.getSucursales().stream()
                    .map(SucursalDTO::desde)
                    .collect(Collectors.toList());
        }
        return dto;
    }
}
