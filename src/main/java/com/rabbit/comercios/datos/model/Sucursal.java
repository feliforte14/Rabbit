package com.rabbit.comercios.datos.model;

/**
 * Entidad JPA que representa una sucursal de un comercio.
 * Cada sucursal pertenece a un comercio (relación ManyToOne — lado dueño
 * de la relación, ver Comercio.sucursales para el lado inverso).
 * Se mapea a la tabla "sucursales" en la BD.
 */

import jakarta.persistence.*;

@Entity
@Table(name = "sucursales")
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nombre;
    private String direccion;

    // Baja lógica: false = dada de baja, sigue en la BD pero no opera.
    // No puede quedar activa=true si el comercio dueño está inactivo
    // (ver ComercioService.darDeBajaComercio / reactivarSucursal).
    private boolean activa;

    // Lado dueño de la relación ManyToOne/OneToMany: acá vive físicamente
    // la clave foránea comercio_id en la tabla "sucursales". fetch = LAZY
    // evita traer el Comercio completo cada vez que se carga una Sucursal
    // (solo se dispara la consulta si se llama a getComercio()).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comercio_id")
    private Comercio comercio;

    public Sucursal() {}

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
}
