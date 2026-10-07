package com.rabbit.seguridad.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "usuarios": el
 * perfil de la cuenta en la aplicación (rol, si está activa, a qué
 * comercio o repartidor representa).
 *
 * NO guarda credenciales: la contraseña la valida el ApplicationRealm de
 * WildFly (ver LoginBean y ApplicationRealmSync). Antes se guardaba acá
 * también un hash que nadie usaba para autenticar; una credencial
 * redundante solo suma riesgo si la base se filtra, así que se quitó (ver
 * LimpiezaDeCredenciales).
 */

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    private Rol rol;

    // Baja lógica: false = no puede autenticarse aunque la contraseña sea correcta.
    private boolean activo;

    // A quién representa la cuenta: el comercio de un usuario COMERCIO o el
    // repartidor de un usuario REPARTIDOR. Null para el personal de Rabbit.
    private Long idComercio;
    private Long idRepartidor;

    public Usuario() {}

    // Getters/setters JavaBean estándar de la entidad.
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Long getIdRepartidor() { return idRepartidor; }
    public void setIdRepartidor(Long idRepartidor) { this.idRepartidor = idRepartidor; }
}
