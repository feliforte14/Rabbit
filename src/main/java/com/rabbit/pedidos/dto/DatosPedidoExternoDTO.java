package com.rabbit.pedidos.dto;

import com.rabbit.pedidos.datos.model.OrigenPedido;
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

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public OrigenPedido getOrigen() { return origen; }
    public void setOrigen(OrigenPedido origen) { this.origen = origen; }
    public Long getIdPuntoPicking() { return idPuntoPicking; }
    public void setIdPuntoPicking(Long idPuntoPicking) { this.idPuntoPicking = idPuntoPicking; }
    public List<DatosLineaPedidoDTO> getLineas() { return lineas; }
    public void setLineas(List<DatosLineaPedidoDTO> lineas) { this.lineas = lineas; }
}
