package com.rabbit.pedidos.datos.model;

/**
 * Entidad JPA: cada instancia es una fila de la tabla "pedidos_externos".
 *
 * MOCK DEL ERP DEL COMERCIO (ver Sección 1.1 y 1.6 del documento técnico).
 * Rabbit no origina pedidos — los consume desde el sistema del comercio.
 * Para esta etapa esa integración se simula con esta tabla propia en vez
 * de una API real: representa lo que en producción vendría de un
 * IERPComercioAdapter. SincronizadorDePedidos (EJB Timer, @Schedule) la
 * barre periódicamente y convierte cada fila no sincronizada en un
 * Pedido real del modelo de Rabbit — la única "alta" de pedido que existe
 * en este alcance es esta sincronización, no un formulario de alta común.
 */

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedidos_externos")
public class PedidoExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idComercio;
    private Long idItem;
    private int cantidad;
    private LocalDateTime fechaPedido;

    // false = todavía no lo tomó el sincronizador. true = ya se procesó y
    // no se vuelve a mirar, sea porque generó su Pedido (errorSincronizacion
    // null) o porque se descartó por una regla de negocio
    // (errorSincronizacion con el motivo).
    private boolean sincronizado;

    // null = sincronizó bien, o todavía no se intentó.
    // Con texto = el sincronizador la descartó por este motivo (comercio
    // dado de baja, sin stock suficiente, ítem inexistente). Se guarda en
    // vez de reintentar para siempre: son fallas de negocio, no baches
    // transitorios, y una fila así se reintentaba cada minuto sin fin.
    // Ver SincronizadorDePedidos.
    @Column(length = 500)
    private String errorSincronizacion;

    public PedidoExterno() {}

    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Long getIdItem() { return idItem; }
    public void setIdItem(Long idItem) { this.idItem = idItem; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public LocalDateTime getFechaPedido() { return fechaPedido; }
    public void setFechaPedido(LocalDateTime fechaPedido) { this.fechaPedido = fechaPedido; }
    public boolean isSincronizado() { return sincronizado; }
    public void setSincronizado(boolean sincronizado) { this.sincronizado = sincronizado; }
    public String getErrorSincronizacion() { return errorSincronizacion; }
    public void setErrorSincronizacion(String errorSincronizacion) { this.errorSincronizacion = errorSincronizacion; }
}
