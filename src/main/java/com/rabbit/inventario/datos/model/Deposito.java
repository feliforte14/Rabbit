package com.rabbit.inventario.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "depositos".
 * Un depósito puede tener muchos ítems de stock (ver campo items).
 */

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "depositos")
public class Deposito {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nombre;
    private String direccion;

    // La FK vive del lado de ItemInventario (mappedBy = "deposito").
    // fetch LAZY: los items se cargan recién si se piden.
    @OneToMany(mappedBy = "deposito", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ItemInventario> items;

    public Deposito() {}

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public List<ItemInventario> getItems() { return items; }
    public void setItems(List<ItemInventario> items) { this.items = items; }
}
