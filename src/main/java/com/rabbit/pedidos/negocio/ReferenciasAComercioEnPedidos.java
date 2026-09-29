package com.rabbit.pedidos.negocio;

/**
 * Impide eliminar un comercio que tiene pedidos o pedidos del ERP: quedarían
 * apuntando a un comercio inexistente (ver EliminacionDeComercio).
 */

import com.rabbit.comercios.negocio.EliminacionDeComercio;
import com.rabbit.pedidos.datos.PedidoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReferenciasAComercioEnPedidos {

    @Inject
    private PedidoRepository repository;

    public void alEliminarComercio(@Observes EliminacionDeComercio evento) {
        long pedidos = repository.contarPedidosDeComercio(evento.getIdComercio());
        long externos = repository.contarPedidosExternosDeComercio(evento.getIdComercio());
        if (pedidos > 0) {
            evento.impedir(pedidos + " pedido(s)");
        }
        if (externos > 0) {
            evento.impedir(externos + " pedido(s) recibido(s) del ERP");
        }
    }
}
