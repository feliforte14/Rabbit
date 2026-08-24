package com.rabbit.comercios.dto;

/**
 * DTO (Data Transfer Object)
 *
 * Los DTOs son objetos simples que viajan entre capas. Sirven para separar
 * lo que se muestra en la vista de lo que existe en la base de datos (la entidad).
 *
 * ComercioDTO representa los datos de un comercio tal como se muestran
 * en la vista JSF — nunca se persiste en la BD.
 *
 * Ventaja: si mañana cambia la entidad Comercio, la vista no se rompe,
 * y viceversa. Las capas quedan desacopladas.
 */

import com.rabbit.comercios.datos.model.Comercio;
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

    // Convierte una entidad Comercio en un DTO listo para mostrar en la vista
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

    // Getters JavaBean: los requiere Expression Language (JSF) para leer
    // estos campos desde las vistas .xhtml (p. ej. #{c.nombre})
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getRazonSocial() { return razonSocial; }
    public String getCuit() { return cuit; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public boolean isActivo() { return activo; }
    public List<SucursalDTO> getSucursales() { return sucursales; }
}
