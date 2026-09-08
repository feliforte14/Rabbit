package com.rabbit.comercios.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "puntos_picking".
 * Cada punto de picking pertenece a un comercio (relación ManyToOne).
 *
 * Renombrada desde "Sucursal" (ver Sección 1.2 del documento técnico):
 * es una sucursal, depósito o punto de stock que pertenece y administra
 * el COMERCIO, no Rabbit. Rabbit conoce su ubicación para poder coordinar
 * el retiro de mercadería, pero no gestiona ni expone el detalle de su
 * stock interno — esa responsabilidad es exclusiva del comercio. Por eso
 * es una entidad completamente distinta de Deposito (infraestructura
 * propia de Rabbit, ver esa clase): no hay relación entre ambas.
 */

import jakarta.persistence.*;

@Entity
@Table(name = "puntos_picking")
public class PuntoPicking {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nombre;
    private String direccion;

    // Baja lógica: false = dado de baja (sigue en la BD pero no opera).
    // No puede reactivarse si el comercio dueño sigue inactivo
    // (ver ComercioService.darDeBajaComercio / reactivarPuntoPicking).
    private boolean activa;

    // Acá vive la FK comercio_id. fetch LAZY: el Comercio se carga
    // recién si se llama a getComercio(), no en cada consulta.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comercio_id")
    private Comercio comercio;

    public PuntoPicking() {}

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
