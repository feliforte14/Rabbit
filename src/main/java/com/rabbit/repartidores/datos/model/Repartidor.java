package com.rabbit.repartidores.datos.model;

import jakarta.persistence.*;

/**
 * CAPA DE DATOS — Entidad JPA del componente ServicioDeRepartidores.
 *
 * El pedido a cargo se guarda como ID (idPedidoActual) y no como relación
 * JPA: Pedidos y Repartidores son componentes distintos y ninguno navega
 * las entidades del otro, igual que Pedido.idComercio.
 */
@Entity
@Table(name = "repartidores")
public class Repartidor {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nombre;

    private String telefono;

    @Enumerated(EnumType.STRING)
    private EstadoRepartidor estado;

    // null mientras está DISPONIBLE.
    private Long idPedidoActual;

    // Zona de reparto (del componente Ruteo, por ID). Null: sin zona fija.
    private Long idZona;

    public Repartidor() {}

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public EstadoRepartidor getEstado() { return estado; }
    public void setEstado(EstadoRepartidor estado) { this.estado = estado; }
    public Long getIdPedidoActual() { return idPedidoActual; }
    public void setIdPedidoActual(Long idPedidoActual) { this.idPedidoActual = idPedidoActual; }
    public Long getIdZona() { return idZona; }
    public void setIdZona(Long idZona) { this.idZona = idZona; }
}
