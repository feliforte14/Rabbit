package com.rabbit.pedidos.presentacion.rest;

/**
 * CAPA DE PRESENTACIÓN — API REST de entrada de pedidos para el ERP de cada
 * comercio (integración sincrónica con un partner moderno).
 *
 * Es otra puerta de entrada al mismo componente Pedidos, igual que
 * PedidoBean para la pantalla: no tiene reglas de negocio propias, delega
 * en IGestionPedidos / ISeguimientoPedido. Reemplaza al formulario JSF que
 * simulaba el ERP: ahora hay una frontera real entre los dos sistemas.
 *
 * POR QUÉ SINCRÓNICO: el ERP necesita saber en el momento si Rabbit aceptó
 * el pedido (datos válidos) y con qué ID. La CONVERSIÓN en pedido real
 * sigue siendo asincrónica (cola.pedidos.externos), así que la respuesta
 * es inmediata: 201 Created con el ID del pedido externo, y el ERP consulta
 * después cómo terminó con GET /api/pedidos-externos/{id}.
 *
 * POR QUÉ REST Y NO SOAP: el ERP es un partner moderno; JSON sobre HTTP no
 * le exige generar clientes a partir de un WSDL.
 *
 * SEGURIDAD: solo usuarios con rol ERP (autenticación HTTP Basic contra el
 * ApplicationRealm, ver web.xml). Se exige en dos lugares: el
 * security-constraint de web.xml (responde 401 sin credenciales) y
 * @RolesAllowed en este EJB, que es la restricción que no depende de cómo
 * se configure la URL.
 */

import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.ValidacionException;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;

@Path("pedidos-externos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Stateless
@DeclareRoles({"ERP"})
@PermitAll
public class PedidosExternosResource {

    @Inject
    private IGestionPedidos gestion;

    @Inject
    private ISeguimientoPedido seguimiento;

    /**
     * POST /api/pedidos-externos
     * Body: {"idComercio": 1, "origen": "STOCK_CONSIGNADO",
     *        "lineas": [{"idItem": 3, "cantidad": 2}],
     *        "importe": 2500, "medioPago": "PREPAGO"}
     *
     * 201 Created + Location: el pedido quedó registrado y se sincroniza por
     * la cola. 400: datos inválidos (mismo motivo que muestra la pantalla).
     */
    @POST
    @RolesAllowed("ERP")
    public Response registrar(DatosPedidoExternoDTO datos, @Context UriInfo uri) {
        if (datos == null) {
            return error(Response.Status.BAD_REQUEST, "Falta el cuerpo del pedido");
        }
        try {
            Long id = gestion.registrarPedidoExterno(datos);
            URI ubicacion = uri.getAbsolutePathBuilder().path(String.valueOf(id)).build();
            return Response.created(ubicacion)
                    .entity(Json.createObjectBuilder()
                            .add("idPedidoExterno", id)
                            .add("resultado", "Pendiente")
                            .build())
                    .build();
        } catch (ValidacionException e) {
            return error(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * GET /api/pedidos-externos/{id}
     * Cómo terminó un pedido enviado: Pendiente, Sincronizado (con el ID del
     * pedido real, para seguirlo) o Descartado (con el motivo).
     */
    @GET
    @Path("{id}")
    @RolesAllowed("ERP")
    public Response consultar(@PathParam("id") Long id) {
        try {
            PedidoExternoDTO externo = seguimiento.consultarPedidoExterno(id);
            JsonObjectBuilder json = Json.createObjectBuilder()
                    .add("idPedidoExterno", externo.id)
                    .add("resultado", externo.resultado);
            if (externo.idPedido != null) {
                json.add("idPedido", externo.idPedido);
            }
            if (externo.errorSincronizacion != null) {
                json.add("motivo", externo.errorSincronizacion);
            }
            return Response.ok(json.build()).build();
        } catch (ValidacionException e) {
            return error(Response.Status.NOT_FOUND, e.getMessage());
        }
    }

    static Response error(Response.Status status, String mensaje) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Json.createObjectBuilder().add("error", mensaje).build())
                .build();
    }
}
