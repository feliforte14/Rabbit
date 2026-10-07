package com.rabbit.transportistas.presentacion.rest;

/**
 * CAPA DE PRESENTACIÓN — webhook de novedades de envíos.
 *
 *   POST /api/v1/transportistas/{id}/novedades
 *   Authorization: Bearer <clave del transportista>
 *   {"codigoSeguimiento": "TR-7", "estado": "EN_TRANSITO"}
 *
 * Un transportista moderno avisa cada cambio de estado apenas ocurre, en
 * vez de esperar a que Rabbit le pregunte (polling cada 15 s). El polling
 * sigue igual para los legados, que no avisan, y como respaldo si un aviso
 * se pierde: los dos llevan al mismo resultado, y repetir un aviso no
 * cambia nada.
 *
 * SEGURIDAD: no hay usuario: la clave (que genera el personal en la
 * pantalla de Transportistas y se ve una sola vez) identifica al
 * transportista, y solo puede tocar sus propios envíos. Viaja por HTTPS
 * (web.xml). @RunAs("OPERADOR"), igual que el polling: mover el pedido
 * (EN_CAMINO / ENTREGADO) es una operación del personal.
 *
 * Respuestas: 204 aceptado (cambió o ya estaba así) · 400 cuerpo inválido ·
 * 401 clave inválida · 404 envío desconocido · 422 estado desconocido.
 */

import com.rabbit.infraestructura.Problema;
import com.rabbit.transportistas.negocio.ISeguimientoEnvios;
import com.rabbit.transportistas.negocio.NovedadRechazadaException;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.StringReader;

@Path("v1/transportistas/{id}/novedades")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Stateless
@PermitAll
@RunAs("OPERADOR")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class NovedadesTransportistaResource {

    private static final String BEARER = "Bearer ";

    @Inject
    private ISeguimientoEnvios seguimiento;

    @POST
    public Response recibir(@PathParam("id") Long idTransportista,
                            @HeaderParam(HttpHeaders.AUTHORIZATION) String autorizacion,
                            String cuerpo) {
        String clave = autorizacion != null && autorizacion.startsWith(BEARER)
                ? autorizacion.substring(BEARER.length()).trim() : null;
        JsonObject novedad;
        try (var lector = Json.createReader(new StringReader(cuerpo == null ? "" : cuerpo))) {
            novedad = lector.readObject();
        } catch (JsonException e) {
            return Problema.de(Response.Status.BAD_REQUEST, "cuerpo-invalido", "Cuerpo inválido",
                    "Se espera {\"codigoSeguimiento\": \"...\", \"estado\": \"...\"}").respuesta();
        }
        try {
            seguimiento.recibirNovedad(idTransportista, clave,
                    novedad.getString("codigoSeguimiento", null), novedad.getString("estado", null));
            return Response.noContent().build();
        } catch (NovedadRechazadaException e) {
            switch (e.getMotivo()) {
                case CLAVE_INVALIDA:
                    return Problema.de(Response.Status.UNAUTHORIZED, "clave-webhook-invalida",
                            "Clave de webhook inválida", e.getMessage()).respuesta();
                case ENVIO_DESCONOCIDO:
                    return Problema.de(Response.Status.NOT_FOUND, "envio-inexistente",
                            "Envío inexistente", e.getMessage()).respuesta();
                default:
                    return Problema.de(422, "estado-desconocido", "Estado desconocido", e.getMessage()).respuesta();
            }
        }
    }
}
