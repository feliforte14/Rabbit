package com.rabbit.ruteo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de salida: la hoja de ruta de un pedido. Junta en un solo objeto lo
 * que el repartidor necesita en la calle y lo que el operador sigue en el
 * tablero: de dónde se retira, adónde se entrega y qué cobrar.
 */
public class HojaDeRutaDTO {

    public Long idPedido;
    public String comercio;
    public String estado;
    public String repartidor;
    public String telefonoRepartidor;
    public String productos;
    public int cantidadTotal;
    // Una parada por lugar de retiro: el punto de picking, o cada depósito
    // del que sale mercadería consignada.
    public List<String> retiros;
    public String direccionEntrega;
    // Importe a cobrar al entregar; null si el pedido ya está pagado.
    public BigDecimal cobrarAlEntregar;
    public String actualizado;

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getIdPedido() { return idPedido; }
    public String getComercio() { return comercio; }
    public String getEstado() { return estado; }
    public String getRepartidor() { return repartidor; }
    public String getTelefonoRepartidor() { return telefonoRepartidor; }
    public String getProductos() { return productos; }
    public int getCantidadTotal() { return cantidadTotal; }
    public List<String> getRetiros() { return retiros; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public BigDecimal getCobrarAlEntregar() { return cobrarAlEntregar; }
    public String getActualizado() { return actualizado; }
}
