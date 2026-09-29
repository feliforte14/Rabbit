package com.rabbit.integracion.transportistas.simulador;

/**
 * TRANSPORTISTA SIMULADO con API REST moderna (el que consume
 * AdaptadorRestTransportista). Base:
 *   http://localhost:8080/Rabbit/api/simulador/transportista-rest
 * Sin autenticación, como el banco simulado: no es parte de Rabbit sino el
 * sistema de otra empresa, publicado acá solo para la demo.
 */

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.StringReader;

@Path("simulador/transportista-rest/envios")
@Produces(MediaType.APPLICATION_JSON)
public class TransportistaRestSimuladoResource {

    private static final SimuladorDeEnvios SIMULADOR = new SimuladorDeEnvios("Transportista REST", "TR-");

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response solicitar(String cuerpo) {
        JsonObject pedido;
        try (var lector = Json.createReader(new StringReader(cuerpo == null ? "" : cuerpo))) {
            pedido = lector.readObject();
        } catch (RuntimeException e) {
            return error(400, "Cuerpo JSON inválido");
        }
        String motivo = SIMULADOR.motivoDeRechazo(pedido.getInt("bultos", 0));
        if (motivo != null) {
            return error(422, motivo);
        }
        String codigo = SIMULADOR.registrar(pedido.getString("referencia", "?"), pedido.getString("direccionEntrega", "?"));
        return Response.status(201)
                .entity(Json.createObjectBuilder().add("codigoSeguimiento", codigo).build().toString())
                .build();
    }

    @GET
    @Path("{codigo}")
    public Response consultar(@PathParam("codigo") String codigo) {
        SimuladorDeEnvios.Estado estado = SIMULADOR.estado(codigo);
        if (estado == null) {
            return error(404, "Envío inexistente");
        }
        return Response.ok(Json.createObjectBuilder()
                .add("codigoSeguimiento", codigo)
                .add("estado", estado.name())
                .build().toString()).build();
    }

    @DELETE
    @Path("{codigo}")
    public Response cancelar(@PathParam("codigo") String codigo) {
        return SIMULADOR.cancelar(codigo) ? Response.noContent().build() : error(404, "Envío inexistente");
    }

    private static Response error(int estado, String mensaje) {
        return Response.status(estado)
                .entity(Json.createObjectBuilder().add("error", mensaje).build().toString())
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
