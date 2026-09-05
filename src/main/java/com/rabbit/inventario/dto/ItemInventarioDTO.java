package com.rabbit.inventario.dto;

/**
 * DTO de salida: representa un ítem de stock tal como se muestra en la
 * vista JSF. Nunca se persiste en la BD (ver ItemInventario para la entidad).
 */

import com.rabbit.inventario.datos.model.ItemInventario;

public class ItemInventarioDTO {

    public Long id;
    public String producto;
    public int cantidadDisponible;
    public int cantidadReservada;
    public int cantidadLibre;
    public Long idDeposito;

    public static ItemInventarioDTO desde(ItemInventario i) {
        ItemInventarioDTO dto = new ItemInventarioDTO();
        dto.id = i.getId();
        dto.producto = i.getProducto();
        dto.cantidadDisponible = i.getCantidadDisponible();
        dto.cantidadReservada = i.getCantidadReservada();
        dto.cantidadLibre = i.getCantidadDisponible() - i.getCantidadReservada();
        dto.idDeposito = i.getDeposito() != null ? i.getDeposito().getId() : null;
        return dto;
    }

    // Getters JavaBean: los requiere Expression Language (JSF)
    public Long getId() { return id; }
    public String getProducto() { return producto; }
    public int getCantidadDisponible() { return cantidadDisponible; }
    public int getCantidadReservada() { return cantidadReservada; }
    public int getCantidadLibre() { return cantidadLibre; }
    public Long getIdDeposito() { return idDeposito; }
}
