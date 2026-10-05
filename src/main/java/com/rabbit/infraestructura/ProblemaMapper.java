package com.rabbit.infraestructura;

/**
 * Red de contención de la API REST: toda excepción que un recurso no
 * atrapó termina acá y sale como Problem Details (ver Problema), nunca
 * como la página error.html del web.xml ni con un stack trace.
 *
 * Los recursos siguen respondiendo sus propios errores de negocio (400,
 * 404, 409, 422) con el detalle que corresponde; este mapper cubre lo que
 * no es de ningún recurso:
 *   - URL o método inexistente, media type no soportado (las
 *     WebApplicationException del runtime JAX-RS): mismo status, en JSON;
 *   - cuerpo que no es JSON o no encaja con el DTO: 400;
 *   - un EJB que niega el acceso (@RolesAllowed): 403;
 *   - cualquier otra cosa: 500 genérico, y el detalle solo en el log.
 *
 * Los recursos son EJB: lo que se les escapa llega envuelto en una
 * EJBException, así que primero se desenvuelve.
 */

import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.json.JsonException;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class ProblemaMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ProblemaMapper.class.getName());

    @Override
    public Response toResponse(Throwable excepcion) {
        Throwable e = desenvolver(excepcion);

        if (e instanceof WebApplicationException) {
            Response original = ((WebApplicationException) e).getResponse();
            int status = original.getStatus();
            String titulo = original.getStatusInfo().getReasonPhrase();
            // fromResponse conserva los headers del runtime (por ejemplo
            // Allow en un 405).
            return Problema.de(status, tipoSegun(status), titulo, detalleSegun(status))
                    .aplicarA(Response.fromResponse(original));
        }
        if (esCuerpoInvalido(e)) {
            return Problema.de(Response.Status.BAD_REQUEST, "cuerpo-invalido", "Cuerpo inválido",
                    "El cuerpo no es JSON válido o algún campo tiene un tipo o valor no admitido").respuesta();
        }
        if (e instanceof EJBAccessException) {
            return Problema.de(Response.Status.FORBIDDEN, "acceso-denegado", "Acceso denegado",
                    "Tu usuario no tiene permiso para esta operación").respuesta();
        }
        LOG.log(Level.SEVERE, "[API REST] Error no controlado", excepcion);
        return Problema.de(Response.Status.INTERNAL_SERVER_ERROR, "error-interno", "Error interno",
                "No se pudo procesar el pedido. Si el problema sigue, avisá a Rabbit.").respuesta();
    }

    private static Throwable desenvolver(Throwable e) {
        while (e instanceof EJBException && e.getCause() != null && !(e instanceof EJBAccessException)) {
            e = e.getCause();
        }
        return e;
    }

    // El runtime envuelve el error de JSON-B de distintas formas según la
    // versión: se busca en toda la cadena de causas.
    private static boolean esCuerpoInvalido(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof JsonbException || t instanceof JsonException || t instanceof ProcessingException) {
                return true;
            }
        }
        return false;
    }

    private static String tipoSegun(int status) {
        switch (status) {
            case 400: return "cuerpo-invalido";
            case 401: return "no-autenticado";
            case 403: return "acceso-denegado";
            case 404: return "recurso-inexistente";
            case 405: return "metodo-no-admitido";
            case 406: return "formato-no-disponible";
            case 415: return "formato-no-admitido";
            default: return status >= 500 ? "error-interno" : "error-http";
        }
    }

    private static String detalleSegun(int status) {
        switch (status) {
            case 404: return "No existe ese recurso en la API. Revisá la URL (la versión actual es /api/v1).";
            case 405: return "El recurso existe pero no admite ese método HTTP (ver el header Allow).";
            case 415: return "La API recibe application/json.";
            case 406: return "La API responde application/json.";
            default: return null;
        }
    }
}
