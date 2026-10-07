package com.rabbit.integracion.transportistas.simulador;

/**
 * TRANSPORTISTA SIMULADO con API REST moderna (el que consume
 * AdaptadorRestTransportista). Base:
 *   http://localhost:8080/Rabbit/api/simulador/transportista-rest
 *   POST /cotizaciones, POST /envios, GET y DELETE /envios/{codigo}
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

@Path("simulador/transportista-rest")
@Produces(MediaType.APPLICATION_JSON)
public class TransportistaRestSimuladoResource {

    private static final SimuladorDeEnvios SIMULADOR = new SimuladorDeEnvios("Transportista REST", "TR-");

    // Cuánto saldría el envío, sin pedirlo: mismas reglas de capacidad que
    // el pedido real, así una cotización nunca promete algo que después se
    // rechaza.
    @POST
    @Path("cotizaciones")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cotizar(String cuerpo) {
        JsonObject pedido = leer(cuerpo);
        if (pedido == null) {
            return error(400, "Cuerpo JSON inválido");
        }
        int bultos = pedido.getInt("bultos", 0);
        String motivo = SIMULADOR.motivoDeRechazo(bultos);
        if (motivo != null) {
            return error(422, motivo);
        }
        return Response.ok(Json.createObjectBuilder()
                .add("precio", SIMULADOR.precio(bultos, pedido.containsKey("cobrarAlEntregar")))
                .add("plazoHoras", SimuladorDeEnvios.PLAZO_HORAS)
                .build().toString()).build();
    }

    @POST
    @Path("envios")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response solicitar(String cuerpo) {
        JsonObject pedido = leer(cuerpo);
        if (pedido == null) {
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
    @Path("envios/{codigo}")
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
    @Path("envios/{codigo}")
    public Response cancelar(@PathParam("codigo") String codigo) {
        switch (SIMULADOR.cancelar(codigo)) {
            case CANCELADO: return Response.noContent().build();
            case YA_ENTREGADO: return error(409, "El envío ya fue entregado: no se puede cancelar");
            default: return error(404, "Envío inexistente");
        }
    }

    private static JsonObject leer(String cuerpo) {
        try (var lector = Json.createReader(new StringReader(cuerpo == null ? "" : cuerpo))) {
            return lector.readObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Response error(int estado, String mensaje) {
        return Response.status(estado)
                .entity(Json.createObjectBuilder().add("error", mensaje).build().toString())
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
