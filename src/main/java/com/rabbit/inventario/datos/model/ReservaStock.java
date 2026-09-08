package com.rabbit.inventario.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "reservas_stock".
 *
 * Representa un "hold" temporal sobre el stock de un item: cantidad
 * comprometida pero todavia NO descontada del deposito, con una fecha de
 * vencimiento. Es el estado conversacional que hace que
 * ServicioDeInventario sea un componente @Stateful.
 *
 * Contabilidad sobre ItemInventario (ver InventarioService):
 *
 *   reservarStock()     -> item.cantidadReservada += cantidad
 *   confirmarReserva()  -> item.cantidadDisponible -= cantidad
 *                          item.cantidadReservada  -= cantidad
 *   liberarReserva()    -> item.cantidadReservada  -= cantidad
 *
 * Es decir: mientras la reserva esta VIGENTE, el stock figura como
 * disponible pero no libre. Recien al confirmar sale del inventario.
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservas_stock")
public class ReservaStock {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // Se guarda desnormalizado (ademas de estar en el item) para que la
    // reserva siga siendo legible como registro historico aunque el item
    // cambie de nombre o se elimine.
    private String producto;

    private int cantidad;

    // Comercio que pidio la reserva. ServicioDeInventario valida contra
    // IConsultaComercios que este activo antes de reservar; se guarda el
    // ID para poder rastrear despues quien comprometio el stock.
    // No es una relacion JPA: Comercio pertenece a OTRO componente y las
    // entidades no se comparten entre componentes.
    private Long idComercio;

    // STRING en vez del ordinal por defecto: si manana se agrega un valor
    // al medio del enum, las filas ya guardadas no cambian de significado.
    @Enumerated(EnumType.STRING)
    private EstadoReserva estado;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaExpiracion;

    // Cuando la reserva salio de VIGENTE, sea por confirmarse, liberarse,
    // vencer o devolverse. Sin esto el estado final se sabe pero no cuando
    // ocurrio, y el historial no se puede ordenar ni filtrar por eso.
    // Null mientras la reserva sigue VIGENTE (y en las filas anteriores a
    // este campo, que ya estaban cerradas cuando se agrego).
    private LocalDateTime fechaCierre;

    // Sobre que producto/deposito puntual pesa la reserva.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private ItemInventario item;

    public ReservaStock() {}

    /**
     * Indica si la reserva sigue comprometiendo stock en este instante.
     *
     * No basta con mirar el estado: una reserva puede seguir marcada como
     * VIGENTE en la base y estar vencida igual, porque nadie la toco desde
     * que paso la fecha. Por eso se compara tambien contra el reloj.
     *
     * @return true si esta VIGENTE y todavia no vencio
     */
    public boolean estaVigente() {
        return estado == EstadoReserva.VIGENTE
                && fechaExpiracion != null
                && LocalDateTime.now().isBefore(fechaExpiracion);
    }

    /**
     * Indica si la reserva quedo vencida sin que nadie la confirmara ni
     * liberara. Es lo que busca el barredor automatico para limpiarlas.
     *
     * @return true si sigue marcada VIGENTE pero ya paso su vencimiento
     */
    public boolean estaVencida() {
        return estado == EstadoReserva.VIGENTE
                && fechaExpiracion != null
                && LocalDateTime.now().isAfter(fechaExpiracion);
    }

    public Long getId() { return id; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public EstadoReserva getEstado() { return estado; }
    public void setEstado(EstadoReserva estado) { this.estado = estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public ItemInventario getItem() { return item; }
    public void setItem(ItemInventario item) { this.item = item; }
}
