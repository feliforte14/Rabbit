package com.rabbit.pagos.datos.model;

import com.rabbit.pagos.dto.MedioPago;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CAPA DE DATOS — Entidad JPA del componente ServicioDePagosYCobranzas.
 *
 * Un cobro por pedido (idPedido único). El pedido se referencia por ID,
 * no por relación JPA: Pagos y Pedidos son componentes distintos.
 */
@Entity
@Table(name = "cobros")
public class Cobro {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long idPedido;

    @Column(precision = 12, scale = 2)
    private BigDecimal importe;

    @Enumerated(EnumType.STRING)
    private MedioPago medioPago;

    @Enumerated(EnumType.STRING)
    private EstadoCobro estado;

    private LocalDateTime fechaCreacion;

    // Cuándo pasó a ACREDITADO (al confirmar si es PREPAGO, al entregar si
    // es CONTRA_ENTREGA). null mientras está PENDIENTE.
    private LocalDateTime fechaAcreditacion;

    public Cobro() {}

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }
    public EstadoCobro getEstado() { return estado; }
    public void setEstado(EstadoCobro estado) { this.estado = estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaAcreditacion() { return fechaAcreditacion; }
    public void setFechaAcreditacion(LocalDateTime fechaAcreditacion) { this.fechaAcreditacion = fechaAcreditacion; }
}
