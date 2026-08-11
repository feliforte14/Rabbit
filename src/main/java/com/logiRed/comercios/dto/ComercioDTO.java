package com.logiRed.comercios.dto;

/**
 * CAPA DTO (Data Transfer Object)
 *
 * Los DTOs son objetos que viajan entre capas. Su función es separar
 * lo que se expone al mundo exterior (la API) de lo que existe internamente
 * en la base de datos (el modelo).
 *
 * ComercioDTO representa los datos de un comercio tal como se devuelven
 * en las respuestas HTTP — nunca se persiste en la BD.
 *
 * Ventaja: si mañana cambia la entidad Comercio, la API no cambia,
 * y viceversa. Las capas quedan desacopladas.
 */

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

    // Convierte una entidad Comercio en un DTO listo para enviar como respuesta JSON
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
