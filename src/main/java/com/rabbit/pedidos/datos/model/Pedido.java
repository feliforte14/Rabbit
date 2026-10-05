package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "pedidos" — el
 * modelo REAL de Rabbit, resultado de sincronizar un PedidoExterno (el
 * mock del ERP del comercio). Ver SincronizadorDePedidos.
 *
 * idComercio es una referencia cross-módulo (Comercios), guardada como
 * Long plano y no como relación JPA — mismo criterio que
 * ReservaStock.idComercio: las entidades no se comparten entre
 * componentes.
 *
 * Un mismo pedido puede comprometer varios productos distintos, cada uno
 * con su propia cantidad — ver lineas (LineaPedido). El origen
 * (STOCK_CONSIGNADO / PUNTO_PICKING) y el punto de picking, si aplica, son
 * del pedido completo: todas sus líneas salen del mismo lugar.
 */

import com.rabbit.pagos.dto.MedioPago;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idComercio;

    @Enumerated(EnumType.STRING)
    private OrigenPedido origen;

    // Con origen PUNTO_PICKING: el punto de picking del que Rabbit va a
    // retirar TODAS las líneas. Con STOCK_CONSIGNADO, null.
    private Long idPuntoPicking;

    // cascade ALL + orphanRemoval: las líneas no tienen sentido sin su
    // pedido dueño, así que su ciclo de vida va pegado al de éste.
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LineaPedido> lineas;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estado;

    // Lo que hay que cobrar y cómo, tal como lo mandó el ERP del comercio
    // (ver PedidoExterno). Lo usa confirmarPedido para registrar el cobro
    // en ServicioDePagosYCobranzas. Nullable: los pedidos anteriores a
    // este cambio no lo tienen.
    @Column(precision = 12, scale = 2)
    private BigDecimal importe;

    @Enumerated(EnumType.STRING)
    private MedioPago medioPago;

    // Adónde se entrega (la manda el ERP con el pedido). Es el destino de
    // la hoja de ruta del repartidor (ver RuteoService). Nullable: los
    // pedidos anteriores a este cambio no la tienen.
    @Column(length = 200)
    private String direccionEntrega;

    // Código postal de entrega (4 dígitos): define la zona del pedido (ver
    // RuteoService). Null si no se pudo determinar: el pedido queda sin zona.
    @Column(length = 4)
    private String codigoPostalEntrega;

    // Repartidor asignado al confirmar (ver IAsignacionRepartidores). Por
    // ID y no por relación JPA: Repartidores es otro componente.
    private Long idRepartidor;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    // Código público para el seguimiento sin login (/api/v1/seguimiento).
    // Aleatorio a propósito: con el ID secuencial cualquiera podía recorrer
    // los estados de todos los pedidos. Null en pedidos anteriores a este
    // cambio (no tienen seguimiento público).
    @Column(length = 16, unique = true)
    private String codigoSeguimiento;

    public Pedido() {}

    // Getters/setters JavaBean estándar de la entidad.
    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public OrigenPedido getOrigen() { return origen; }
    public void setOrigen(OrigenPedido origen) { this.origen = origen; }
    public Long getIdPuntoPicking() { return idPuntoPicking; }
    public void setIdPuntoPicking(Long idPuntoPicking) { this.idPuntoPicking = idPuntoPicking; }
    public List<LineaPedido> getLineas() { return lineas; }
    public void setLineas(List<LineaPedido> lineas) { this.lineas = lineas; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public void setDireccionEntrega(String direccionEntrega) { this.direccionEntrega = direccionEntrega; }
    public String getCodigoPostalEntrega() { return codigoPostalEntrega; }
    public void setCodigoPostalEntrega(String codigoPostalEntrega) { this.codigoPostalEntrega = codigoPostalEntrega; }
    public Long getIdRepartidor() { return idRepartidor; }
    public void setIdRepartidor(Long idRepartidor) { this.idRepartidor = idRepartidor; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
    public String getCodigoSeguimiento() { return codigoSeguimiento; }
    public void setCodigoSeguimiento(String codigoSeguimiento) { this.codigoSeguimiento = codigoSeguimiento; }
}
