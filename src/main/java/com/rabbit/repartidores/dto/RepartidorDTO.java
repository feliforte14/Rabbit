package com.rabbit.repartidores.dto;

import com.rabbit.repartidores.datos.model.Repartidor;

// DTO de salida: lo que la vista necesita mostrar de un repartidor.
public class RepartidorDTO {

    public Long id;
    public String nombre;
    public String telefono;
    public String estado;
    public Long idPedidoActual;
    public Long idZona;

    public static RepartidorDTO desde(Repartidor r) {
        RepartidorDTO dto = new RepartidorDTO();
        dto.id = r.getId();
        dto.nombre = r.getNombre();
        dto.telefono = r.getTelefono();
        dto.estado = r.getEstado() != null ? r.getEstado().name() : null;
        dto.idPedidoActual = r.getIdPedidoActual();
        dto.idZona = r.getIdZona();
        return dto;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public String getEstado() { return estado; }
    public Long getIdPedidoActual() { return idPedidoActual; }
    public Long getIdZona() { return idZona; }
}
