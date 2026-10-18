package com.rabbit.ruteo.negocio;

/**
 * Ubica en el mapa las direcciones de las entregas en curso (ADR-046), con
 * OpenStreetMap (Nominatim), para ordenar la hoja de ruta por distancia.
 *
 * EN SEGUNDO PLANO: un timer cada minuto busca las direcciones de entrega
 * y de retiro (depósito o punto de picking) de los pedidos en curso que
 * todavía no están en la caché (CacheDeCoordenadas) y las consulta. La
 * hoja de ruta solo lee la caché: si una dirección todavía no se ubicó, esa
 * parada se ordena por código postal como antes. Así la pantalla del
 * repartidor nunca espera a un servicio externo.
 *
 * REGLAS DE USO DE NOMINATIM: como mucho una consulta por segundo, con un
 * User-Agent que identifique a la aplicación, y guardando los resultados
 * (no se pregunta dos veces lo mismo). Hasta 20 direcciones por pasada. Si
 * el servicio falla, se corta la pasada y se reintenta al minuto siguiente.
 *
 * Se configura con system properties, leídas en cada pasada:
 *   rabbit.geocodificacion       "ninguna" la apaga (todo por código postal)
 *   rabbit.geocodificacion.url   otro servidor Nominatim (uno propio, por ejemplo)
 *
 * Las direcciones salen de Rabbit hacia OpenStreetMap (solo la dirección y
 * el código postal, nunca quién recibe).
 */

import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.ParadaDTO;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@RunAs("OPERADOR")
@PermitAll
public class Geocodificador {

    private static final Logger LOG = Logger.getLogger(Geocodificador.class.getName());

    static final String PROPIEDAD = "rabbit.geocodificacion";
    static final String PROPIEDAD_URL = "rabbit.geocodificacion.url";
    private static final int POR_PASADA = 20;
    private static final long PAUSA_MS = 1100;
    private static final String USER_AGENT = "Rabbit/1.0 (TP de logistica de ultima milla, UADE)";

    @Inject
    private IRuteo ruteo;

    @Inject
    private CacheDeCoordenadas cache;

    private final AtomicBoolean corriendo = new AtomicBoolean();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Schedule(hour = "*", minute = "*", second = "15", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void cadaMinuto() {
        if ("ninguna".equalsIgnoreCase(System.getProperty(PROPIEDAD, "").strip())
                || !corriendo.compareAndSet(false, true)) {
            return;
        }
        try {
            ubicarPendientes();
        } catch (RuntimeException e) {
            LOG.warning("[Ruteo][Mapa] No se pudieron ubicar las direcciones: " + e.getMessage());
        } finally {
            corriendo.set(false);
        }
    }

    private void ubicarPendientes() {
        // clave -> {dirección, código postal}
        Map<String, String[]> direcciones = new LinkedHashMap<>();
        for (HojaDeRutaDTO hoja : ruteo.listarEntregasEnCurso()) {
            if (hoja.paradasRetiro != null) {
                for (ParadaDTO retiro : hoja.paradasRetiro) {
                    sumar(direcciones, retiro.direccion, retiro.codigoPostal);
                }
            }
            sumar(direcciones, hoja.direccionEntrega, hoja.codigoPostalEntrega);
        }
        Set<String> faltan = cache.faltantes(direcciones.keySet());
        int consultadas = 0;
        String base = System.getProperty(PROPIEDAD_URL, Nominatim.URL_POR_DEFECTO).strip();
        for (String clave : faltan) {
            if (consultadas == POR_PASADA) {
                break;
            }
            if (consultadas > 0) {
                esperar();
            }
            String[] d = direcciones.get(clave);
            double[] coordenadas;
            try {
                coordenadas = consultar(base, d[0], d[1]);
            } catch (IOException | IllegalArgumentException e) {
                LOG.warning("[Ruteo][Mapa] Nominatim no respondió bien (" + e.getMessage()
                        + "): se reintenta en la próxima pasada");
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            cache.guardar(clave, coordenadas);
            consultadas++;
            LOG.info("[Ruteo][Mapa] " + d[0] + (coordenadas != null
                    ? String.format(java.util.Locale.ROOT, " -> %.5f, %.5f", coordenadas[0], coordenadas[1])
                    : ": no se encontró"));
        }
    }

    private double[] consultar(String base, String direccion, String codigoPostal)
            throws IOException, InterruptedException {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(Nominatim.url(base, direccion, codigoPostal)))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "es")
                .GET().build();
        HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() != 200) {
            throw new IOException("HTTP " + respuesta.statusCode());
        }
        return Nominatim.leer(respuesta.body());
    }

    private static void sumar(Map<String, String[]> direcciones, String direccion, String codigoPostal) {
        if (direccion != null && !direccion.isBlank()) {
            direcciones.putIfAbsent(Nominatim.clave(direccion, codigoPostal), new String[] { direccion, codigoPostal });
        }
    }

    private static void esperar() {
        try {
            Thread.sleep(PAUSA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
