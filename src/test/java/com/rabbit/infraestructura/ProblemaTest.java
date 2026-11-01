package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

/** Formato Problem Details (RFC 9457) de los errores de la API. */
class ProblemaTest {

    private static JsonObject leer(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }

    @Test
    void armaLosCamposDelRfcYLosDeExtension() {
        Problema p = Problema.de(Response.Status.CONFLICT, "stock-en-conflicto", "El stock cambió", "Reintentá")
                .con("pedido", "42")
                .con("errores", Json.createArrayBuilder().add("a").build());
        JsonObject c = leer(p.cuerpo());
        assertEquals(409, p.status());
        assertEquals("/problemas/stock-en-conflicto", c.getString("type"));
        assertEquals("El stock cambió", c.getString("title"));
        assertEquals(409, c.getInt("status"));
        assertEquals("Reintentá", c.getString("detail"));
        assertEquals("42", c.getString("pedido"));
        assertEquals(1, c.getJsonArray("errores").size());
    }

    @Test
    void sinDetalleNoLoIncluye() {
        assertFalse(leer(Problema.de(422, "x", "X", null).cuerpo()).containsKey("detail"));
    }

    @Test
    void laRespuestaVaComoProblemJson() {
        Response r = Problema.de(422, "datos-invalidos", "Datos inválidos", "Falta el importe").respuesta();
        assertEquals(422, r.getStatus());
        assertEquals("application/problem+json;charset=UTF-8", r.getMediaType().toString());
        assertEquals("/problemas/datos-invalidos", leer((String) r.getEntity()).getString("type"));
    }
}
