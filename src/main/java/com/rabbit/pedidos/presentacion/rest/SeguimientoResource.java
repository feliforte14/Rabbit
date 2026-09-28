package com.rabbit.pedidos.presentacion.rest;

/**
 * CAPA DE PRESENTACIÓN — API REST pública de seguimiento de un pedido
 * (API de consumo externo: la puede usar el cliente final o la web del
 * comercio para mostrar "tu pedido está en camino").
 *
 * GET /api/seguimiento/{idPedido} → {"idPedido", "estado"}
 *
 * @PermitAll a propósito, sin autenticación: solo expone el estado del
 * pedido, nada de importes, cobros ni datos del comercio. Es la única
 * operación pública del sistema (ver docs/SEGURIDAD.md).
 */

import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.ValidacionException;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("seguimiento")
@Produces(MediaType.APPLICATION_JSON)
@Stateless
@PermitAll
public class SeguimientoResource {

    @Inject
    private ISeguimientoPedido seguimiento;

    @GET
    @Path("{idPedido}")
    public Response consultar(@PathParam("idPedido") Long idPedido) {
        try {
            PedidoDTO pedido = seguimiento.consultarEstadoPedido(idPedido);
            return Response.ok(Json.createObjectBuilder()
                    .add("idPedido", pedido.id)
                    .add("estado", pedido.estado)
                    .build()).build();
        } catch (ValidacionException e) {
            return PedidosExternosResource.error(Response.Status.NOT_FOUND, "Pedido no encontrado: " + idPedido);
        }
    }
}
