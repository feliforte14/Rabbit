package com.rabbit.pedidos.presentacion.rest;

/**
 * CAPA DE PRESENTACIÓN — API REST de entrada de pedidos para el ERP de cada
 * comercio (integración sincrónica con un partner moderno). Contrato
 * completo en docs/integraciones/openapi.yaml.
 *
 *   POST /api/v1/pedidos-externos                    registrar un pedido
 *   GET  /api/v1/pedidos-externos/{id}               cómo terminó
 *   POST /api/v1/pedidos-externos/{id}/cancelacion   cancelarlo
 *
 * Es otra puerta de entrada al mismo componente Pedidos, igual que
 * PedidoBean para la pantalla: no tiene reglas de negocio propias, delega
 * en IGestionPedidos / ISeguimientoPedido.
 *
 * POR QUÉ SINCRÓNICO: el ERP necesita saber en el momento si Rabbit aceptó
 * el pedido (datos válidos) y con qué ID. La CONVERSIÓN en pedido real
 * sigue siendo asincrónica (cola.pedidos.externos), así que la respuesta
 * es inmediata: 201 Created con el pedido externo en estado Pendiente, y
 * el ERP consulta después cómo terminó (o sigue los _links).
 *
 * POR QUÉ REST Y NO SOAP: el ERP es un partner moderno; JSON sobre HTTP no
 * le exige generar clientes a partir de un WSDL.
 *
 * IDEMPOTENCIA: el POST exige el header Idempotency-Key (un UUID por
 * pedido). Si el 201 se pierde por un timeout y el ERP reintenta con la
 * misma clave, recibe el mismo pedido externo en vez de crear otro. La
 * cancelación es idempotente por diseño (cancelar dos veces = una vez).
 *
 * SEGURIDAD: solo usuarios con rol ERP (HTTP Basic contra el
 * ApplicationRealm, ver web.xml), y cada cuenta ERP representa a UN
 * comercio: solo carga y ve los pedidos de ese comercio (lo impone
 * PedidoService, no este recurso). Se exige en dos lugares: el
 * security-constraint de web.xml (401 sin credenciales) y @RolesAllowed en
 * este EJB, que no depende de cómo se configure la URL.
 *
 * ERRORES: Problem Details (application/problem+json, ver Problema).
 *   400 formato (cuerpo, header, campos)   404 no existe o es de otro comercio
 *   409 ya no se puede cancelar            422 regla de negocio o clave reutilizada
 *
 * TRANSACCIONES: NOT_SUPPORTED a propósito. Cada operación de negocio
 * abre y confirma su propia transacción, así un error al confirmar (por
 * ejemplo, dos reintentos simultáneos con la misma clave) llega acá y se
 * puede responder bien, en vez de explotar después de que este método
 * terminó.
 */

