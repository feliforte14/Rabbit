package com.rabbit.comercios.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "sucursales".
 * Cada sucursal pertenece a un comercio (relación ManyToOne).
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

    // Baja lógica: false = dada de baja (sigue en la BD pero no opera).
    // No puede reactivarse si el comercio dueño sigue inactivo
    // (ver ComercioService.darDeBajaComercio / reactivarSucursal).
    private boolean activa;

    // Acá vive la FK comercio_id. fetch LAZY: el Comercio se carga
    // recién si se llama a getComercio(), no en cada consulta.
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
