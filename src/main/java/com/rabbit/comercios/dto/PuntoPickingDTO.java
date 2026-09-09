package com.rabbit.comercios.dto;

/**
 * DTO de PuntoPicking (renombrado desde SucursalDTO).
 * Representa los datos de un punto de picking tal como se muestran en la
 * vista. Se usa dentro de ComercioDTO para listar los puntos de picking
 * de un comercio.
 */

import com.rabbit.comercios.datos.model.PuntoPicking;

public class PuntoPickingDTO {

    public Long id;
    public String nombre;
    public String direccion;
    public boolean activa;

    // Convierte una entidad PuntoPicking en un DTO para mostrar en la vista
    public static PuntoPickingDTO desde(PuntoPicking p) {
        PuntoPickingDTO dto = new PuntoPickingDTO();
        dto.id = p.getId();
        dto.nombre = p.getNombre();
        dto.direccion = p.getDireccion();
        dto.activa = p.isActiva();
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public boolean isActiva() { return activa; }
}
