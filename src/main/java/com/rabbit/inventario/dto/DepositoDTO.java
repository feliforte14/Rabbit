package com.rabbit.inventario.dto;

/**
 * DTO de salida: representa un depósito tal como se muestra en la vista
 * JSF. Nunca se persiste en la BD (ver Deposito para la entidad).
 */

import com.rabbit.inventario.datos.model.Deposito;

public class DepositoDTO {

    public Long id;
    public String nombre;
    public String direccion;

    public static DepositoDTO desde(Deposito d) {
        DepositoDTO dto = new DepositoDTO();
        dto.id = d.getId();
        dto.nombre = d.getNombre();
        dto.direccion = d.getDireccion();
        return dto;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
}
