package com.rabbit.inventario.datos.model;

/**
 * Entidad JPA: la "foto" de un día (ADR-059). Cuántas unidades de cada categoría de tamaño tenía un
 * comercio guardadas en los depósitos de Rabbit ese día. Hoy Rabbit solo sabe cuánto stock hay AHORA; para
 * cobrar por día hace falta saber cuánto hubo cada día. Cuentan las unidades disponibles y las reservadas:
 * una reservada sigue ocupando lugar hasta que sale con el repartidor.
 *
 * Una fila por comercio, día y categoría. idComercio es un Long suelto (Comercio es de otro componente, ADR 001).
 */

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ocupacion_diaria",
       uniqueConstraints = @UniqueConstraint(columnNames = { "idComercio", "fecha", "categoria" }))
public class OcupacionDiaria {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long idComercio;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaDeTamano categoria;

    private long unidades;

    public OcupacionDiaria() {}

    public OcupacionDiaria(Long idComercio, LocalDate fecha, CategoriaDeTamano categoria, long unidades) {
        this.idComercio = idComercio;
        this.fecha = fecha;
        this.categoria = categoria;
        this.unidades = unidades;
    }

    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public LocalDate getFecha() { return fecha; }
    public CategoriaDeTamano getCategoria() { return categoria; }
    public long getUnidades() { return unidades; }
}
