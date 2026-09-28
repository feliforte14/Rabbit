package com.rabbit.notificaciones.datos.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * CAPA DE DATOS — Entidad JPA del componente ServicioDeNotificaciones: un
 * aviso enviado a un comercio por un cambio de estado de su pedido.
 *
 * estadoPedido se guarda como texto y no como el enum EstadoPedido: es
 * información de otro componente (Pedidos) que llega por el tópico, y
 * Notificaciones no tiene por qué depender de su modelo.
 */
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idPedido;

    private Long idComercio;

    private String estadoPedido;

    // Cuándo cambió el estado en Pedidos (viene en el evento). Sirve para
    // descartar eventos que llegan desordenados (ver NotificacionService).
    private LocalDateTime fechaCambio;

    // Cuándo se generó el aviso.
    private LocalDateTime fechaAviso;

    private String mensaje;

    public Notificacion() {}

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public String getEstadoPedido() { return estadoPedido; }
    public void setEstadoPedido(String estadoPedido) { this.estadoPedido = estadoPedido; }
    public LocalDateTime getFechaCambio() { return fechaCambio; }
    public void setFechaCambio(LocalDateTime fechaCambio) { this.fechaCambio = fechaCambio; }
    public LocalDateTime getFechaAviso() { return fechaAviso; }
    public void setFechaAviso(LocalDateTime fechaAviso) { this.fechaAviso = fechaAviso; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}
