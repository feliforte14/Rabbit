package com.rabbit.transportistas.dto;

import com.rabbit.transportistas.datos.model.Envio;
import java.time.format.DateTimeFormatter;

/** DTO de salida: un envío derivado a un transportista. */
public class EnvioDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    public Long id;
    public Long idPedido;
    public Long idComercio;
    public Long idTransportista;
    public String transportista;
    public String codigoSeguimiento;
    public String estado;
    public String solicitado;
    public String actualizado;

    public static EnvioDTO desde(Envio e) {
        EnvioDTO dto = new EnvioDTO();
        dto.id = e.getId();
        dto.idPedido = e.getIdPedido();
        dto.idComercio = e.getIdComercio();
        dto.idTransportista = e.getTransportista().getId();
        dto.transportista = e.getTransportista().getNombre();
        dto.codigoSeguimiento = e.getCodigoSeguimiento();
        dto.estado = e.getEstado() != null ? e.getEstado().name() : null;
        dto.solicitado = e.getFechaSolicitud() != null ? e.getFechaSolicitud().format(FORMATO) : null;
        dto.actualizado = e.getFechaActualizacion() != null ? e.getFechaActualizacion().format(FORMATO) : null;
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF).
    public Long getId() { return id; }
    public Long getIdPedido() { return idPedido; }
    public Long getIdComercio() { return idComercio; }
    public Long getIdTransportista() { return idTransportista; }
    public String getTransportista() { return transportista; }
    public String getCodigoSeguimiento() { return codigoSeguimiento; }
    public String getEstado() { return estado; }
    public String getSolicitado() { return solicitado; }
    public String getActualizado() { return actualizado; }
}
