package com.rabbit.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests de integración de las mejoras de última milla (ADR-039, 042 y 043)
 * contra Rabbit desplegado. Mismos parámetros que ApiRestIT:
 *
 *   mvn verify -Pintegracion -Drabbit.erp.usuario=... -Drabbit.erp.clave=... -Drabbit.punto=...
 *
 * Los pedidos de prueba llevan "[TEST]" en la dirección.
 */
class MejorasUltimaMillaIT {

    private static final String PROBLEMA = "application/problem+json";
    private static String usuario;
    private static String clave;
    private static long punto;

    @BeforeAll
    static void preparar() {
        RestAssured.baseURI = System.getProperty("rabbit.base", "https://localhost:8443/Rabbit");
        RestAssured.basePath = "/api/v1";
        RestAssured.useRelaxedHTTPSValidation();
        usuario = System.getProperty("rabbit.erp.usuario");
        clave = System.getProperty("rabbit.erp.clave");
        punto = Long.parseLong(System.getProperty("rabbit.punto", "0"));
        assumeTrue(usuario != null && clave != null && punto > 0,
                "Faltan -Drabbit.erp.usuario, -Drabbit.erp.clave y -Drabbit.punto");
    }

    private static RequestSpecification erp() {
        return given().auth().preemptive().basic(usuario, clave).contentType("application/json");
    }

    private static Map<String, Object> pedido() {
        Map<String, Object> p = new HashMap<>();
        p.put("origen", "PUNTO_PICKING");
        p.put("idPuntoPicking", punto);
        p.put("lineas", List.of(Map.of("producto", "[TEST] Caja", "cantidad", 1)));
        p.put("importe", 1500);
        p.put("medioPago", "CONTRA_ENTREGA");
        p.put("direccionEntrega", "[TEST] Av. Siempreviva 742, CABA");
        p.put("codigoPostalEntrega", "1043");
        return p;
    }

    private static Map<String, Object> conDestinatario(String email) {
        Map<String, Object> p = pedido();
        p.put("nombreDestinatario", "Ana Pérez");
        p.put("telefonoDestinatario", "(011) 4567-8901");
        p.put("emailDestinatario", email);
        return p;
    }

    private static Response alta(Map<String, Object> cuerpo, String claveIdempotencia) {
        return erp().header("Idempotency-Key", claveIdempotencia).body(cuerpo).post("/pedidos-externos");
    }

    // Espera a que la cola lo convierta en pedido real (a lo sumo ~10 s).
    private static Response sincronizado(int idExterno) throws InterruptedException {
        Response r = null;
        for (int i = 0; i < 20; i++) {
            r = erp().get("/pedidos-externos/" + idExterno);
            if ("Sincronizado".equals(r.path("resultado"))) {
                return r;
            }
            Thread.sleep(500);
        }
        fail("El pedido externo " + idExterno + " no se sincronizó: " + (r == null ? "" : r.asString()));
        return r;
    }

    @Test
    void elAltaConDestinatarioTraeLaFechaPrometida() {
        alta(conDestinatario("ana@correo.test"), UUID.randomUUID().toString()).then()
                .statusCode(201)
                .body("resultado", equalTo("Pendiente"))
                .body("fechaPrometida", matchesPattern("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void sinCodigoPostalNoSePrometeFecha() {
        Map<String, Object> p = pedido();
        p.remove("codigoPostalEntrega");
        alta(p, UUID.randomUUID().toString()).then()
                .statusCode(201)
                .body("$", not(hasKey("fechaPrometida")));
    }

    @Test
    void unEmailInvalidoEs400ConElCampo() {
        alta(conDestinatario("ana.correo.test"), UUID.randomUUID().toString()).then()
                .statusCode(400)
                .contentType(PROBLEMA)
                .body("errores.campo", hasItem("emailDestinatario"));
    }

    @Test
    void unTelefonoQueNoSirveEs422() {
        Map<String, Object> p = conDestinatario("ana@correo.test");
        p.put("telefonoDestinatario", "llamar al portero");
        alta(p, UUID.randomUUID().toString()).then()
                .statusCode(422)
                .contentType(PROBLEMA)
                .body("detail", containsString("teléfono"));
    }

    @Test
    void elMismoDestinatarioEscritoDistintoEsElMismoReintento() {
        // El email se normaliza (minúsculas, sin espacios): la huella no cambia.
        String k = UUID.randomUUID().toString();
        int primero = alta(conDestinatario("ana@correo.test"), k).then().statusCode(201).extract().path("idPedidoExterno");
        int segundo = alta(conDestinatario("  Ana@Correo.TEST "), k).then().statusCode(201).extract().path("idPedidoExterno");
        assertEquals(primero, segundo);
    }

    @Test
    void elSeguimientoPublicoNoMuestraAQuienRecibe() throws InterruptedException {
        int id = alta(conDestinatario("ana@correo.test"), UUID.randomUUID().toString())
                .then().statusCode(201).extract().path("idPedidoExterno");
        String codigo = sincronizado(id).path("codigoSeguimiento");
        String cuerpo = given().get("/seguimiento/" + codigo).then()
                .statusCode(200)
                .body("estado", equalTo("PENDIENTE"))
                .body("fechaPrometida", matchesPattern("\\d{4}-\\d{2}-\\d{2}"))
                .extract().asString();
        assertFalse(cuerpo.contains("Ana") || cuerpo.contains("4567") || cuerpo.contains("correo"),
                "El seguimiento público expone datos de quien recibe: " + cuerpo);
    }
}
