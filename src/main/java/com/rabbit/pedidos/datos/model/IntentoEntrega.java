package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: una vez que el repartidor no pudo entregar un pedido
 * (ADR-045), con la evidencia: motivo, comentario, dónde estaba y las
 * fotos (FotoIntentoEntrega, tabla aparte).
 *
 * El pedido guarda solo el resumen (cuántas visitas fallaron y el motivo
 * de la última, ADR-041); acá queda el historial completo, para que el
 * personal y el comercio puedan ver qué pasó en cada una. Por ID de
 * pedido, no por relación JPA: los listados de pedidos no lo traen.
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "intentos_entrega", indexes = @Index(columnList = "idPedido"))
public class IntentoEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long idPedido;

    // El repartidor que fue (el pedido después se le asigna a otro).
    private Long idRepartidor;

    @Column(length = 30)
    private String registradoPor;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotivoEntregaFallida motivo;

    @Column(length = 500)
    private String comentario;

    // Número de visita fallida que fue (1, 2...), o 0 si no contó como
    // visita (no entra en el vehículo, problema del repartidor...).
    private int numeroVisita;

    // Si con esta visita el pedido volvió al comercio.
    private boolean devueltoAlComercio;

    // Dónde estaba el repartidor. Null si no hubo forma de saberlo.
    private Double latitud;
    private Double longitud;
    // Radio de error que informó el celular, en metros.
    private Integer precisionMetros;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrigenUbicacion origenUbicacion;

    // Cuándo se tomó esa ubicación (con ULTIMA_COMPARTIDA, puede ser antes).
    private LocalDateTime fechaUbicacion;

    private int cantidadFotos;

    public IntentoEntrega() {}

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public Long getIdRepartidor() { return idRepartidor; }
    public void setIdRepartidor(Long idRepartidor) { this.idRepartidor = idRepartidor; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public MotivoEntregaFallida getMotivo() { return motivo; }
    public void setMotivo(MotivoEntregaFallida motivo) { this.motivo = motivo; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public int getNumeroVisita() { return numeroVisita; }
    public void setNumeroVisita(int numeroVisita) { this.numeroVisita = numeroVisita; }
    public boolean isDevueltoAlComercio() { return devueltoAlComercio; }
    public void setDevueltoAlComercio(boolean devueltoAlComercio) { this.devueltoAlComercio = devueltoAlComercio; }
    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }
    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
    public Integer getPrecisionMetros() { return precisionMetros; }
    public void setPrecisionMetros(Integer precisionMetros) { this.precisionMetros = precisionMetros; }
    public OrigenUbicacion getOrigenUbicacion() { return origenUbicacion; }
    public void setOrigenUbicacion(OrigenUbicacion origenUbicacion) { this.origenUbicacion = origenUbicacion; }
    public LocalDateTime getFechaUbicacion() { return fechaUbicacion; }
    public void setFechaUbicacion(LocalDateTime fechaUbicacion) { this.fechaUbicacion = fechaUbicacion; }
    public int getCantidadFotos() { return cantidadFotos; }
    public void setCantidadFotos(int cantidadFotos) { this.cantidadFotos = cantidadFotos; }
}
