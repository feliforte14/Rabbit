package com.rabbit.ruteo.dto;

import com.rabbit.pedidos.dto.PedidoDTO;
import java.util.ArrayList;
import java.util.List;

/** Tablero de ruteo: los pedidos pendientes de una zona (o los sin zona). */
public class GrupoZonaDTO {

    // null: el grupo "Sin zona".
    public ZonaDTO zona;
    public String transportista;
    public long repartidoresLibres;
    public List<PedidoDTO> pedidos = new ArrayList<>();

    public ZonaDTO getZona() { return zona; }
    public String getTransportista() { return transportista; }
    public long getRepartidoresLibres() { return repartidoresLibres; }
    public List<PedidoDTO> getPedidos() { return pedidos; }
}
