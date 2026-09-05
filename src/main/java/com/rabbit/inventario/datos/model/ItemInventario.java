package com.rabbit.inventario.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "items_inventario".
 * Representa el stock de un producto puntual dentro de un depósito.
 *
 * cantidadReservada empieza en 0 y todavía no se usa (el hold de reserva
 * se agrega cuando ServicioDeInventario pase a ser @Stateful). Por ahora
 * solo existe para que el cálculo de disponibilidad ya quede correcto:
 * disponible = cantidadDisponible - cantidadReservada.
 */

import jakarta.persistence.*;

@Entity
@Table(name = "items_inventario")
public class ItemInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String producto;
    private int cantidadDisponible;
    private int cantidadReservada;

    // Acá vive la FK deposito_id. fetch LAZY: el Deposito se carga
    // recién si se llama a getDeposito().
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposito_id")
    private Deposito deposito;

    public ItemInventario() {}

    public Long getId() { return id; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(int cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }
    public int getCantidadReservada() { return cantidadReservada; }
    public void setCantidadReservada(int cantidadReservada) { this.cantidadReservada = cantidadReservada; }
    public Deposito getDeposito() { return deposito; }
    public void setDeposito(Deposito deposito) { this.deposito = deposito; }
}
