package com.rabbit.integracion.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El proveedor de Claude contra un servidor HTTP propio (en la misma JVM): sin red ni clave real. */
class ProveedorClaudeTest {

    private HttpServer servidor;
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private final AtomicReference<String> claveRecibida = new AtomicReference<>();
    private final AtomicReference<String> versionRecibida = new AtomicReference<>();
    private volatile int estado = 200;
    private volatile String respuesta = "{\"content\":[{\"type\":\"text\",\"text\":\"POSITIVO;TRATO\"}]}";
    private volatile long demoraMs = 0;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/messages", intercambio -> {
            cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            claveRecibida.set(intercambio.getRequestHeaders().getFirst("x-api-key"));
            versionRecibida.set(intercambio.getRequestHeaders().getFirst("anthropic-version"));
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            try {
                intercambio.sendResponseHeaders(estado, bytes.length);
                intercambio.getResponseBody().write(bytes);
            } catch (IOException e) {
                // El cliente ya se fue por el timeout: es lo que prueba el test.
            } finally {
                intercambio.close();
            }
        });
        servidor.start();
        System.setProperty("rabbit.ia.url", "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/messages");
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        System.clearProperty("rabbit.ia.url");
        System.clearProperty("rabbit.ia.modelo");
    }

    @Test
    void mandaLaConsignaElTextoLaClaveYElModeloYDevuelveLoQueContesta() throws Exception {
        System.setProperty("rabbit.ia.modelo", "modelo-de-prueba");

        String texto = new ProveedorClaude("clave-de-prueba").completar("la consigna", "el texto");

        assertEquals("POSITIVO;TRATO", texto);
        assertEquals("clave-de-prueba", claveRecibida.get());
        assertEquals("2023-06-01", versionRecibida.get());
        assertTrue(cuerpoRecibido.get().contains("\"model\":\"modelo-de-prueba\""));
        assertTrue(cuerpoRecibido.get().contains("\"system\":\"la consigna\""));
        assertTrue(cuerpoRecibido.get().contains("\"content\":\"el texto\""));
    }

    @Test
    void usaElModeloPorDefectoSiNoSeConfigura() throws Exception {
        new ProveedorClaude("k").completar("c", "t");
        assertTrue(cuerpoRecibido.get().contains("\"model\":\"" + ProveedorClaude.MODELO_POR_DEFECTO + "\""));
    }

    @Test
    void unaRespuestaQueNoEsCorrectaEsUnaFallaDelProveedor() {
        estado = 500;
        assertThrows(IaNoDisponibleException.class, () -> new ProveedorClaude("k").completar("c", "t"));
        estado = 401;
        assertThrows(IaNoDisponibleException.class, () -> new ProveedorClaude("k").completar("c", "t"));
    }

    @Test
    void unaRespuestaConUnCuerpoInesperadoEsUnaFalla() {
        for (String rara : new String[] {"no es json", "{}", "{\"content\":[]}", "{\"content\":[{\"type\":\"text\"}]}", "[]"}) {
            respuesta = rara;
            assertThrows(IaNoDisponibleException.class, () -> new ProveedorClaude("k").completar("c", "t"), rara);
        }
    }

    @Test
    void sinRespuestaATiempoSeCortaYSeCaeEnElFallback() {
        demoraMs = 2000;
        long t0 = System.nanoTime();
        IaNoDisponibleException e = assertThrows(IaNoDisponibleException.class,
                () -> new ProveedorClaude("k", Duration.ofMillis(300)).completar("c", "t"));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 1500, "esperó " + ms + " ms: no cortó por el timeout");
        assertTrue(e.getMessage().contains("a tiempo"));
    }

    @Test
    void unServidorQueNoEstaEsUnaFalla() {
        servidor.stop(0);
        assertThrows(IaNoDisponibleException.class, () -> new ProveedorClaude("k", Duration.ofMillis(500)).completar("c", "t"));
    }

    @Test
    void elTimeoutPorDefectoEsDeCincoSegundos() {
        assertEquals(Duration.ofSeconds(5), ProveedorClaude.TIMEOUT);
    }
}
