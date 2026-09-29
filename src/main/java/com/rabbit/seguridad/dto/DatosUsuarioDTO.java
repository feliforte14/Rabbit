package com.rabbit.seguridad.dto;

/**
 * DTO de entrada para registrar un usuario nuevo (formulario en usuarios.xhtml).
 */

import com.rabbit.seguridad.datos.model.Rol;

public class DatosUsuarioDTO {
    public String username;
    public String password;
    public Rol rol;
    // Obligatorio según el rol: COMERCIO lleva su comercio, REPARTIDOR su repartidor.
    public Long idComercio;
    public Long idRepartidor;

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Long getIdRepartidor() { return idRepartidor; }
    public void setIdRepartidor(Long idRepartidor) { this.idRepartidor = idRepartidor; }
}
