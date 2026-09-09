package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "pedidos" — el
 * modelo REAL de Rabbit, resultado de sincronizar un PedidoExterno (el
 * mock del ERP del comercio). Ver SincronizadorDePedidos.
 *
 * idComercio e idItem son referencias cross-módulo (Comercios e
 * Inventario respectivamente), guardadas como Long plano y no como
 * relación JPA — mismo criterio que ReservaStock.idComercio: las
 * entidades no se comparten entre componentes.
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idComercio;
    private Long idItem;

    // Denormalizado (igual que ReservaStock.producto): el pedido queda
    // legible como registro histórico aunque el ítem cambie o se elimine.
    private String producto;
    private int cantidad;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estado;

    // Reserva de stock ya CONFIRMADA en ServicioDeInventario que este
    // pedido comprometió al sincronizarse. Se guarda para trazabilidad y
    // para poder revertirla: cancelarPedido() llama a
    // IReservaStock.registrarDevolucion(idReservaStock) y el stock vuelve
    // al disponible.
    private Long idReservaStock;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Pedido() {}

    // Getters/setters JavaBean estándar de la entidad.
    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Long getIdItem() { return idItem; }
    public void setIdItem(Long idItem) { this.idItem = idItem; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public Long getIdReservaStock() { return idReservaStock; }
    public void setIdReservaStock(Long idReservaStock) { this.idReservaStock = idReservaStock; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
