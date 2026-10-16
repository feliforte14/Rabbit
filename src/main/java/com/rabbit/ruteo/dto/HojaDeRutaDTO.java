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
    // Si el pedido se derivó a un transportista externo, en lugar de repartidor.
    public String transportista;
    public String codigoSeguimiento;
    // Código público del pedido (seguimiento.xhtml): el que el cliente final
    // puede tener a mano. No es el del transportista (codigoSeguimiento).
    public String codigoCliente;
    public String productos;
    public int cantidadTotal;
    // Una parada por lugar de retiro: el punto de picking, o cada depósito
    // del que sale mercadería consignada.
    public List<String> retiros;
    public String direccionEntrega;
    // Para la hoja de ruta recomendada (ver OrdenDeRecorrido): los retiros
    // con su dirección y CP, y el CP de entrega.
    public List<ParadaDTO> paradasRetiro;
    public String codigoPostalEntrega;
    // Recorrido en Google Maps (retiros → entrega); null sin dirección de entrega.
    public String urlMapa;
    // Importe a cobrar al entregar; null si el pedido ya está pagado.
    public BigDecimal cobrarAlEntregar;
    public String actualizado;
    // Kilos del pedido (null si no se conocen) y si tiene foto del remito.
    public BigDecimal pesoKg;
    public boolean conRemito;
    // Quién recibe (ADR-039), para que el repartidor pregunte por esa
    // persona o la llame antes de llegar. Null si el ERP no los mandó.
    public String nombreDestinatario;
    public String telefonoDestinatario;
    // Visitas que ya fallaron y por qué (ADR-041).
    public int intentosFallidos;
    public String motivoUltimoIntento;

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getIdPedido() { return idPedido; }
    public String getComercio() { return comercio; }
    public String getEstado() { return estado; }
    public String getRepartidor() { return repartidor; }
    public String getTelefonoRepartidor() { return telefonoRepartidor; }
    public String getTransportista() { return transportista; }
    public String getCodigoSeguimiento() { return codigoSeguimiento; }
    public String getCodigoCliente() { return codigoCliente; }
    public String getProductos() { return productos; }
    public int getCantidadTotal() { return cantidadTotal; }
    public List<String> getRetiros() { return retiros; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public String getUrlMapa() { return urlMapa; }
    public BigDecimal getCobrarAlEntregar() { return cobrarAlEntregar; }
    public String getActualizado() { return actualizado; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public boolean isConRemito() { return conRemito; }
    public String getNombreDestinatario() { return nombreDestinatario; }
    public String getTelefonoDestinatario() { return telefonoDestinatario; }
    /** El teléfono solo con + y números, para el link tel: del celular. */
    public String getTelefonoParaLlamar() {
        return telefonoDestinatario == null ? null : telefonoDestinatario.replaceAll("[^0-9+]", "");
    }
    public int getIntentosFallidos() { return intentosFallidos; }
    public String getMotivoUltimoIntento() { return motivoUltimoIntento; }
}
