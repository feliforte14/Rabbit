package com.rabbit.transportistas.datos.model;

/**
 * Entidad JPA: un pedido derivado a un transportista. Guarda el código de
 * seguimiento que devolvió el transportista y el último estado conocido.
 *
 * El pedido y el comercio van por ID (son de otros componentes); el
 * transportista es una relación JPA porque es de este mismo componente.
 * idComercio permite que un COMERCIO vea solo sus envíos sin consultar a
 * Pedidos (mismo criterio que Cobro).
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "envios")
public class Envio {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idPedido;

    private Long idComercio;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transportista_id")
    private Transportista transportista;

    private String codigoSeguimiento;

    @Enumerated(EnumType.STRING)
    private EstadoEnvio estado;

    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaActualizacion;

    public Envio() {}

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Transportista getTransportista() { return transportista; }
    public void setTransportista(Transportista transportista) { this.transportista = transportista; }
    public String getCodigoSeguimiento() { return codigoSeguimiento; }
    public void setCodigoSeguimiento(String codigoSeguimiento) { this.codigoSeguimiento = codigoSeguimiento; }
    public EstadoEnvio getEstado() { return estado; }
    public void setEstado(EstadoEnvio estado) { this.estado = estado; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
