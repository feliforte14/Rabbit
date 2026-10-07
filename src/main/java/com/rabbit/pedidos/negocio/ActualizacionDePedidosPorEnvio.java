package com.rabbit.pedidos.negocio;

/**
 * Mueve un pedido derivado según lo que informa su transportista: EN_TRANSITO
 * lo pasa a EN_CAMINO y ENTREGADO a ENTREGADO (pasando por EN_CAMINO si el
 * seguimiento se salteó ese paso). Observa EstadoEnvioCambiado, que
 * Transportistas dispara dentro de la transacción de la novedad: si mover el
 * pedido falla, se deshace también la novedad y el seguimiento lo reintenta.
 *
 * Usa las mismas operaciones que el personal (despacharPedido,
 * registrarEntrega), así el tópico de estados, los avisos al comercio y el
 * cobro contra entrega funcionan igual que con un repartidor propio.
 */

import com.rabbit.transportistas.negocio.EstadoEnvioCambiado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class ActualizacionDePedidosPorEnvio {

    private static final Logger LOG = Logger.getLogger(ActualizacionDePedidosPorEnvio.class.getName());

    @Inject
    private IGestionPedidos gestion;

    @Inject
    private ISeguimientoPedido seguimiento;

    public void alCambiarEnvio(@Observes EstadoEnvioCambiado evento) {
        Long idPedido = evento.idPedido();
        String estado = seguimiento.consultarEstadoPedido(idPedido).getEstado();
        switch (evento.nuevo()) {
            case EN_TRANSITO:
                if ("CONFIRMADO".equals(estado)) {
                    gestion.despacharPedido(idPedido);
                }
                break;
            case ENTREGADO:
                if ("CONFIRMADO".equals(estado)) {
                    gestion.despacharPedido(idPedido);
                    estado = "EN_CAMINO";
                }
                if ("EN_CAMINO".equals(estado)) {
                    gestion.registrarEntrega(idPedido);
                }
                break;
            case CANCELADO:
                // Un transportista que cancela por su cuenta: el pedido queda
                // como está y lo resuelve el personal (cancelarlo o derivarlo
                // de nuevo no se hace solo).
                LOG.warning("[Pedidos] El transportista canceló el envío del pedido " + idPedido
                        + ": hay que resolverlo a mano");
                break;
            default:
                break;
        }
    }
}
