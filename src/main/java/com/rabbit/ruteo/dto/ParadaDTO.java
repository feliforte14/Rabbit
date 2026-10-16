package com.rabbit.ruteo.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Una parada del recorrido del repartidor: un lugar de retiro (depósito o
 * punto de picking) o una dirección de entrega. Si varios pedidos van al
 * mismo lugar, es una sola parada con todos esos pedidos.
 */
public class ParadaDTO {

    public enum Tipo { RETIRO, ENTREGA }

    public Tipo tipo;
    // Lo que se muestra ("Depósito Central — Av. Siempre Viva 123, CABA").
    public String descripcion;
    // Lo que se le pasa a Google Maps.
    public String direccion;
    // Para ordenar (ver OrdenDeRecorrido); null si no se conoce.
    public String codigoPostal;
    public List<Long> pedidos = new ArrayList<>();

    public ParadaDTO() {
    }

    public ParadaDTO(Tipo tipo, String descripcion, String direccion, String codigoPostal, Long idPedido) {
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.direccion = direccion;
        this.codigoPostal = codigoPostal;
        this.pedidos.add(idPedido);
    }

    public Tipo getTipo() { return tipo; }
    public boolean isRetiro() { return tipo == Tipo.RETIRO; }
    public String getDescripcion() { return descripcion; }
    public String getDireccion() { return direccion; }
    public String getCodigoPostal() { return codigoPostal; }
    public List<Long> getPedidos() { return pedidos; }

    /** "#12, #15". */
    public String getPedidosTexto() {
        return pedidos.stream().map(id -> "#" + id).collect(Collectors.joining(", "));
    }
}
