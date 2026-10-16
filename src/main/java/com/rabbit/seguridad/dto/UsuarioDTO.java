package com.rabbit.seguridad.dto;

/**
 * DTO de salida: representa un usuario tal como se muestra en la vista.
 * Nunca incluye el hash de la contraseña: solo si la cuenta ya tiene una
 * contraseña en la base (claveCompartida), para avisar cuáles faltan migrar.
 */

import com.rabbit.seguridad.datos.model.Usuario;

public class UsuarioDTO {

    public Long id;
    public String username;
    public String rol;
    public boolean activo;
    public Long idComercio;
    public Long idRepartidor;
    public boolean claveCompartida;

    // Convierte una entidad Usuario en un DTO listo para mostrar en la
    // vista — a propósito nunca copia el hash.
    public static UsuarioDTO desde(Usuario u) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.id = u.getId();
        dto.username = u.getUsername();
        dto.rol = u.getRol() != null ? u.getRol().name() : null;
        dto.activo = u.isActivo();
        dto.idComercio = u.getIdComercio();
        dto.idRepartidor = u.getIdRepartidor();
        dto.claveCompartida = u.getHashClave() != null;
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public Long getIdComercio() { return idComercio; }
    public Long getIdRepartidor() { return idRepartidor; }
    public boolean isClaveCompartida() { return claveCompartida; }
}
