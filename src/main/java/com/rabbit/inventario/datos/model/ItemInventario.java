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

    /**
     * BLOQUEO OPTIMISTA contra la sobreventa por concurrencia.
     *
     * reservarStock lee los contadores, calcula lo libre, decide y escribe.
     * Sin esto, dos sesiones que reservan el mismo item a la vez leen ambas
     * cantidadReservada = 0, ambas pasan el chequeo de stock y ambas
     * escriben: la segunda pisa a la primera y quedan dos reservas VIGENTE
     * contra un contador que solo refleja una. Al confirmarse las dos,
     * cantidadDisponible puede terminar en negativo.
     *
     * Con @Version, Hibernate agrega "AND version = ?" al UPDATE y sube el
     * numero. La segunda transaccion no encuentra la fila que esperaba y
     * falla con OptimisticLockException, que InventarioService traduce a un
     * mensaje entendible en vez de dejar sobrevender.
     *
     * Long y no int: las filas anteriores a esta columna quedan en NULL y
     * un primitivo no las puede cargar. Se rellenan con 0 por migracion.
     */
    @Version
    private Long version;

    // Acá vive la FK deposito_id. fetch LAZY: el Deposito se carga
    // recién si se llama a getDeposito().
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposito_id")
    private Deposito deposito;

    public ItemInventario() {}

    // Getters/setters JavaBean estándar de la entidad.
    public Long getId() { return id; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(int cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }
    public int getCantidadReservada() { return cantidadReservada; }
    public void setCantidadReservada(int cantidadReservada) { this.cantidadReservada = cantidadReservada; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    /** Lo administra Hibernate; no tiene setter a proposito. */
    public Long getVersion() { return version; }
    public Deposito getDeposito() { return deposito; }
    public void setDeposito(Deposito deposito) { this.deposito = deposito; }
}
