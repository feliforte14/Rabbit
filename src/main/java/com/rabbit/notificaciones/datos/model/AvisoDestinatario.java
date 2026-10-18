package com.rabbit.notificaciones.datos.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * CAPA DE DATOS — Entidad JPA del componente ServicioDeNotificaciones: un
 * aviso a quien recibe el pedido (ADR-040), por un cambio de estado.
 *
 * Es aparte de Notificacion (los avisos al comercio) porque son otro
 * destinatario con otra regla: solo se manda si el ERP informó el email,
 * y nunca se muestra en el portal del comercio. Queda guardado aunque el
 * mail no salga (servidor de correo sin configurar): así el personal ve
 * qué se le avisó a cada cliente, y sirve para descartar eventos repetidos
 * o desordenados, igual que con los avisos al comercio.
 */
@Entity
@Table(name = "avisos_destinatario")
public class AvisoDestinatario {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idPedido;

    private Long idComercio;

    // Texto y no el enum EstadoPedido: es información de otro componente
    // (mismo criterio que Notificacion.estadoPedido).
    private String estadoPedido;

    // Cuándo cambió el estado en Pedidos (viene en el evento).
    private LocalDateTime fechaCambio;

    private LocalDateTime fechaAviso;

    @Column(length = 120)
    private String email;

    @Column(length = 200)
    private String asunto;

    @Column(length = 1000)
    private String mensaje;

    public AvisoDestinatario() {}

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
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}
