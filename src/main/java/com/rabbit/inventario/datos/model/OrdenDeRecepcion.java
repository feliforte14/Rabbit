package com.rabbit.inventario.datos.model;

/**
 * Entidad JPA: una orden de recepción (ADR-058). El comercio la crea antes de mandar la mercadería a un
 * depósito de Rabbit, con producto y cantidad esperados; el personal del depósito registra lo que llegó,
 * línea por línea, y recién ahí se suma al stock lo que efectivamente llegó.
 *
 * idComercio es un Long suelto y no una relación: Comercio es de OTRO componente (ADR 001), como en
 * ItemInventario.idComercio.
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ordenes_recepcion")
public class OrdenDeRecepcion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long idComercio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deposito_id")
    private Deposito deposito;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOrdenDeRecepcion estado = EstadoOrdenDeRecepcion.ANUNCIADA;

    // Aclaración del comercio para el depósito (transporte, fecha estimada...). Texto libre y corto.
    @Column(length = 200)
    private String notas;

    private LocalDateTime fechaAnuncio;
    private LocalDateTime fechaRecepcion;

    // Usuario del personal que registró la recepción.
    @Column(length = 30)
    private String recibidaPor;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<LineaDeRecepcion> lineas = new ArrayList<>();

    public OrdenDeRecepcion() {}

    public void agregarLinea(LineaDeRecepcion linea) {
        linea.setOrden(this);
        lineas.add(linea);
    }

    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Deposito getDeposito() { return deposito; }
    public void setDeposito(Deposito deposito) { this.deposito = deposito; }
    public EstadoOrdenDeRecepcion getEstado() { return estado; }
    public void setEstado(EstadoOrdenDeRecepcion estado) { this.estado = estado; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public LocalDateTime getFechaAnuncio() { return fechaAnuncio; }
    public void setFechaAnuncio(LocalDateTime fechaAnuncio) { this.fechaAnuncio = fechaAnuncio; }
    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
    public void setFechaRecepcion(LocalDateTime fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }
    public String getRecibidaPor() { return recibidaPor; }
    public void setRecibidaPor(String recibidaPor) { this.recibidaPor = recibidaPor; }
    public List<LineaDeRecepcion> getLineas() { return lineas; }
}
