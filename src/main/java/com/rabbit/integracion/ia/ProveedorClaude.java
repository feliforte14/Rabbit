package com.rabbit.integracion.ia;

/**
 * Proveedor de IA: la API de mensajes de Claude (Anthropic) por HTTPS.
 *
 * Configuración (nada va en el repositorio; la clave es un secreto):
 *   ANTHROPIC_API_KEY o rabbit.ia.clave   la clave de la API (sin ella no hay IA)
 *   rabbit.ia.modelo                      por defecto claude-haiku-5-5 (rápido y barato: alcanza para clasificar)
 *   rabbit.ia.url                         por defecto https://api.anthropic.com/v1/messages (los tests apuntan a
 *                                         un servidor propio)
 *
 * TIMEOUT de 5 s en total (conexión y respuesta), el mismo que el banco: la IA nunca frena una operación.
 */

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ProveedorClaude implements ProveedorIa {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final String URL_POR_DEFECTO = "https://api.anthropic.com/v1/messages";
    static final String MODELO_POR_DEFECTO = "claude-haiku-5-5";

    private final String clave;
    private final Duration timeout;
    private final HttpClient http;

    public ProveedorClaude(String clave) {
        this(clave, TIMEOUT);
    }

    // Para los tests: un timeout corto, así no hay que esperar 5 s de verdad.
    ProveedorClaude(String clave, Duration timeout) {
        this.clave = clave;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /** La clave de la API, o null si no está configurada. */
    static String claveConfigurada() {
        String clave = System.getProperty("rabbit.ia.clave");
        if (clave == null || clave.isBlank()) {
            clave = System.getenv("ANTHROPIC_API_KEY");
        }
        return clave == null || clave.isBlank() ? null : clave.trim();
    }

    @Override
    public String completar(String consigna, String texto) throws IaNoDisponibleException {
        String modelo = System.getProperty("rabbit.ia.modelo", MODELO_POR_DEFECTO);
        String url = System.getProperty("rabbit.ia.url", URL_POR_DEFECTO);
        String cuerpo = Json.createObjectBuilder()
                .add("model", modelo)
                .add("max_tokens", 40)
                .add("system", consigna)
                .add("messages", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("role", "user").add("content", texto)))
                .build().toString();
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("content-type", "application/json")
                .header("x-api-key", clave)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        try {
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new IaNoDisponibleException("El proveedor de IA respondió " + respuesta.statusCode());
            }
            return textoDe(respuesta.body());
        } catch (IOException e) {
            throw new IaNoDisponibleException("El proveedor de IA no respondió a tiempo", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IaNoDisponibleException("Se interrumpió la consulta a la IA", e);
        }
    }

    // {"content":[{"type":"text","text":"..."}], ...}
    static String textoDe(String json) throws IaNoDisponibleException {
        try (var lector = Json.createReader(new StringReader(json))) {
            JsonObject raiz = lector.readObject();
            JsonArray contenido = raiz.getJsonArray("content");
            if (contenido == null || contenido.isEmpty()) {
                throw new IaNoDisponibleException("La respuesta de la IA no trae contenido");
            }
            return contenido.getJsonObject(0).getString("text");
        } catch (RuntimeException e) {
            throw new IaNoDisponibleException("La respuesta de la IA no se puede leer", e);
        }
    }
}
