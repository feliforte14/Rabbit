package com.rabbit.transportistas.dto;

/**
 * DTO de salida: lo que cobraría y tardaría UN transportista por un envío,
 * para comparar antes de derivar (ver IEnvios.cotizarEnvio).
 *
 * estado: COTIZADO (con precio y plazo), RECHAZADO (con motivo), NO_COTIZA
 * (transportista legado, sin esa operación) o NO_DISPONIBLE (no respondió).
 */

import java.math.BigDecimal;

public class CotizacionDTO {

    public Long idTransportista;
    public String transportista;
    public String tipoIntegracion;
    public String estado;
    public BigDecimal precio;
    public Integer plazoHoras;
    public String motivo;

    public boolean isCotizado() { return "COTIZADO".equals(estado); }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getIdTransportista() { return idTransportista; }
    public String getTransportista() { return transportista; }
    public String getTipoIntegracion() { return tipoIntegracion; }
    public String getEstado() { return estado; }
    public BigDecimal getPrecio() { return precio; }
    public Integer getPlazoHoras() { return plazoHoras; }
    public String getMotivo() { return motivo; }
}
