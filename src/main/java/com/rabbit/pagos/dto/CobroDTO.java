package com.rabbit.pagos.dto;

import com.rabbit.pagos.datos.model.Cobro;
import java.math.BigDecimal;

// DTO de salida: el estado del cobro de un pedido, para mostrarlo en la vista.
public class CobroDTO {

    public Long id;
    public Long idPedido;
    public BigDecimal importe;
    public MedioPago medioPago;
    public String estado;
    public String codigoAutorizacion;

    public static CobroDTO desde(Cobro c) {
        CobroDTO dto = new CobroDTO();
        dto.id = c.getId();
        dto.idPedido = c.getIdPedido();
        dto.importe = c.getImporte();
        dto.medioPago = c.getMedioPago();
        dto.estado = c.getEstado() != null ? c.getEstado().name() : null;
        dto.codigoAutorizacion = c.getCodigoAutorizacion();
        return dto;
    }

    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public BigDecimal getImporte() { return importe; }
    public MedioPago getMedioPago() { return medioPago; }
    public String getEstado() { return estado; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
}
