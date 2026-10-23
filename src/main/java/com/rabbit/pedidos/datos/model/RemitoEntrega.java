package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: la foto del remito firmado que prueba una entrega (ADR-033).
 *
 * Tabla aparte de "pedidos" a propósito: la imagen pesa cientos de KB y
 * los listados de pedidos no tienen por qué traerla. Se guarda en la base
 * compartida (bytea) y no en el disco de un WildFly, porque cada
 * integrante levanta el suyo: en disco, la foto solo la vería quien
 * registró la entrega. Un remito por pedido.
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "remitos_entrega")
public class RemitoEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long idPedido;

    // image/jpeg, image/png o image/webp (lo verifica PedidoService por
    // los primeros bytes, no por lo que dice el navegador).
    @Column(nullable = false, length = 20)
    private String tipoContenido;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] imagen;

    private int tamanoBytes;

    private LocalDateTime fechaCarga;

    // Usuario que registró la entrega.
    @Column(length = 30)
    private String cargadoPor;

    // Dónde estaba el repartidor al entregar (ADR-055). Null si el celular no la dio.
    private Double latitud;
    private Double longitud;
    private Integer precisionMetros;

    public RemitoEntrega() {}

    public Double getLatitud() { return latitud; }
    public Double getLongitud() { return longitud; }
    public Integer getPrecisionMetros() { return precisionMetros; }
    public void setUbicacion(Double latitud, Double longitud, Integer precisionMetros) {
        this.latitud = latitud;
        this.longitud = longitud;
        this.precisionMetros = precisionMetros;
    }

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }
    public byte[] getImagen() { return imagen; }
    public void setImagen(byte[] imagen) { this.imagen = imagen; }
    public int getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(int tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public void setFechaCarga(LocalDateTime fechaCarga) { this.fechaCarga = fechaCarga; }
    public String getCargadoPor() { return cargadoPor; }
    public void setCargadoPor(String cargadoPor) { this.cargadoPor = cargadoPor; }
}
