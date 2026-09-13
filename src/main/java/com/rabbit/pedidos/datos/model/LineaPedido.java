package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: cada instancia es UNA línea de producto+cantidad dentro de
 * un Pedido real — un mismo pedido puede comprometer varios productos
 * distintos, cada uno con su propia cantidad (ver Pedido.lineas).
 *
 * Con origen STOCK_CONSIGNADO (ver OrigenPedido, a nivel del Pedido
 * dueño): idItem y idReservaStock reflejan la reserva CONFIRMADA que esta
 * línea comprometió en ServicioDeInventario (ver PedidoService). Con
 * PUNTO_PICKING: ambos quedan null, producto es descriptivo y no hay nada
 * que reservar ni devolver.
 */

import jakarta.persistence.*;

@Entity
@Table(name = "pedidos_lineas")
public class LineaPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    // STOCK_CONSIGNADO: item del deposito comprometido. PUNTO_PICKING: null.
    private Long idItem;

    // Denormalizado (igual que antes en Pedido): queda legible como
    // registro histórico aunque el ítem cambie o se elimine.
    private String producto;

    private int cantidad;

    // Reserva ya CONFIRMADA en ServicioDeInventario que esta línea
    // comprometió al sincronizarse. Solo con origen STOCK_CONSIGNADO — ver
    // PedidoService.cancelarPedido, que la usa para revertirla.
    private Long idReservaStock;

    public LineaPedido() {}

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public Long getIdItem() { return idItem; }
    public void setIdItem(Long idItem) { this.idItem = idItem; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public Long getIdReservaStock() { return idReservaStock; }
    public void setIdReservaStock(Long idReservaStock) { this.idReservaStock = idReservaStock; }
}
