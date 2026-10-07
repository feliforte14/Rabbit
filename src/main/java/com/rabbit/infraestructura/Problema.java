package com.rabbit.infraestructura;

/**
 * Respuesta de error de la API REST en formato Problem Details (RFC 9457,
 * application/problem+json): el equivalente REST del SOAP Fault.
 *
 *   {"type": "/problemas/datos-invalidos", "title": "Datos inválidos",
 *    "status": 422, "detail": "El importe del pedido debe ser mayor a cero"}
 *
 * Un único formato para todos los errores de la API, sean de un recurso
 * (PedidosExternosResource) o de algo que se escapó (ProblemaMapper).
 * "type" identifica el tipo de problema (el ERP puede decidir con él, sin
 * leer el texto); "detail" explica este caso puntual y nunca lleva stack
 * traces ni errores de la base.
 */

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;

public final class Problema {

    public static final String MEDIA_TYPE = "application/problem+json";

    private final int status;
    private final JsonObjectBuilder json;

    private Problema(int status, String tipo, String titulo, String detalle) {
        this.status = status;
        this.json = Json.createObjectBuilder()
                .add("type", "/problemas/" + tipo)
                .add("title", titulo)
                .add("status", status);
        if (detalle != null) {
            json.add("detail", detalle);
        }
    }

    /**
     * @param tipo     identificador estable del problema (va en "type")
     * @param titulo   resumen corto, igual para todos los casos de ese tipo
     * @param detalle  explicación de este caso, o null
     */
    public static Problema de(Response.Status status, String tipo, String titulo, String detalle) {
        return new Problema(status.getStatusCode(), tipo, titulo, detalle);
    }

    /** Para los status que Response.Status no trae (por ejemplo 422). */
    public static Problema de(int status, String tipo, String titulo, String detalle) {
        return new Problema(status, tipo, titulo, detalle);
    }

    /** Campo de extensión propio del problema (por ejemplo, la lista de errores por campo). */
    public Problema con(String campo, JsonArray valor) {
        json.add(campo, valor);
        return this;
    }

    public Problema con(String campo, String valor) {
        json.add(campo, valor);
        return this;
    }

    public Response respuesta() {
        return aplicarA(Response.status(status));
    }

    // Se manda como texto ya serializado: así no depende de que el
    // proveedor JSON-P del servidor acepte el media type problem+json.
    Response aplicarA(Response.ResponseBuilder respuesta) {
        return respuesta.status(status)
                .type(MEDIA_TYPE + ";charset=UTF-8")
                .entity(json.build().toString())
                .build();
    }
}
