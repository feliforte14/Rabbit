package com.rabbit.pagos.datos.model;

/**
 * Entidad JPA: lo que Rabbit le cobra a un comercio por un mes de servicio
 * (ADR-034): un abono fijo, un cargo por cada pedido entregado y otro por
 * kilo entregado. Es aparte del cobro de cada pedido (Cobro), que es la
 * venta del comercio a su cliente.
 *
 * Una por comercio y por mes. Los importes quedan guardados con la tarifa
 * del momento: si después cambia la tarifa, la liquidación ya hecha no.
 */

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "liquidaciones",
       uniqueConstraints = @UniqueConstraint(columnNames = { "idComercio", "periodo" }))
public class Liquidacion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long idComercio;

    // "2026-09": el mes liquidado.
    @Column(nullable = false, length = 7)
    private String periodo;

    private int cantidadEnvios;

    @Column(precision = 12, scale = 3)
    private BigDecimal kilos;

    @Column(precision = 12, scale = 2)
    private BigDecimal abono;

    @Column(precision = 12, scale = 2)
    private BigDecimal cargoEnvios;

    @Column(precision = 12, scale = 2)
    private BigDecimal cargoKilos;

    // Almacenamiento (ADR-059): unidades-día de cada categoría de tamaño y lo que se cobró por ellas, con
    // los importes del momento (una liquidación ya hecha no cambia si después cambia la tarifa). Null en las
    // anteriores: se leen como 0.
    private Long unidadesDiaPequeno;
    private Long unidadesDiaMediano;
    private Long unidadesDiaGrande;

    @Column(precision = 12, scale = 2)
    private BigDecimal cargoAlmacenamiento;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private EstadoLiquidacion estado;

    // Por qué la rechazó el banco, o por qué sigue pendiente.
    @Column(length = 300)
    private String motivo;

    private String codigoAutorizacion;

    private int intentos;

    private LocalDateTime fechaGeneracion;
    private LocalDateTime fechaCobro;

    public Liquidacion() {}

    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public int getCantidadEnvios() { return cantidadEnvios; }
    public void setCantidadEnvios(int cantidadEnvios) { this.cantidadEnvios = cantidadEnvios; }
    public BigDecimal getKilos() { return kilos; }
    public void setKilos(BigDecimal kilos) { this.kilos = kilos; }
    public BigDecimal getAbono() { return abono; }
    public void setAbono(BigDecimal abono) { this.abono = abono; }
    public BigDecimal getCargoEnvios() { return cargoEnvios; }
    public void setCargoEnvios(BigDecimal cargoEnvios) { this.cargoEnvios = cargoEnvios; }
    public BigDecimal getCargoKilos() { return cargoKilos; }
    public void setCargoKilos(BigDecimal cargoKilos) { this.cargoKilos = cargoKilos; }
    public long getUnidadesDiaPequeno() { return unidadesDiaPequeno == null ? 0 : unidadesDiaPequeno; }
    public long getUnidadesDiaMediano() { return unidadesDiaMediano == null ? 0 : unidadesDiaMediano; }
    public long getUnidadesDiaGrande() { return unidadesDiaGrande == null ? 0 : unidadesDiaGrande; }
    public void setUnidadesDia(long pequeno, long mediano, long grande) {
        this.unidadesDiaPequeno = pequeno;
        this.unidadesDiaMediano = mediano;
        this.unidadesDiaGrande = grande;
    }
    public BigDecimal getCargoAlmacenamiento() { return cargoAlmacenamiento == null ? BigDecimal.ZERO : cargoAlmacenamiento; }
    public void setCargoAlmacenamiento(BigDecimal cargoAlmacenamiento) { this.cargoAlmacenamiento = cargoAlmacenamiento; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public EstadoLiquidacion getEstado() { return estado; }
    public void setEstado(EstadoLiquidacion estado) { this.estado = estado; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public void setCodigoAutorizacion(String codigoAutorizacion) { this.codigoAutorizacion = codigoAutorizacion; }
    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }
    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }
    public void setFechaGeneracion(LocalDateTime fechaGeneracion) { this.fechaGeneracion = fechaGeneracion; }
    public LocalDateTime getFechaCobro() { return fechaCobro; }
    public void setFechaCobro(LocalDateTime fechaCobro) { this.fechaCobro = fechaCobro; }
}
