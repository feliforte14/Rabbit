package com.rabbit.pedidos.negocio;

/**
 * Impide eliminar un comercio que tiene pedidos vivos —en curso o
 * entregados— o pedidos del ERP que todavía esperan sincronizarse: quedarían
 * apuntando a un comercio inexistente (ver EliminacionDeComercio).
 *
 * Los pedidos cancelados (y los del ERP descartados o cancelados) no
 * impiden: son historial terminado y se borran junto con el comercio (ver
 * ComercioEliminado).
 */

import com.rabbit.comercios.negocio.ComercioEliminado;
import com.rabbit.comercios.negocio.EliminacionDeComercio;
import com.rabbit.pedidos.datos.PedidoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class ReferenciasAComercioEnPedidos {

    private static final Logger LOG = Logger.getLogger(ReferenciasAComercioEnPedidos.class.getName());

    @Inject
    private PedidoRepository repository;

    public void alEliminarComercio(@Observes EliminacionDeComercio evento) {
        long pedidos = repository.contarPedidosNoCanceladosDeComercio(evento.getIdComercio());
        long externos = repository.contarPedidosExternosPendientesDeComercio(evento.getIdComercio());
        if (pedidos > 0) {
            evento.impedir(pedidos + " pedido(s) en curso o entregado(s)");
        }
        if (externos > 0) {
            evento.impedir(externos + " pedido(s) del ERP sin sincronizar");
        }
    }

    public void alConfirmarEliminacion(@Observes ComercioEliminado evento) {
        long[] borrados = repository.eliminarPedidosDeComercio(evento.getIdComercio());
        LOG.info("[Pedidos] Comercio " + evento.getIdComercio() + " eliminado: se borraron "
                + borrados[0] + " pedido(s) cancelado(s) y " + borrados[1] + " pedido(s) del ERP");
    }
}
