package com.rabbit.seguridad.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "usuarios": el
 * perfil de la cuenta en la aplicación (rol, si está activa, a qué
 * comercio o repartidor representa) y el hash bcrypt de su contraseña.
 *
 * El login lo valida WildFly leyendo esta tabla con su realm JDBC
 * (ADR-031, scripts/configurar-realm-compartido.cli): como la base es
 * compartida, una cuenta creada en cualquier máquina entra en todas.
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

    // Hash bcrypt ("$2a$10$...", ver Claves). Null en las cuentas creadas
    // antes de ADR-031: esas siguen entrando solo con el realm de archivos
    // de la máquina donde se crearon, hasta que un administrador les
    // restablezca la contraseña.
    @Column(name = "hash_clave", length = 60)
    private String hashClave;

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
    public String getHashClave() { return hashClave; }
    public void setHashClave(String hashClave) { this.hashClave = hashClave; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Long getIdRepartidor() { return idRepartidor; }
    public void setIdRepartidor(Long idRepartidor) { this.idRepartidor = idRepartidor; }
}
