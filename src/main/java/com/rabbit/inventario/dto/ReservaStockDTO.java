package com.rabbit.inventario.dto;

/**
 * DTO de salida: representa una reserva de stock tal como se muestra en la
 * vista JSF. Nunca se persiste (ver ReservaStock para la entidad).
 *
 * Incluye segundosRestantes ya calculado, para que la vista no tenga que
 * hacer aritmetica de fechas en Expression Language.
 */

import com.rabbit.inventario.datos.model.ReservaStock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ReservaStockDTO {

    public Long id;
    public String producto;
    public int cantidad;
    public Long idComercio;
    public String estado;
    public String fechaExpiracion;
    public boolean vigente;
    public long segundosRestantes;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static ReservaStockDTO desde(ReservaStock r) {
        ReservaStockDTO dto = new ReservaStockDTO();
        dto.id = r.getId();
        dto.producto = r.getProducto();
        dto.cantidad = r.getCantidad();
        dto.idComercio = r.getIdComercio();
        dto.estado = r.getEstado() != null ? r.getEstado().name() : null;
        dto.vigente = r.estaVigente();

        if (r.getFechaExpiracion() != null) {
            dto.fechaExpiracion = r.getFechaExpiracion().format(HORA);
            long segundos = Duration.between(LocalDateTime.now(), r.getFechaExpiracion()).getSeconds();
            dto.segundosRestantes = Math.max(segundos, 0);
        }
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF)
    public Long getId() { return id; }
    public String getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
    public Long getIdComercio() { return idComercio; }
    public String getEstado() { return estado; }
    public String getFechaExpiracion() { return fechaExpiracion; }
    public boolean isVigente() { return vigente; }
    public long getSegundosRestantes() { return segundosRestantes; }
}
