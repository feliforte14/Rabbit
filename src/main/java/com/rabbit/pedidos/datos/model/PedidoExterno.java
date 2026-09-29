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
 *
 * Un mismo pedido del ERP puede traer varios productos distintos, cada
 * uno con su propia cantidad — ver lineas (LineaPedidoExterno). El origen
 * (STOCK_CONSIGNADO / PUNTO_PICKING) y el punto de picking, si aplica, son
 * del pedido completo: todas sus líneas salen del mismo lugar.
 */

import com.rabbit.pagos.dto.MedioPago;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pedidos_externos")
public class PedidoExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long idComercio;

    @Enumerated(EnumType.STRING)
    private OrigenPedido origen;

    // Con origen PUNTO_PICKING: el punto de picking del propio comercio
    // del que hay que retirar TODAS las líneas. Con STOCK_CONSIGNADO, null.
    private Long idPuntoPicking;

    // cascade ALL + orphanRemoval: las líneas no tienen sentido sin su
    // pedido dueño, así que su ciclo de vida va pegado al de éste (se
    // guardan/borran junto con él, nunca sueltas).
    @OneToMany(mappedBy = "pedidoExterno", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LineaPedidoExterno> lineas;

    private LocalDateTime fechaPedido;

    // El importe y el medio de pago los define el comercio al vender, no
    // Rabbit: Rabbit no tiene precios de los ítems consignados (ItemInventario
    // no referencia a Producto) ni de lo que se retira de un punto de
    // picking. Viajan tal cual al Pedido real al sincronizar.
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

    // Pedido real que generó al sincronizarse (null mientras está pendiente o
    // si se descartó). Permite que el ERP siga el pedido por la API REST.
    private Long idPedido;

    public PedidoExterno() {}

    // Getters/setters JavaBean estándar de la entidad.
    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public OrigenPedido getOrigen() { return origen; }
    public void setOrigen(OrigenPedido origen) { this.origen = origen; }
    public Long getIdPuntoPicking() { return idPuntoPicking; }
    public void setIdPuntoPicking(Long idPuntoPicking) { this.idPuntoPicking = idPuntoPicking; }
    public List<LineaPedidoExterno> getLineas() { return lineas; }
    public void setLineas(List<LineaPedidoExterno> lineas) { this.lineas = lineas; }
    public LocalDateTime getFechaPedido() { return fechaPedido; }
    public void setFechaPedido(LocalDateTime fechaPedido) { this.fechaPedido = fechaPedido; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public void setDireccionEntrega(String direccionEntrega) { this.direccionEntrega = direccionEntrega; }
    public String getCodigoPostalEntrega() { return codigoPostalEntrega; }
    public void setCodigoPostalEntrega(String codigoPostalEntrega) { this.codigoPostalEntrega = codigoPostalEntrega; }
    public boolean isSincronizado() { return sincronizado; }
    public void setSincronizado(boolean sincronizado) { this.sincronizado = sincronizado; }
    public String getErrorSincronizacion() { return errorSincronizacion; }
    public void setErrorSincronizacion(String errorSincronizacion) { this.errorSincronizacion = errorSincronizacion; }
    public Long getIdPedido() { return idPedido; }
    public void setIdPedido(Long idPedido) { this.idPedido = idPedido; }
}
