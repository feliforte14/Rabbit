package com.rabbit.repartidores.datos.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CAPA DE DATOS — Entidad JPA del componente ServicioDeRepartidores.
 *
 * Los pedidos a cargo se guardan como ID (idPedidoActual y CargaAsignada)
 * y no como relación JPA: Pedidos y Repartidores son componentes distintos
 * y ninguno navega las entidades del otro, igual que Pedido.idComercio.
 *
 * Capacidad del vehículo (ADR-032): con kilos y/o bultos cargados, lleva
 * varios pedidos mientras le entren (ver CapacidadRepartidor); sin
 * ninguno de los dos, uno a la vez.
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

    // null mientras está DISPONIBLE. Con varios pedidos, el último que se
    // le asignó; la lista completa está en CargaAsignada.
    private Long idPedidoActual;

    // Lo que soporta el vehículo. Null: sin ese límite. Los dos null: un
    // pedido a la vez.
    @Column(precision = 8, scale = 2)
    private BigDecimal capacidadKg;

    private Integer capacidadBultos;

    // Última ubicación que mandó su celular mientras tenía entregas en
    // curso (ADR-035). Solo la última: no se guarda el recorrido.
    private Double latitud;
    private Double longitud;
    private LocalDateTime fechaUbicacion;

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
    public BigDecimal getCapacidadKg() { return capacidadKg; }
    public void setCapacidadKg(BigDecimal capacidadKg) { this.capacidadKg = capacidadKg; }
    public Integer getCapacidadBultos() { return capacidadBultos; }
    public void setCapacidadBultos(Integer capacidadBultos) { this.capacidadBultos = capacidadBultos; }
    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }
    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
    public LocalDateTime getFechaUbicacion() { return fechaUbicacion; }
    public void setFechaUbicacion(LocalDateTime fechaUbicacion) { this.fechaUbicacion = fechaUbicacion; }
    public Long getIdZona() { return idZona; }
    public void setIdZona(Long idZona) { this.idZona = idZona; }
}
