package com.rabbit.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import io.restassured.RestAssured;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
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
