package com.rabbit.pedidos.presentacion.rest;

/**
 * CAPA DE PRESENTACIÓN — API REST pública de seguimiento de un pedido
 * (API de consumo externo: la puede usar el cliente final o la web del
 * comercio para mostrar "tu pedido está en camino").
 *
 * GET /api/v1/seguimiento/{codigoSeguimiento}
 *   → {"codigoSeguimiento", "estado"}
 *
 * @PermitAll a propósito, sin autenticación: solo expone el estado del
 * pedido, nada de importes, cobros, direcciones ni datos del comercio. Es
 * la única operación pública del sistema (ver docs/seguridad/SEGURIDAD.md).
 *
 * Se entra por el código de seguimiento aleatorio (RB-XXXXXXXXXX), no por
 * el ID: con IDs secuenciales cualquiera podía recorrer el estado de todos
 * los pedidos de Rabbit. El código lo recibe el ERP del comercio (GET
 * /api/v1/pedidos-externos/{id}) para pasárselo a su cliente.
 */

import com.rabbit.infraestructura.Problema;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.PedidoNoEncontradoException;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("v1/seguimiento")
@Produces(MediaType.APPLICATION_JSON)
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
@PermitAll
public class SeguimientoResource {

    @Inject
    private ISeguimientoPedido seguimiento;

    @GET
    @Path("{codigo}")
    public Response consultar(@PathParam("codigo") String codigo) {
        try {
            PedidoDTO pedido = seguimiento.consultarSeguimiento(codigo);
            return Response.ok(Json.createObjectBuilder()
                    .add("codigoSeguimiento", pedido.codigoSeguimiento)
                    .add("estado", pedido.estado)
                    .build()).build();
        } catch (PedidoNoEncontradoException e) {
            return Problema.de(Response.Status.NOT_FOUND, "pedido-inexistente", "Pedido inexistente",
                    "No hay ningún pedido con el código " + codigo).respuesta();
        }
    }
}
