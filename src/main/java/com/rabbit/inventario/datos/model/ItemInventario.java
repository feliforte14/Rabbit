package com.rabbit.inventario.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "items_inventario".
 * Representa el stock de un producto puntual, DE UN COMERCIO PUNTUAL,
 * dentro de un depósito de Rabbit.
 *
 * Contabilidad (ver InventarioService):
 *   cantidadDisponible = lo que hay físicamente en el depósito
 *   cantidadReservada  = lo comprometido por holds vigentes
 *   libre              = cantidadDisponible - cantidadReservada
 *
 * El stock se compromete al reservar (sube cantidadReservada) y recién
 * sale del depósito al confirmar (baja cantidadDisponible).
 */

import jakarta.persistence.*;

@Entity
@Table(name = "items_inventario")
public class ItemInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String producto;
    private int cantidadDisponible;
    private int cantidadReservada;

    // Comercio dueño de esta mercadería consignada. El depósito es de
    // Rabbit (ver Deposito), pero lo que hay adentro sigue siendo del
    // comercio que lo consignó, y solo él puede reservarlo — ver
    // InventarioService.reservarStock.
    //
    // Long suelto y no relación JPA: Comercio pertenece a OTRO componente
    // y las entidades no se comparten entre componentes (ADR 001). Mismo
    // criterio que ReservaStock.idComercio y Pedido.idComercio.
    //
    // Nullable en la base por las filas anteriores a esta regla; el
    // negocio lo exige en las altas nuevas y rechaza reservar sobre un
    // ítem sin dueño asignado.
    private Long idComercio;

    // Acá vive la FK deposito_id. fetch LAZY: el Deposito se carga
    // recién si se llama a getDeposito().
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposito_id")
    private Deposito deposito;

    public ItemInventario() {}

    public Long getId() { return id; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(int cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }
    public int getCantidadReservada() { return cantidadReservada; }
    public void setCantidadReservada(int cantidadReservada) { this.cantidadReservada = cantidadReservada; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Deposito getDeposito() { return deposito; }
    public void setDeposito(Deposito deposito) { this.deposito = deposito; }
}
