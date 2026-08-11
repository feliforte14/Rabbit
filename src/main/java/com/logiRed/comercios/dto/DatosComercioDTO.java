package com.logiRed.comercios.dto;

/**
 * DTO de entrada para registrar un comercio nuevo.
 * Contiene los datos que el cliente envía en el body del POST.
 * No incluye el ID porque todavía no existe — lo asigna la BD.
 */

public class DatosComercioDTO {
    public String nombre;
    public String razonSocial;
    public String cuit;
    public String email;
    public String telefono;
}
