package com.logiRed.comercios.resource;

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

    // IRegistroComercios — protegido: solo administrador

    @POST
    public Response registrarComercio(DatosComercioDTO datos) {
        Long id = service.registrarComercio(datos);
        return Response.status(Response.Status.CREATED).entity(id).build();
    }

    @PUT
    @Path("/{id}/datos-fiscales")
    public Response actualizarDatosFiscales(@PathParam("id") Long id,
                                             DatosFiscalesDTO datos) {
        service.actualizarDatosFiscales(id, datos);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}")
    public Response darDeBajaComercio(@PathParam("id") Long id) {
        service.darDeBajaComercio(id);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/eliminar")
    public Response eliminarComercio(@PathParam("id") Long id) {
        service.eliminarComercio(id);
        return Response.noContent().build();
    }

    // IConsultaComercios — lectura libre

    @GET
    @Path("/{id}")
    public Response obtenerComercio(@PathParam("id") Long id) {
        return Response.ok(service.obtenerComercio(id)).build();
    }

    @GET
    @Path("/{id}/sucursales")
    public Response listarSucursales(@PathParam("id") Long id) {
        return Response.ok(service.listarSucursales(id)).build();
    }

    @GET
    @Path("/{id}/activo")
    public Response validarComercioActivo(@PathParam("id") Long id) {
        return Response.ok(service.validarComercioActivo(id)).build();
    }
}
