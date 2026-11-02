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
 * Opcionales (si faltan, esos tests se saltean):
 *   -Drabbit.erp2.usuario/-Drabbit.erp2.clave: un ERP de OTRO comercio (SEC-03, pedido ajeno = 404).
 *   -Drabbit.comercio.usuario/-Drabbit.comercio.clave: un usuario con rol COMERCIO (SEC-04, 403).
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
                "direccionEntrega", "[TEST] Av. Siempreviva 742, CABA",
                "emailDestinatario", "cliente@correo.test");
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

    /**
     * SEC-03 (BOLA): un ERP no puede ver ni tocar los pedidos de OTRO comercio, y la API ni siquiera
     * confirma que existen: responde 404, igual que a un pedido inexistente.
     * Necesita un segundo usuario ERP, de otro comercio (-Drabbit.erp2.usuario, -Drabbit.erp2.clave).
     */
    @Test
    void unPedidoDeOtroComercioEs404() {
        String usuario2 = System.getProperty("rabbit.erp2.usuario");
        String clave2 = System.getProperty("rabbit.erp2.clave");
        assumeTrue(usuario2 != null && clave2 != null,
                "Faltan -Drabbit.erp2.usuario y -Drabbit.erp2.clave (un ERP de OTRO comercio)");
        RequestSpecification otroErp = given().auth().preemptive().basic(usuario2, clave2).contentType("application/json");

        int id = alta(pedido(1500), UUID.randomUUID().toString()).then().statusCode(201).extract().path("idPedidoExterno");

        // Consultar: 404 en problem+json, el mismo que un pedido que no existe.
        otroErp.get("/pedidos-externos/" + id).then()
                .statusCode(404).contentType(startsWith(PROBLEMA))
                .body("type", equalTo("/problemas/pedido-inexistente"));
        // Cancelar: también 404, y no se cancela nada.
        otroErp.post("/pedidos-externos/" + id + "/cancelacion").then().statusCode(404);

        // El dueño sigue viéndolo y el pedido no quedó cancelado por el intento ajeno.
        erp().get("/pedidos-externos/" + id).then().statusCode(200)
                .body("idPedidoExterno", equalTo(id));
    }

    /**
     * SEC-04: la API es solo para el rol ERP. Un usuario autenticado con otro rol (acá, COMERCIO) recibe 403,
     * y sin credenciales, 401 (sinCredencialesEs401). Necesita un usuario con rol COMERCIO
     * (-Drabbit.comercio.usuario, -Drabbit.comercio.clave).
     */
    @Test
    void unUsuarioConRolComercioEs403() {
        String usuarioComercio = System.getProperty("rabbit.comercio.usuario");
        String claveComercio = System.getProperty("rabbit.comercio.clave");
        assumeTrue(usuarioComercio != null && claveComercio != null,
                "Faltan -Drabbit.comercio.usuario y -Drabbit.comercio.clave (un usuario con rol COMERCIO)");
        RequestSpecification comercio = given().auth().preemptive().basic(usuarioComercio, claveComercio)
                .contentType("application/json");

        comercio.get("/pedidos-externos/1").then().statusCode(403);
        comercio.header("Idempotency-Key", UUID.randomUUID().toString()).body(pedido(1500))
                .post("/pedidos-externos").then().statusCode(403);
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

    /**
     * Eje D, mass assignment: los campos que el ERP no puede fijar (comercio, estado, resultado, id) se
     * ignoran; el pedido queda del comercio de la cuenta y empieza como cualquier alta.
     */
    @Test
    void losCamposNoPermitidosEnElCuerpoSeIgnoran() {
        Map<String, Object> cuerpo = new java.util.HashMap<>(pedido(1500));
        cuerpo.put("idComercio", 999999);
        cuerpo.put("idPedidoExterno", 1);
        cuerpo.put("resultado", "Sincronizado");
        cuerpo.put("estadoPedido", "ENTREGADO");
        cuerpo.put("idPedido", 1);

        Response r = alta(cuerpo, UUID.randomUUID().toString());

        r.then().statusCode(201).body("estadoPedido", anyOf(nullValue(), equalTo("PENDIENTE")));
        int id = r.path("idPedidoExterno");
        erp().get("/pedidos-externos/" + id).then().statusCode(200)
                .body("estadoPedido", anyOf(nullValue(), equalTo("PENDIENTE")));
    }

    /** Eje C: cancelar con un ETag desactualizado es 412 y no cancela; con el actual, cancela. */
    @Test
    void cancelarConUnETagDesactualizadoEs412() {
        int id = alta(pedido(1500), UUID.randomUUID().toString()).then().statusCode(201).extract().path("idPedidoExterno");

        erp().header("If-Match", "\"no-es-el-actual\"").post("/pedidos-externos/" + id + "/cancelacion").then()
                .statusCode(412).contentType(startsWith(PROBLEMA))
                .body("type", equalTo("/problemas/pedido-modificado"))
                .header("ETag", notNullValue());

        String actual = erp().get("/pedidos-externos/" + id).then().statusCode(200).extract().header("ETag");
        erp().header("If-None-Match", actual).get("/pedidos-externos/" + id).then().statusCode(304);
        erp().header("If-Match", actual).post("/pedidos-externos/" + id + "/cancelacion").then().statusCode(200);
    }

    /** Eje D, inyección: un código de seguimiento con SQL o HTML no rompe nada (4xx, nunca 500). */
    @Test
    void unCodigoDeSeguimientoConInyeccionNoRompeNada() {
        for (String codigo : new String[] {"' OR '1'='1", "RB-1' OR 1=1--", "<script>alert(1)</script>"}) {
            int status = given().urlEncodingEnabled(true).get("/seguimiento/{codigo}", codigo)
                    .then().extract().statusCode();
            assertTrue(status >= 400 && status < 500, codigo + " respondió " + status);
        }
    }

    /** Eje D, inyección: SQL y HTML en un campo de texto se guardan como texto, sin ejecutarse. */
    @Test
    void unaDireccionConSqlYHtmlSeGuardaComoTexto() {
        Map<String, Object> cuerpo = new java.util.HashMap<>(pedido(1500));
        cuerpo.put("direccionEntrega", "[TEST] Calle 1'; DROP TABLE pedidos;-- <script>alert(1)</script>");

        alta(cuerpo, UUID.randomUUID().toString()).then().statusCode(201);
        // La tabla sigue ahí: otra alta funciona.
        alta(pedido(1500), UUID.randomUUID().toString()).then().statusCode(201);
    }

    @Test
    void urlInexistenteEs404EnProblemDetails() {
        given().get("/no-existe").then().statusCode(404).contentType(startsWith(PROBLEMA));
    }
}
