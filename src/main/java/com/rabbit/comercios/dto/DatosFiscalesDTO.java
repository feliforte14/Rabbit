package com.rabbit.comercios.dto;

/**
 * DTO de entrada para actualizar los datos fiscales de un comercio existente.
 * Contiene solo los campos modificables fiscalmente — no incluye nombre ni ID.
 * Lo usa ComercioService.actualizarDatosFiscales(); todavía no tiene una
 * pantalla propia en la vista (queda preparado para cuando se agregue).
 */

public class DatosFiscalesDTO {
    public String razonSocial;
    public String cuit;
    public String email;
    public String telefono;

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
}
