package com.rabbit.repartidores.datos.model;

/**
 * CAPA DE DATOS — un pedido que lleva un repartidor, con lo que ocupa en
 * su vehículo (ADR-032).
 *
 * Una fila por pedido asignado: se crea al asignarlo y se borra al
 * entregarlo o cancelarlo. Lo que lleva un repartidor es la suma de sus
 * filas. Se guarda el peso y los bultos del momento de la asignación (no
 * se recalculan si después cambia el peso de un ítem), y el pedido va por
 * ID: Pedidos es otro componente.
 */

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "repartidores_cargas")
public class CargaAsignada {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long idRepartidor;

    // Un pedido lo lleva un solo repartidor.
    @Column(nullable = false, unique = true)
    private Long idPedido;

    // Null si el peso del pedido no se conoce: no suma kilos.
    @Column(precision = 10, scale = 3)
    private BigDecimal pesoKg;

    private int bultos;

    private LocalDateTime fechaAsignacion;

    public CargaAsignada() {}

    public Long getId() { return id; }
    public Long getIdRepartidor() { return idRepartidor; }
    public void setIdRepartidor(Long idRepartidor) { this.idRepartidor = idRepartidor; }
    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public void setPesoKg(BigDecimal pesoKg) { this.pesoKg = pesoKg; }
    public int getBultos() { return bultos; }
    public void setBultos(int bultos) { this.bultos = bultos; }
    public LocalDateTime getFechaAsignacion() { return fechaAsignacion; }
    public void setFechaAsignacion(LocalDateTime fechaAsignacion) { this.fechaAsignacion = fechaAsignacion; }
}
