package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.negocio.TransicionNoPermitidaException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Red de contención de la API: toda excepción sale como Problem Details, nunca con un stack trace. */
class ProblemaMapperTest {

    private final ProblemaMapper mapper = new ProblemaMapper();

    private static JsonObject cuerpo(Response r) {
        return Json.createReader(new StringReader((String) r.getEntity())).readObject();
    }

    @Test
    void unaUrlInexistenteEs404EnProblemDetails() {
        Response r = mapper.toResponse(new NotFoundException());
        assertEquals(404, r.getStatus());
        assertEquals(Problema.MEDIA_TYPE + ";charset=UTF-8", r.getMediaType().toString());
        assertEquals("/problemas/recurso-inexistente", cuerpo(r).getString("type"));
        assertTrue(cuerpo(r).getString("detail").contains("/api/v1"));
    }

    @Test
    void unMetodoNoAdmitidoConservaElHeaderAllow() {
        Response r = mapper.toResponse(new NotAllowedException(Response.status(405).allow("GET", "POST").build()));
        assertEquals(405, r.getStatus());
        assertEquals("/problemas/metodo-no-admitido", cuerpo(r).getString("type"));
        assertNotNull(r.getHeaderString("Allow"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 406, 415, 418, 503})
    void cadaStatusDelRuntimeTieneSuTipo(int status) {
        Response r = mapper.toResponse(new WebApplicationException(status));
        assertEquals(status, r.getStatus());
        String tipo = cuerpo(r).getString("type");
        assertTrue(tipo.startsWith("/problemas/"), tipo);
        if (status == 503) {
            assertEquals("/problemas/error-interno", tipo);
        } else if (status == 418) {
            assertEquals("/problemas/error-http", tipo);
        }
    }

    @Test
    void unMediaTypeNoSoportadoDiceQueLaApiRecibeJson() {
        Response r = mapper.toResponse(new NotSupportedException());
        assertEquals(415, r.getStatus());
        assertEquals("La API recibe application/json.", cuerpo(r).getString("detail"));
    }

    @Test
    void unCuerpoQueNoEsJsonEs400AunqueVengaEnvuelto() {
        assertEquals(400, mapper.toResponse(new JsonbException("roto")).getStatus());
        assertEquals(400, mapper.toResponse(new ProcessingException(new JsonbException("roto"))).getStatus());
        assertEquals(400, mapper.toResponse(new EJBException(new RuntimeException(new JsonbException("roto")))).getStatus());
    }

    @Test
    void unAccesoDenegadoEs403() {
        Response r = mapper.toResponse(new EJBAccessException("sin rol"));
        assertEquals(403, r.getStatus());
        assertEquals("/problemas/acceso-denegado", cuerpo(r).getString("type"));
    }

    @Test
    void unaTransicionInvalidaEs409() {
        Response r = mapper.toResponse(new EJBException(new TransicionNoPermitidaException("cambió")));
        assertEquals(409, r.getStatus());
        assertEquals("/problemas/transicion-no-permitida", cuerpo(r).getString("type"));
    }

    @Test
    void cualquierOtraCosaEs500SinElDetalleInterno() {
        Response r = mapper.toResponse(new EJBException(new IllegalStateException("SQL: tabla usuarios rota")));
        assertEquals(500, r.getStatus());
        JsonObject c = cuerpo(r);
        assertEquals("/problemas/error-interno", c.getString("type"));
        assertFalse(c.toString().contains("SQL"), "el detalle técnico queda solo en el log");
    }
}
