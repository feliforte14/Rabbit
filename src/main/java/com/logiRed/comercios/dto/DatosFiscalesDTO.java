package com.logiRed.comercios.dto;

/**
 * DTO de entrada para actualizar los datos fiscales de un comercio existente.
 * Contiene solo los campos modificables fiscalmente — no incluye nombre ni ID.
 * Se usa en el PUT /comercios/{id}/datos-fiscales.
 */

public class DatosFiscalesDTO {
    public String razonSocial;
    public String cuit;
    public String email;
    public String telefono;
}