import com.rabbit.infraestructura.Problema;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.negocio.CancelacionNoPermitidaException;
import com.rabbit.pedidos.negocio.ClaveIdempotenciaReutilizadaException;
import com.rabbit.pedidos.negocio.CuentaErpSinComercioException;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.PedidoNoEncontradoException;
import com.rabbit.pedidos.negocio.ValidacionException;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Comparator;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Path("v1/pedidos-externos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
@DeclareRoles({"ERP"})
@RolesAllowed("ERP")
public class PedidosExternosResource {

    private static final Logger LOG = Logger.getLogger(PedidosExternosResource.class.getName());

    // Un UUID entra holgado; la lista blanca evita guardar cualquier cosa.
    private static final Pattern CLAVE_VALIDA = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    @Inject
    private IGestionPedidos gestion;

    @Inject
    private ISeguimientoPedido seguimiento;

    // Bean Validation a mano y no con @Valid en el parámetro: así el 400 sale
    // en el mismo formato Problem Details que el resto, y no en el formato
    // propio con el que el runtime responde las violaciones.
    @Inject
    private Validator validador;

    /**
     * POST /api/v1/pedidos-externos
     * Header: Idempotency-Key: 7f3c9e1a-...
     * Body: {"origen": "STOCK_CONSIGNADO", "lineas": [{"idItem": 3, "cantidad": 2}],
     *        "importe": 2500, "medioPago": "PREPAGO", "direccionEntrega": "..."}
     *
     * 201 Created + Location + el pedido externo (también en un reintento).
     */
    @POST
    public Response registrar(PedidoExternoRequest pedido,
                              @HeaderParam("Idempotency-Key") String clave,
                              @Context UriInfo uri) {
        if (pedido == null) {
            return Problema.de(Response.Status.BAD_REQUEST, "cuerpo-invalido", "Cuerpo inválido",
                    "Falta el cuerpo del pedido").respuesta();
        }
        if (clave == null || !CLAVE_VALIDA.matcher(clave).matches()) {
            return Problema.de(Response.Status.BAD_REQUEST, "clave-idempotencia-invalida",
                    "Falta o es inválido el header Idempotency-Key",
                    "Mandá un identificador único por pedido (por ejemplo un UUID), y el mismo si reintentás").respuesta();
        }
        Set<ConstraintViolation<PedidoExternoRequest>> errores = validador.validate(pedido);
        if (!errores.isEmpty()) {
            return datosConFormatoInvalido(errores);
        }

        Long id;
        try {
            id = registrarConReintento(pedido, clave);
        } catch (ClaveIdempotenciaReutilizadaException e) {
            return Problema.de(422, "clave-idempotencia-reutilizada", "Clave de idempotencia reutilizada",
                    e.getMessage()).respuesta();
        } catch (CuentaErpSinComercioException e) {
            return sinComercio(e);
        } catch (ValidacionException e) {
            return Problema.de(422, "datos-invalidos", "El pedido no cumple las reglas de Rabbit",
                    e.getMessage()).respuesta();
        }
        PedidoExternoDTO externo = seguimiento.consultarPedidoExterno(id);
        return Response.created(enlace(uri, id))
                .entity(representacion(externo, uri))
                .build();
    }

    /**
     * GET /api/v1/pedidos-externos/{id}
     * Cómo terminó un pedido enviado: Pendiente, Sincronizado (con el ID,
     * el estado y el código de seguimiento del pedido real), Descartado
     * (con el motivo) o Cancelado.
     */
    @GET
    @Path("{id}")
    public Response consultar(@PathParam("id") Long id, @Context UriInfo uri) {
        try {
            return Response.ok(representacion(seguimiento.consultarPedidoExterno(id), uri)).build();
        } catch (PedidoNoEncontradoException e) {
            return noEncontrado(id);
        } catch (CuentaErpSinComercioException e) {
            return sinComercio(e);
        }
    }

    /**
     * POST /api/v1/pedidos-externos/{id}/cancelacion
     * Sub-recurso y no DELETE: cancelar no borra nada (el pedido queda,
     * cancelado). Repetirlo devuelve lo mismo.
     */
    @POST
    @Path("{id}/cancelacion")
    public Response cancelar(@PathParam("id") Long id, @Context UriInfo uri) {
        try {
            return Response.ok(representacion(gestion.cancelarPedidoExterno(id), uri)).build();
        } catch (PedidoNoEncontradoException e) {
            return noEncontrado(id);
        } catch (CancelacionNoPermitidaException e) {
            return Problema.de(Response.Status.CONFLICT, "cancelacion-no-permitida",
                    "El pedido ya no se puede cancelar", e.getMessage()).respuesta();
        } catch (CuentaErpSinComercioException e) {
            return sinComercio(e);
        } catch (ValidacionException e) {
            return Problema.de(422, "cancelacion-fallida", "No se pudo cancelar el pedido",
                    e.getMessage()).respuesta();
        }
    }

    // Dos reintentos simultáneos con la misma clave pasan los dos la
    // consulta de PedidoService, pero la restricción única rechaza al
    // segundo al confirmar (EJBException). Un nuevo intento ya encuentra
    // el pedido del primero y lo devuelve: el ERP nunca ve el choque.
    private Long registrarConReintento(PedidoExternoRequest pedido, String clave) {
        try {
            return gestion.registrarPedidoExterno(pedido.aDatos(), clave);
        } catch (EJBAccessException e) {
            throw e;
        } catch (EJBException e) {
            LOG.info("[API REST] El alta con la clave " + clave + " no se pudo confirmar; se reintenta una vez");
            return gestion.registrarPedidoExterno(pedido.aDatos(), clave);
        }
    }

    // HATEOAS: además del estado, la respuesta dice qué se puede hacer
    // ahora (cancelar, seguir el pedido) y dónde, sin que el ERP arme URLs.
    private static JsonObject representacion(PedidoExternoDTO externo, UriInfo uri) {
        JsonObjectBuilder json = Json.createObjectBuilder()
                .add("idPedidoExterno", externo.id)
                .add("resultado", externo.resultado);
        if (externo.idPedido != null) {
            json.add("idPedido", externo.idPedido);
        }
        if (externo.estadoPedido != null) {
            json.add("estadoPedido", externo.estadoPedido);
        }
        if (externo.codigoSeguimiento != null) {
            json.add("codigoSeguimiento", externo.codigoSeguimiento);
        }
        if (externo.errorSincronizacion != null) {
            json.add("motivo", externo.errorSincronizacion);
        }

        JsonObjectBuilder links = Json.createObjectBuilder()
                .add("self", Json.createObjectBuilder().add("href", enlace(uri, externo.id).toString()));
        boolean cancelable = "Pendiente".equals(externo.resultado)
                || ("Sincronizado".equals(externo.resultado) && "PENDIENTE".equals(externo.estadoPedido));
        if (cancelable) {
            links.add("cancelar", Json.createObjectBuilder()
                    .add("href", uri.getBaseUriBuilder().path(PedidosExternosResource.class)
                            .path(String.valueOf(externo.id)).path("cancelacion").build().toString())
                    .add("method", "POST"));
        }
        if (externo.codigoSeguimiento != null) {
            links.add("seguimiento", Json.createObjectBuilder()
                    .add("href", uri.getBaseUriBuilder().path(SeguimientoResource.class)
                            .path(externo.codigoSeguimiento).build().toString()));
        }
        return json.add("_links", links).build();
    }

    private static URI enlace(UriInfo uri, Long id) {
        return uri.getBaseUriBuilder().path(PedidosExternosResource.class).path(String.valueOf(id)).build();
    }

    private static Response noEncontrado(Long id) {
        return Problema.de(Response.Status.NOT_FOUND, "pedido-inexistente", "Pedido externo inexistente",
                "No hay ningún pedido externo " + id + " de tu comercio").respuesta();
    }

    private static Response sinComercio(CuentaErpSinComercioException e) {
        return Problema.de(Response.Status.FORBIDDEN, "cuenta-erp-sin-comercio", "Cuenta ERP sin comercio",
                e.getMessage()).respuesta();
    }

    // 400 con un error por campo: el ERP sabe exactamente qué corregir.
    private static Response datosConFormatoInvalido(Set<ConstraintViolation<PedidoExternoRequest>> errores) {
        JsonArrayBuilder campos = Json.createArrayBuilder();
        errores.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .forEach(v -> campos.add(Json.createObjectBuilder()
                        .add("campo", v.getPropertyPath().toString())
                        .add("mensaje", v.getMessage())));
        return Problema.de(Response.Status.BAD_REQUEST, "datos-con-formato-invalido",
                        "Hay campos con formato inválido", "Revisá los campos indicados en \"errores\"")
                .con("errores", campos.build())
                .respuesta();
    }
}
