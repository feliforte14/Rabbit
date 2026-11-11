package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA de la tabla "eventos_estado_pedido": la bandeja de salida (outbox)
 * de los cambios de estado de un pedido que hay que avisar por el tópico
 * "topico.pedidos.estado".
 *
 * La fila se guarda en la MISMA transacción que el cambio de estado: si el
 * cambio se deshace, la fila también. Después, un envío aparte la publica y
 * le pone publicadoEn. Si el broker estaba caído, la fila queda pendiente
 * (publicadoEn nulo) y un timer la reenvía cuando vuelve: el aviso no se
 * pierde. Es "al menos una vez": un suscriptor puede recibir el mismo
 * evento repetido, y todos lo toleran (ver EnvioDeEstadosPendientes).
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eventos_estado_pedido",
        indexes = @Index(name = "ix_eventos_estado_publicado", columnList = "publicadoEn"))
public class EventoEstadoPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idPedido;

    private Long idComercio;

    @Column(length = 30)
    private String estado;

    // Cuándo cambió el estado (es el que viaja en el mensaje y ordena en los suscriptores).
    private LocalDateTime fechaCambio;

    private LocalDateTime creadoEn;

    // Nulo mientras no se publicó.
    private LocalDateTime publicadoEn;

    // Envíos que fallaron (broker caído, por ejemplo).
    private int intentosFallidos;

    public Long getId() { return id; }

    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }

    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaCambio() { return fechaCambio; }
    public void setFechaCambio(LocalDateTime fechaCambio) { this.fechaCambio = fechaCambio; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getPublicadoEn() { return publicadoEn; }
    public void setPublicadoEn(LocalDateTime publicadoEn) { this.publicadoEn = publicadoEn; }

    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }
}
