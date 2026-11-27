package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: cómo calificó quien recibió el pedido la entrega (ADR-052).
 * De 1 a 5 estrellas y un comentario opcional. Una por pedido: idPedido es
 * único, así que ni dos toques seguidos ni dos pestañas pueden dejar dos
 * calificaciones.
 *
 * Por ID de pedido, no por relación JPA (mismo criterio que RemitoEntrega e
 * IntentoEntrega): los listados de pedidos no la traen.
 *
 * No guarda quién calificó: el seguimiento es público y se abre con el código
 * del pedido, sin identidad.
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "calificaciones_entrega")
public class CalificacionEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long idPedido;

    // 1 a 5 (lo valida ReglaDeCalificacion antes de guardar).
    @Column(nullable = false)
    private int estrellas;

    // Opcional. Texto libre que escribe un tercero: se muestra siempre escapado.
    @Column(length = 500)
    private String comentario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    // Lo que la IA dijo del comentario (gateway de IA): sentimiento y tema, de listas cerradas. Quedan en null
    // si no hay comentario o si la IA no estaba disponible: la calificación vale igual.
    @Column(length = 20)
    private String sentimiento;

    @Column(length = 30)
    private String tema;

    public CalificacionEntrega() {}

    public CalificacionEntrega(Long idPedido, int estrellas, String comentario, LocalDateTime fecha) {
        this.idPedido = idPedido;
        this.estrellas = estrellas;
        this.comentario = comentario;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public int getEstrellas() { return estrellas; }
    public String getComentario() { return comentario; }
    public LocalDateTime getFecha() { return fecha; }
    public String getSentimiento() { return sentimiento; }
    public String getTema() { return tema; }

    public void clasificar(String sentimiento, String tema) {
        this.sentimiento = sentimiento;
        this.tema = tema;
    }
}
