package com.logiRed.comercios.resource;

/**
 * CAPA RESOURCE (Presentación - JAX-RS)
 *
 * Esta carpeta es el punto de entrada HTTP del componente.
 * Cada método en esta clase corresponde a un endpoint REST de la API.
 *
 * JAX-RS (@Path, @GET, @POST, etc.) traduce las peticiones HTTP entrantes
 * en llamadas Java al Service. Esta capa NO tiene lógica de negocio —
 * solo recibe el request, llama al Service, y devuelve la Response HTTP.
 *
 * ComercioResource expone dos grupos de operaciones:
 *   - Escritura (POST, PUT, DELETE): modifican el estado del sistema
 *   - Lectura (GET): consultas que no modifican nada
 *
 * URL base: http://localhost:8080/servicio-comercios/api/comercios
 */

import com.logiRed.comercios.dto.*;
import com.logiRed.comercios.service.ComercioService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/comercios")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ComercioResource {

    @Inject
    private ComercioService service;

    // POST /comercios — registra un comercio nuevo, devuelve 201 con el ID asignado
    @POST
    public Response registrarComercio(DatosComercioDTO datos) {
        Long id = service.registrarComercio(datos);
        return Response.status(Response.Status.CREATED).entity(id).build();
    }

    // PUT /comercios/{id}/datos-fiscales — actualiza CUIT, razón social, email, teléfono
    @PUT
    @Path("/{id}/datos-fiscales")
    public Response actualizarDatosFiscales(@PathParam("id") Long id,
                                             DatosFiscalesDTO datos) {
        service.actualizarDatosFiscales(id, datos);
        return Response.ok().build();
    }

    // DELETE /comercios/{id} — baja lógica: activo=false, el registro queda en la BD
    @DELETE
    @Path("/{id}")
    public Response darDeBajaComercio(@PathParam("id") Long id) {
        service.darDeBajaComercio(id);
        return Response.ok().build();
    }

    // DELETE /comercios/{id}/eliminar — eliminación física: borra el registro definitivamente
    @DELETE
    @Path("/{id}/eliminar")
    public Response eliminarComercio(@PathParam("id") Long id) {
        service.eliminarComercio(id);
        return Response.noContent().build();
    }

    // GET /comercios/{id} — devuelve los datos del comercio como JSON (ComercioDTO)
    @GET
    @Path("/{id}")
    public Response obtenerComercio(@PathParam("id") Long id) {
        return Response.ok(service.obtenerComercio(id)).build();
    }

    // GET /comercios/{id}/sucursales — lista las sucursales activas del comercio
    @GET
    @Path("/{id}/sucursales")
    public Response listarSucursales(@PathParam("id") Long id) {
        return Response.ok(service.listarSucursales(id)).build();
    }

    // GET /comercios/{id}/activo — devuelve true/false según si el comercio puede operar
    @GET
    @Path("/{id}/activo")
    public Response validarComercioActivo(@PathParam("id") Long id) {
        return Response.ok(service.validarComercioActivo(id)).build();
    }
}
