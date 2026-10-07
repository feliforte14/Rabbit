package com.rabbit.pedidos.dto;

import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO de entrada para simular un pedido nuevo "llegando del ERP" del
 * comercio (formulario en pedidos.xhtml). No crea un Pedido directo: crea
 * la fila mock que SincronizadorDePedidos va a levantar en su próxima
 * pasada — ver PedidoExterno.
 *
 * Un pedido puede traer VARIAS líneas de producto+cantidad (ver lineas,
 * DatosLineaPedidoDTO) — el origen y, si aplica, el punto de picking son
 * del pedido completo, no de cada línea.
 */

public class DatosPedidoExternoDTO {
    public Long idComercio;
    public OrigenPedido origen = OrigenPedido.STOCK_CONSIGNADO;
    public Long idPuntoPicking;
    public List<DatosLineaPedidoDTO> lineas = new ArrayList<>();
    // Total de la venta que cerró el comercio y cómo la cobra (ver PedidoExterno).
    public BigDecimal importe;
    public MedioPago medioPago = MedioPago.CONTRA_ENTREGA;
    // Adónde se entrega el pedido.
    public String direccionEntrega;
    // Opcional: si no viene, se busca en la dirección (ver CodigosPostales).
    public String codigoPostalEntrega;

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public OrigenPedido getOrigen() { return origen; }
    public void setOrigen(OrigenPedido origen) { this.origen = origen; }
    public Long getIdPuntoPicking() { return idPuntoPicking; }
    public void setIdPuntoPicking(Long idPuntoPicking) { this.idPuntoPicking = idPuntoPicking; }
    public List<DatosLineaPedidoDTO> getLineas() { return lineas; }
    public void setLineas(List<DatosLineaPedidoDTO> lineas) { this.lineas = lineas; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public void setDireccionEntrega(String direccionEntrega) { this.direccionEntrega = direccionEntrega; }
    public String getCodigoPostalEntrega() { return codigoPostalEntrega; }
    public void setCodigoPostalEntrega(String codigoPostalEntrega) { this.codigoPostalEntrega = codigoPostalEntrega; }
}
