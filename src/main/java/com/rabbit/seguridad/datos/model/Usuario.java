package com.rabbit.seguridad.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "usuarios".
 * Es la fuente de verdad que consulta RabbitIdentityStore para autenticar
 * (Jakarta Security) y para informarle al contenedor a qué rol pertenece
 * el caller — ese rol es lo que evalúan las anotaciones @RolesAllowed.
 *
 * passwordHash guarda un hash (ver PasswordUtil), nunca la contraseña en
 * texto plano.
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

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Rol rol;

    // Baja lógica: false = no puede autenticarse aunque la contraseña sea correcta.
    private boolean activo;

    public Usuario() {}

    // Getters/setters JavaBean estándar de la entidad.
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
