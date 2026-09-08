package com.rabbit.seguridad.dto;

/**
 * DTO de salida: representa un usuario tal como se muestra en la vista.
 * Nunca incluye passwordHash — la vista no tiene por qué verlo ni falta le hace.
 */

import com.rabbit.seguridad.datos.model.Usuario;

public class UsuarioDTO {

    public Long id;
    public String username;
    public String rol;
    public boolean activo;

    public static UsuarioDTO desde(Usuario u) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.id = u.getId();
        dto.username = u.getUsername();
        dto.rol = u.getRol() != null ? u.getRol().name() : null;
        dto.activo = u.isActivo();
        return dto;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getRol() { return rol; }
    public boolean isActivo() { return activo; }
}
