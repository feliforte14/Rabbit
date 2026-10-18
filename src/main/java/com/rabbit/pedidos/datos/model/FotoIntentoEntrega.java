package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: una foto de una visita fallida (ADR-045), hasta 3 por
 * visita. Mismo criterio que RemitoEntrega: tabla aparte (la imagen pesa)
 * y en la base compartida, no en el disco de un WildFly.
 *
 * Lleva también el ID del pedido: con eso se decide quién la puede ver
 * (ver PedidoService.obtenerFotoDeVisita) y se borra junto con él.
 */

import jakarta.persistence.*;

@Entity
@Table(name = "fotos_intento_entrega", indexes = @Index(columnList = "idIntento"))
public class FotoIntentoEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long idIntento;

    @Column(nullable = false)
    private Long idPedido;

    // 1, 2 o 3: el orden en que las cargó el repartidor.
    private int orden;

    // image/jpeg, image/png o image/webp (ver Remitos.tipoDeImagen).
    @Column(nullable = false, length = 20)
    private String tipoContenido;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] imagen;

    private int tamanoBytes;

    public FotoIntentoEntrega() {}

    public Long getId() { return id; }
    public Long getIdIntento() { return idIntento; }
    public void setIdIntento(Long idIntento) { this.idIntento = idIntento; }
    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }
    public byte[] getImagen() { return imagen; }
    public void setImagen(byte[] imagen) { this.imagen = imagen; }
    public int getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(int tamanoBytes) { this.tamanoBytes = tamanoBytes; }
}
