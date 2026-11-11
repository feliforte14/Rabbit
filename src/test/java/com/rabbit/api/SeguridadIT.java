package com.rabbit.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import io.restassured.RestAssured;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas de seguridad en vivo contra Rabbit desplegado (criterios del eje D).
 *
 *   mvn verify -Pintegracion
 *
 * No necesitan cuentas: la fuerza bruta usa un usuario que no existe (no
 * bloquea a nadie real) y el XXE va a los servicios SOAP, que no piden login.
 * Opcionales: -Drabbit.base (HTTPS, la API) y -Drabbit.soap.base (HTTP, los
 * servicios SOAP simulados), por defecto los de un WildFly local.
 */
class SeguridadIT {

    private static String soap;

    @BeforeAll
    static void preparar() {
        RestAssured.baseURI = System.getProperty("rabbit.base", "https://localhost:8443/Rabbit");
        RestAssured.basePath = "/api/v1";
        RestAssured.useRelaxedHTTPSValidation();
        soap = System.getProperty("rabbit.soap.base", "http://localhost:8080/Rabbit");
    }

    @Test
    void laFuerzaBrutaContraLaApiTerminaEn429() {
        String usuario = "it-fuerza-bruta-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            given().auth().preemptive().basic(usuario, "clave-" + i).get("/pedidos-externos/1").then()
                    .statusCode(401)
                    .header("WWW-Authenticate", startsWith("Basic"))
                    .contentType(startsWith("application/problem+json"));
        }
        given().auth().preemptive().basic(usuario, "otra").get("/pedidos-externos/1").then()
                .statusCode(429)
                .header("Retry-After", not(equalTo("0")))
                .contentType(startsWith("application/problem+json"))
                .body("type", equalTo("/problemas/demasiados-intentos"));
    }

    @Test
    void sinCredencialesEs401ConProblemDetails() {
        given().get("/pedidos-externos/1").then()
                .statusCode(401)
                .header("WWW-Authenticate", startsWith("Basic"))
                .body("type", equalTo("/problemas/no-autenticado"));
    }

    /**
     * El equivalente a "JWT vencido, con firma alterada o alg=none" (Rabbit no usa JWT: la API
     * es HTTP Basic): cualquier credencial que no sea un Basic válido es 401, nunca 500 ni 200.
     * Ninguna nombra una cuenta real, así que no cuentan para bloquear a nadie.
     */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("credencialesInvalidas")
    void unaCredencialInvalidaOdeOtroEsquemaEs401(String authorization) {
        given().header("Authorization", authorization).get("/pedidos-externos/1").then()
                .statusCode(401)
                .contentType(startsWith("application/problem+json"))
                .body("type", equalTo("/problemas/no-autenticado"));
    }

    // Los JWT se arman acá y no van como texto: no firman nada, pero un escáner de
    // secretos no tiene por qué saberlo.
    static java.util.stream.Stream<String> credencialesInvalidas() {
        return java.util.stream.Stream.of(
                "Bearer " + jwt("{\"alg\":\"none\"}", "{\"sub\":\"demo.erp\"}") + ".",
                "Bearer " + jwt("{\"alg\":\"HS256\"}", "{\"sub\":\"demo.erp\",\"exp\":1}") + ".firma-alterada",
                "Basic !!!no-es-base64!!!",
                "Basic " + base64("sin-dos-puntos"),
                "Basic " + base64(":"),
                "Basic",
                "Digest username=\"demo.erp\"");
    }

    private static String jwt(String cabecera, String cuerpo) {
        return base64Url(cabecera) + "." + base64Url(cuerpo);
    }

    private static String base64(String texto) {
        return java.util.Base64.getEncoder().encodeToString(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String base64Url(String texto) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Errores de la API: Problem Details con el código correcto y sin detalles internos. */
    @ParameterizedTest
    @CsvSource({
        "POST, /seguimiento/RB-AAAAAAAAAA, application/json, 405",
        "GET,  /seguimiento/RB-AAAAAAAAAA, text/csv,         406",
        "GET,  /no-existe,                 application/json, 404",
        "GET,  /seguimiento/..%2F..%2Fetc%2Fpasswd, application/json, 404"})
    void losErroresNoMuestranDetallesInternos(String metodo, String ruta, String accept, int codigo) {
        given().accept(accept).urlEncodingEnabled(false).request(metodo, ruta).then()
                .statusCode(codigo)
                .contentType(startsWith("application/problem+json"))
                .body(not(matchesPattern("(?s).*(Exception|\\bat (com|org|java|jakarta)\\.|org\\.jboss|WFLY|\\.java:\\d).*")));
    }

    /** CORS: un origen ajeno no recibe permiso (ni comodín ni su propio origen reflejado). */
    @Test
    void unOrigenAjenoNoRecibeCabecerasCors() {
        given().header("Origin", "https://otro-sitio.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization")
                .options("/pedidos-externos").then()
                .header("Access-Control-Allow-Origin", nullValue())
                .header("Access-Control-Allow-Credentials", nullValue());
        given().header("Origin", "https://otro-sitio.example").get("/seguimiento/RB-AAAAAAAAAA").then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    /** Cifrado en tránsito: por HTTP, las pantallas de Rabbit redirigen a HTTPS. */
    @Test
    void porHttpRedirigeAHttps() {
        given().baseUri(soap).basePath("").redirects().follow(false).get("/login.xhtml").then()
                .statusCode(302)
                .header("Location", startsWith("https://"));
    }

    /** Cookie de sesión: Secure, HttpOnly y SameSite=Lax; y las cabeceras de seguridad en la respuesta. */
    @Test
    void laCookieDeSesionYLasCabecerasDeSeguridad() {
        given().basePath("").get("/login.xhtml").then()
                .statusCode(200)
                .header("Set-Cookie", matchesPattern("(?i)JSESSIONID=[^;]+;.*\\bsecure\\b.*"))
                .header("Set-Cookie", matchesPattern("(?i).*\\bHttpOnly\\b.*"))
                .header("Set-Cookie", matchesPattern("(?i).*\\bSameSite=Lax\\b.*"))
                .header("Strict-Transport-Security", startsWith("max-age="))
                .header("Content-Security-Policy", containsString("frame-ancestors 'none'"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Frame-Options", equalTo("DENY"));
    }

    /** Una entidad externa que apunta a un archivo local: el parser no tiene que leerlo. */
    @ParameterizedTest
    @ValueSource(strings = {"BancoLegadoService", "TransportistaLegadoService"})
    void elCanalSoapRechazaEntidadesExternas(String servicio, @TempDir Path dir) throws Exception {
        Path secreto = Files.writeString(dir.resolve("secreto.txt"), "SECRETO-XXE-" + UUID.randomUUID());
        String dtd = "<!DOCTYPE e [<!ENTITY xxe SYSTEM \"" + secreto.toUri() + "\">]>";

        given().baseUri(soap).basePath("").contentType("text/xml; charset=utf-8").header("SOAPAction", "\"\"")
                .body(sobre(dtd)).post("/" + servicio).then()
                .statusCode(500)
                .body(containsString("Fault"))
                .body(not(containsString(Files.readString(secreto))));
    }

    /** Expansión de entidades internas ("billion laughs"): también se rechaza antes de expandir. */
    @ParameterizedTest
    @ValueSource(strings = {"BancoLegadoService", "TransportistaLegadoService"})
    void elCanalSoapRechazaLaExpansionDeEntidades(String servicio) {
        String dtd = "<!DOCTYPE e [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
                + "<!ENTITY xxe \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]>";

        given().baseUri(soap).basePath("").contentType("text/xml; charset=utf-8").header("SOAPAction", "\"\"")
                .body(sobre(dtd)).post("/" + servicio).then()
                .statusCode(500)
                .body(containsString("Fault"))
                .body(not(containsString("aaaaaaaaaaaaaaaaaaaa")));
    }

    private static String sobre(String dtd) {
        return "<?xml version=\"1.0\"?>" + dtd
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<x:prueba xmlns:x=\"urn:rabbit:it\"><valor>&xxe;</valor></x:prueba>"
                + "</soap:Body></soap:Envelope>";
    }
}
