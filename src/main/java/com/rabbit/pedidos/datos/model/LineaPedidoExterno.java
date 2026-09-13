package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: cada instancia es UNA línea de producto+cantidad dentro de
 * un PedidoExterno — un mismo pedido del ERP puede traer varios productos
 * distintos, cada uno con su propia cantidad (ver PedidoExterno.lineas).
 *
 * Con origen STOCK_CONSIGNADO (ver OrigenPedido, a nivel del PedidoExterno
 * dueño): idItem referencia el ItemInventario a pedir. Con PUNTO_PICKING:
 * idItem queda null y producto es una descripción libre, porque Rabbit no
 * gestiona el stock interno del comercio.
 */

import jakarta.persistence.*;

@Entity
@Table(name = "pedidos_externos_lineas")
public class LineaPedidoExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_externo_id")
    private PedidoExterno pedidoExterno;

    // STOCK_CONSIGNADO: item del deposito. PUNTO_PICKING: null.
    private Long idItem;

    // PUNTO_PICKING: descripción libre de qué retirar. STOCK_CONSIGNADO: null.
    private String producto;

    private int cantidad;

    public LineaPedidoExterno() {}

    public Long getId() { return id; }
    public PedidoExterno getPedidoExterno() { return pedidoExterno; }
    public void setPedidoExterno(PedidoExterno pedidoExterno) { this.pedidoExterno = pedidoExterno; }
    public Long getIdItem() { return idItem; }
    public void setIdItem(Long idItem) { this.idItem = idItem; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
