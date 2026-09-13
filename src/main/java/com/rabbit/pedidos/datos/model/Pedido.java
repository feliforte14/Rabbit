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

import jakarta.persistence.*;
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

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

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
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
