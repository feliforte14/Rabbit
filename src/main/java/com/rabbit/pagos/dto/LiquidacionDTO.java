package com.rabbit.pagos.dto;

import com.rabbit.pagos.datos.model.Liquidacion;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * DTO de salida: una liquidación mensual (ADR-034), guardada o calculada
 * para la vista previa (id null, estado "VISTA_PREVIA").
 */
public class LiquidacionDTO {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public Long id;
    public Long idComercio;
    public String periodo;
    public int cantidadEnvios;
    public BigDecimal kilos;
    public BigDecimal abono;
    public BigDecimal cargoEnvios;
    public BigDecimal cargoKilos;
    public BigDecimal total;
    public String estado;
    public String motivo;
    public String codigoAutorizacion;
    public int intentos;
    public String fechaCobro;

    public static LiquidacionDTO desde(Liquidacion l) {
        LiquidacionDTO dto = new LiquidacionDTO();
        dto.id = l.getId();
        dto.idComercio = l.getIdComercio();
        dto.periodo = l.getPeriodo();
        dto.cantidadEnvios = l.getCantidadEnvios();
        dto.kilos = l.getKilos();
        dto.abono = l.getAbono();
        dto.cargoEnvios = l.getCargoEnvios();
        dto.cargoKilos = l.getCargoKilos();
        dto.total = l.getTotal();
        dto.estado = l.getEstado() != null ? l.getEstado().name() : null;
        dto.motivo = l.getMotivo();
        dto.codigoAutorizacion = l.getCodigoAutorizacion();
        dto.intentos = l.getIntentos();
        dto.fechaCobro = l.getFechaCobro() != null ? l.getFechaCobro().format(FORMATO) : null;
        return dto;
    }

    public Long getId() { return id; }
    public Long getIdComercio() { return idComercio; }
    public String getPeriodo() { return periodo; }
    public int getCantidadEnvios() { return cantidadEnvios; }
    public BigDecimal getKilos() { return kilos; }
    public BigDecimal getAbono() { return abono; }
    public BigDecimal getCargoEnvios() { return cargoEnvios; }
    public BigDecimal getCargoKilos() { return cargoKilos; }
    public BigDecimal getTotal() { return total; }
    public String getEstado() { return estado; }
    public String getMotivo() { return motivo; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public int getIntentos() { return intentos; }
    public String getFechaCobro() { return fechaCobro; }
}
