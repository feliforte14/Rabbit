package com.rabbit.pedidos.presentacion.rest;

/**
 * CAPA DE PRESENTACIÓN — API REST pública de seguimiento de un pedido
 * (API de consumo externo: la puede usar el cliente final o la web del
 * comercio para mostrar "tu pedido está en camino").
 *
 * GET /api/v1/seguimiento/{codigoSeguimiento}
 *   → {"codigoSeguimiento", "estado", "fechaPrometida"?, "llegadaEstimada"?,
 *      "ubicacionRepartidor"?}
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
import com.rabbit.pedidos.presentacion.SeguimientoBean;
import com.rabbit.repartidores.dto.UbicacionDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.ruteo.dto.LlegadaEstimadaDTO;
import com.rabbit.ruteo.negocio.ILlegadaEstimada;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
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

    @Inject
    private IGestionRepartidores repartidores;

    @Inject
    private ILlegadaEstimada llegadas;

    @GET
    @Path("{codigo}")
    public Response consultar(@PathParam("codigo") String codigo) {
        try {
            PedidoDTO pedido = seguimiento.consultarSeguimiento(codigo);
            JsonObjectBuilder cuerpo = Json.createObjectBuilder()
                    .add("codigoSeguimiento", pedido.codigoSeguimiento)
                    .add("estado", pedido.estado);
            // El día que Rabbit se comprometió a entregarlo (ADR-042).
            if (pedido.fechaPrometida != null) {
                cuerpo.add("fechaPrometida", pedido.fechaPrometida.toString());
            }
            // Mientras va en camino con un repartidor de Rabbit que comparte
            // su ubicación (ADR-035). Si no, el campo no viene.
            UbicacionDTO ubicacion = "EN_CAMINO".equals(pedido.estado) && pedido.idRepartidor != null
                    ? repartidores.ubicacionParaPedido(pedido.idRepartidor, pedido.id) : null;
            // En qué parada va y en qué franja llega (ADR-043).
            LlegadaEstimadaDTO llegada = "EN_CAMINO".equals(pedido.estado) && pedido.idRepartidor != null
                    ? SeguimientoBean.estimarLlegada(llegadas, pedido) : null;
            if (llegada != null) {
                cuerpo.add("llegadaEstimada", Json.createObjectBuilder()
                        .add("numeroParada", llegada.numeroParada)
                        .add("totalParadas", llegada.totalParadas)
                        .add("desde", llegada.getDesdeTexto())
                        .add("hasta", llegada.getHastaTexto()));
            }
            if (ubicacion != null) {
                cuerpo.add("ubicacionRepartidor", Json.createObjectBuilder()
                        .add("latitud", ubicacion.latitud)
                        .add("longitud", ubicacion.longitud)
                        .add("actualizada", ubicacion.fecha.toString()));
            }
            return Response.ok(cuerpo.build()).build();
        } catch (PedidoNoEncontradoException e) {
            return Problema.de(Response.Status.NOT_FOUND, "pedido-inexistente", "Pedido inexistente",
                    "No hay ningún pedido con el código " + codigo).respuesta();
        }
    }
}
