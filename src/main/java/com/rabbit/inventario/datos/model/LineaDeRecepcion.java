package com.rabbit.inventario.datos.model;

/**
 * Una línea de una orden de recepción (ADR-058): un producto con la cantidad que el comercio anunció
 * y, una vez registrada la recepción, la que llegó. pesoKg es el de UNA unidad: hace falta para cargar
 * el producto al stock si todavía no existe (ver ItemInventario.pesoKg).
 */

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "lineas_recepcion")
public class LineaDeRecepcion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id")
    private OrdenDeRecepcion orden;

    @Column(nullable = false, length = 100)
    private String producto;

    private int cantidadEsperada;

    // Null hasta que se registra la recepción.
    private Integer cantidadRecibida;

    @Column(precision = 8, scale = 3)
    private BigDecimal pesoKg;

    public LineaDeRecepcion() {}

    public Long getId() { return id; }
    public OrdenDeRecepcion getOrden() { return orden; }
    public void setOrden(OrdenDeRecepcion orden) { this.orden = orden; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidadEsperada() { return cantidadEsperada; }
    public void setCantidadEsperada(int cantidadEsperada) { this.cantidadEsperada = cantidadEsperada; }
    public Integer getCantidadRecibida() { return cantidadRecibida; }
    public void setCantidadRecibida(Integer cantidadRecibida) { this.cantidadRecibida = cantidadRecibida; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public void setPesoKg(BigDecimal pesoKg) { this.pesoKg = pesoKg; }
}
