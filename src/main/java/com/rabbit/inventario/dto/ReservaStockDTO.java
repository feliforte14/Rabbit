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
    public Long idItem;
    public Long idDeposito;
    public String estado;
    public String fechaExpiracion;
    public String fechaCreacion;
    public String fechaCierre;
    public boolean vigente;
    public long segundosRestantes;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss");

    public static ReservaStockDTO desde(ReservaStock r) {
        ReservaStockDTO dto = new ReservaStockDTO();
        dto.id = r.getId();
        dto.producto = r.getProducto();
        dto.cantidad = r.getCantidad();
        dto.idComercio = r.getIdComercio();
        dto.estado = r.getEstado() != null ? r.getEstado().name() : null;
        dto.vigente = r.estaVigente();

        // El item es LAZY: se toca solo dentro de la transaccion de lectura
        // que armo esta lista. Se copia el ID acá para que la vista nunca
        // tenga que navegar la relacion (ver README, model vs dto).
        if (r.getItem() != null) {
            dto.idItem = r.getItem().getId();
            dto.idDeposito = r.getItem().getDeposito() != null ? r.getItem().getDeposito().getId() : null;
        }

        if (r.getFechaCreacion() != null) {
            dto.fechaCreacion = r.getFechaCreacion().format(FECHA_HORA);
        }
        if (r.getFechaCierre() != null) {
            dto.fechaCierre = r.getFechaCierre().format(FECHA_HORA);
        }

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
    public Long getIdItem() { return idItem; }
    public Long getIdDeposito() { return idDeposito; }
    public String getEstado() { return estado; }
    public String getFechaExpiracion() { return fechaExpiracion; }
    public String getFechaCreacion() { return fechaCreacion; }
    public String getFechaCierre() { return fechaCierre; }
    public boolean isVigente() { return vigente; }
    public long getSegundosRestantes() { return segundosRestantes; }
}
