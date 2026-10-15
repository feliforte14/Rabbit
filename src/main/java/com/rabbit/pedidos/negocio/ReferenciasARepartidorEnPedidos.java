package com.rabbit.pedidos.negocio;

/**
 * Impide eliminar un repartidor que tiene pedidos asignados sin terminar
 * (confirmados o en camino): se quedarían sin quién los lleve (ver
 * EliminacionDeRepartidor).
 *
 * Los pedidos ya entregados o cancelados no impiden: al eliminarlo se les
 * quita la referencia al repartidor (ver RepartidorEliminado). Quién
 * entregó cada pedido sigue registrado en entregadoPor (ADR-033).
 */

import com.rabbit.pedidos.datos.PedidoRepository;
import com.rabbit.repartidores.negocio.EliminacionDeRepartidor;
import com.rabbit.repartidores.negocio.RepartidorEliminado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class ReferenciasARepartidorEnPedidos {

    private static final Logger LOG = Logger.getLogger(ReferenciasARepartidorEnPedidos.class.getName());

    @Inject
    private PedidoRepository repository;

    public void alEliminarRepartidor(@Observes EliminacionDeRepartidor evento) {
        long enCurso = repository.contarPedidosEnCursoDeRepartidor(evento.getIdRepartidor());
        if (enCurso > 0) {
            evento.impedir(enCurso + " pedido(s) asignado(s) sin entregar");
        }
    }

    public void alConfirmarEliminacion(@Observes RepartidorEliminado evento) {
        int pedidos = repository.desvincularRepartidor(evento.getIdRepartidor());
        LOG.info("[Pedidos] Repartidor " + evento.getIdRepartidor() + " eliminado: "
                + pedidos + " pedido(s) terminados quedaron sin repartidor asignado");
    }
}
