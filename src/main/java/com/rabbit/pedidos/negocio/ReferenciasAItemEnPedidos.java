package com.rabbit.pedidos.negocio;

/**
 * Impide eliminar un ítem de stock que usa un pedido en curso o un pedido
 * del ERP sin sincronizar (ver EliminacionDeItem): si el pedido se cancela
 * o vuelve al comercio, el stock tiene que volver a ese ítem. Los pedidos
 * terminados no impiden: guardan el nombre del producto en cada línea.
 */

import com.rabbit.inventario.negocio.EliminacionDeItem;
import com.rabbit.pedidos.datos.PedidoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReferenciasAItemEnPedidos {

    @Inject
    private PedidoRepository repository;

    public void alEliminarItem(@Observes EliminacionDeItem evento) {
        List<Long> enCurso = repository.listarIdsEnCursoConItem(evento.getIdItem());
        if (!enCurso.isEmpty()) {
            evento.impedir((enCurso.size() == 1 ? "lo usa el pedido " : "lo usan los pedidos ")
                    + enCurso.stream().map(id -> "#" + id).collect(Collectors.joining(", "))
                    + ", en curso");
        }
        long externos = repository.contarPedidosExternosPendientesConItem(evento.getIdItem());
        if (externos > 0) {
            evento.impedir(externos == 1 ? "lo pide 1 pedido del ERP que todavía no se procesó"
                    : "lo piden " + externos + " pedidos del ERP que todavía no se procesaron");
        }
    }
}
