package com.rabbit.pedidos.dto;

/**
 * DTO de salida: representa una fila del mock del ERP tal como se
 * muestra en la vista — deja ver qué está pendiente de sincronizar y qué
 * ya se convirtió en pedido real. Nunca se persiste (ver PedidoExterno).
 */

import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import com.rabbit.pagos.dto.MedioPago;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class PedidoExternoDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    public Long id;
    public Long idComercio;
    public OrigenPedido origen;
    public Long idPuntoPicking;
    public List<LineaPedidoDTO> lineas;
    public BigDecimal importe;
    public MedioPago medioPago;
    public String direccionEntrega;
    public String codigoPostalEntrega;
    public String fechaPedido;
    public boolean sincronizado;
    public String errorSincronizacion;
    public String resultado;
    public Long idPedido;
    // Del pedido real, si ya se generó: los completa PedidoService (este
    // DTO solo ve la fila del ERP).
    public String estadoPedido;
    public String codigoSeguimiento;

    // Convierte una entidad PedidoExterno en un DTO listo para la vista.
    public static PedidoExternoDTO desde(PedidoExterno pe) {
        PedidoExternoDTO dto = new PedidoExternoDTO();
        dto.id = pe.getId();
        dto.idComercio = pe.getIdComercio();
        dto.origen = pe.getOrigen();
        dto.idPuntoPicking = pe.getIdPuntoPicking();
        dto.importe = pe.getImporte();
        dto.medioPago = pe.getMedioPago();
        dto.direccionEntrega = pe.getDireccionEntrega();
        dto.codigoPostalEntrega = pe.getCodigoPostalEntrega();
        dto.lineas = pe.getLineas().stream().map(LineaPedidoDTO::desde).collect(Collectors.toList());
        dto.fechaPedido = pe.getFechaPedido() != null ? pe.getFechaPedido().format(FORMATO) : null;
        dto.sincronizado = pe.isSincronizado();
        dto.errorSincronizacion = pe.getErrorSincronizacion();
        // Los cuatro desenlaces posibles, ya resueltos acá para que la vista
        // no tenga que combinar dos campos en Expression Language.
        if (pe.isCancelado()) {
            dto.resultado = "Cancelado";
        } else if (!pe.isSincronizado()) {
            dto.resultado = "Pendiente";
        } else if (pe.getErrorSincronizacion() == null) {
            dto.resultado = "Sincronizado";
        } else {
            dto.resultado = "Descartado";
        }
        dto.idPedido = pe.getIdPedido();
        return dto;
    }

    // Cantidad total de unidades pedidas, sumando todas las líneas — para
    // no obligar a la vista a iterar solo para mostrar un número.
    public int getCantidadTotal() {
        return lineas.stream().mapToInt(LineaPedidoDTO::getCantidad).sum();
    }

    // Descripción de cada línea para el listado: con STOCK_CONSIGNADO
    // todavía no hay "producto" (recién sale al reservar el ítem al
    // sincronizar, ver PedidoService), así que se muestra el ID del ítem.
    public String getDescripcionLineas() {
        return lineas.stream()
                .map(l -> l.getProducto() != null ? l.getProducto() : "Ítem " + l.getIdItem())
                .collect(Collectors.joining(", "));
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public OrigenPedido getOrigen() { return origen; }
    public Long getIdPuntoPicking() { return idPuntoPicking; }
    public List<LineaPedidoDTO> getLineas() { return lineas; }
    public BigDecimal getImporte() { return importe; }
    public MedioPago getMedioPago() { return medioPago; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public String getCodigoPostalEntrega() { return codigoPostalEntrega; }
    public String getFechaPedido() { return fechaPedido; }
    public boolean isSincronizado() { return sincronizado; }
    public String getErrorSincronizacion() { return errorSincronizacion; }
    public Long getIdPedido() { return idPedido; }
    public String getResultado() { return resultado; }
    public String getEstadoPedido() { return estadoPedido; }
    public String getCodigoSeguimiento() { return codigoSeguimiento; }
}
