package com.rabbit.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests de integración de la API REST v1 contra Rabbit desplegado.
 *
 *   mvn verify -Pintegracion -Drabbit.erp.usuario=... -Drabbit.erp.clave=... -Drabbit.punto=...
 *
 * rabbit.punto: un punto de picking activo del comercio de ese ERP.
 * Opcional para el webhook: -Drabbit.webhook.transportista=<id> -Drabbit.webhook.clave=<clave>.
 * Los pedidos de prueba llevan "[TEST]" en la dirección, para identificarlos.
 */
class ApiRestIT {

    private static final String PROBLEMA = "application/problem+json";
    private static String usuario;
    private static String clave;
    private static long punto;

    @BeforeAll
    static void preparar() {
        RestAssured.baseURI = System.getProperty("rabbit.base", "https://localhost:8443/Rabbit");
        RestAssured.basePath = "/api/v1";
        // WildFly local con certificado autofirmado.
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

    private static Map<String, Object> pedido(int importe) {
        return Map.of("origen", "PUNTO_PICKING", "idPuntoPicking", punto,
                "lineas", java.util.List.of(Map.of("producto", "[TEST] Caja", "cantidad", 1)),
                "importe", importe, "medioPago", "CONTRA_ENTREGA",
                "direccionEntrega", "[TEST] Av. Siempreviva 742, CABA");
    }

    private static Response alta(Map<String, Object> cuerpo, String claveIdempotencia) {
        return erp().header("Idempotency-Key", claveIdempotencia).body(cuerpo).post("/pedidos-externos");
    }

    @Test
    void altaDevuelve201ConLocationYLinks() {
        alta(pedido(1500), UUID.randomUUID().toString()).then()
                .statusCode(201)
                .header("Location", containsString("/api/v1/pedidos-externos/"))
                .body("resultado", equalTo("Pendiente"))
                .body("_links.self.href", notNullValue())
                .body("_links.cancelar.method", equalTo("POST"));
    }

    @Test
    void unReintentoConLaMismaClaveNoDuplica() {
        String k = UUID.randomUUID().toString();
        int primero = alta(pedido(1500), k).then().statusCode(201).extract().path("idPedidoExterno");
        int segundo = alta(pedido(1500), k).then().statusCode(201).extract().path("idPedidoExterno");
        assertEquals(primero, segundo);
    }

    @Test
    void laMismaClaveConOtroPedidoEs422() {
        String k = UUID.randomUUID().toString();
        alta(pedido(1500), k).then().statusCode(201);
        alta(pedido(9999), k).then()
                .statusCode(422).contentType(startsWith(PROBLEMA))
                .body("type", equalTo("/problemas/clave-idempotencia-reutilizada"));
    }

    @Test
    void sinIdempotencyKeyEs400() {
        erp().body(pedido(1500)).post("/pedidos-externos").then()
                .statusCode(400).contentType(startsWith(PROBLEMA))
                .body("type", equalTo("/problemas/clave-idempotencia-invalida"));
    }

    @Test
    void formatoInvalidoEs400ConErroresPorCampo() {
        Map<String, Object> malo = new java.util.HashMap<>(pedido(0));
        malo.remove("direccionEntrega");
        alta(malo, UUID.randomUUID().toString()).then()
                .statusCode(400).contentType(startsWith(PROBLEMA))
                .body("errores.campo", hasItems("importe", "direccionEntrega"));
    }

    @Test
    void sinCredencialesEs401() {
        given().get("/pedidos-externos/1").then().statusCode(401);
    }

    @Test
    void unPedidoInexistenteEs404EnProblemDetails() {
        erp().get("/pedidos-externos/999999999").then()
                .statusCode(404).contentType(startsWith(PROBLEMA))
                .body("type", equalTo("/problemas/pedido-inexistente"));
    }

    @Test
    void cancelarEsIdempotente() {
        int id = alta(pedido(1500), UUID.randomUUID().toString()).then().statusCode(201).extract().path("idPedidoExterno");
        String primero = erp().post("/pedidos-externos/" + id + "/cancelacion").then().statusCode(200).extract().asString();
        String segundo = erp().post("/pedidos-externos/" + id + "/cancelacion").then().statusCode(200).extract().asString();
        assertEquals(primero, segundo);
    }

    @Test
    void seguimientoPublicoSinLoginYConElCodigoEnMinusculas() throws InterruptedException {
        int id = alta(pedido(1500), UUID.randomUUID().toString()).then().statusCode(201).extract().path("idPedidoExterno");
        String codigo = null;
        for (int i = 0; i < 40 && codigo == null; i++) {
            codigo = erp().get("/pedidos-externos/" + id).then().statusCode(200).extract().path("codigoSeguimiento");
            if (codigo == null) {
                Thread.sleep(500);
            }
        }
        assertNotNull(codigo, "el pedido no se sincronizó");
        given().get("/seguimiento/" + codigo.toLowerCase()).then()
                .statusCode(200)
                .body("codigoSeguimiento", equalTo(codigo))
                .body("estado", equalTo("PENDIENTE"))
                .body("$", not(hasKey("importe")));
    }

    @Test
    void urlInexistenteEs404EnProblemDetails() {
        given().get("/no-existe").then().statusCode(404).contentType(startsWith(PROBLEMA));
    }

    @Test
    void webhookConClaveIncorrectaEs401() {
        String transportista = System.getProperty("rabbit.webhook.transportista");
        assumeTrue(transportista != null, "Falta -Drabbit.webhook.transportista");
        given().header("Authorization", "Bearer clave-falsa").contentType("application/json")
                .body(Map.of("codigoSeguimiento", "X", "estado", "ENTREGADO"))
                .post("/transportistas/" + transportista + "/novedades").then()
                .statusCode(401).body("type", equalTo("/problemas/clave-webhook-invalida"));
    }

    @Test
    void webhookConEnvioAjenoEs404YEstadoRaroEs422() {
        String transportista = System.getProperty("rabbit.webhook.transportista");
        String claveWebhook = System.getProperty("rabbit.webhook.clave");
        assumeTrue(transportista != null && claveWebhook != null, "Faltan -Drabbit.webhook.transportista y -Drabbit.webhook.clave");
        given().header("Authorization", "Bearer " + claveWebhook).contentType("application/json")
                .body(Map.of("codigoSeguimiento", "NO-EXISTE", "estado", "ENTREGADO"))
                .post("/transportistas/" + transportista + "/novedades").then().statusCode(404);
        given().header("Authorization", "Bearer " + claveWebhook).contentType("application/json")
                .body(Map.of("codigoSeguimiento", "NO-EXISTE", "estado", "PERDIDO"))
                .post("/transportistas/" + transportista + "/novedades").then().statusCode(422);
    }
}
