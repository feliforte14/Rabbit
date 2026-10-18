package com.rabbit.ruteo.negocio;

/**
 * Lo que no depende de la red al geocodificar con OpenStreetMap (Nominatim,
 * ADR-046): la clave de caché de una dirección, el texto que se le pregunta
 * y la lectura de la respuesta.
 *
 * Las direcciones de Rabbit vienen como las carga cada comercio ("Av.
 * Cabildo 2100, CABA"); Nominatim no conoce "CABA", así que se escribe
 * entera. Se pregunta con el código postal y el país para no caer en una
 * calle homónima de otra provincia.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Nominatim {

    static final String URL_POR_DEFECTO = "https://nominatim.openstreetmap.org/search";

    private Nominatim() {
    }

    /** La clave de caché: misma dirección escrita con otros espacios o mayúsculas, misma clave. */
    static String clave(String direccion, String codigoPostal) {
        String limpia = direccion == null ? "" : direccion.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String cp = codigoPostal == null ? "" : codigoPostal.strip();
        String clave = limpia + "|" + cp;
        return clave.length() <= 300 ? clave : clave.substring(0, 300);
    }

    /** El texto libre que se le pregunta a Nominatim. */
    static String consulta(String direccion, String codigoPostal) {
        String texto = direccion.strip()
                .replaceAll("(?i)\\bC\\.?A\\.?B\\.?A\\.?(?=\\s*(,|$))", "Ciudad Autónoma de Buenos Aires");
        if (codigoPostal != null && !codigoPostal.isBlank() && !texto.contains(codigoPostal.strip())) {
            texto += ", " + codigoPostal.strip();
        }
        return texto + ", Argentina";
    }

    /** La URL completa de la búsqueda (un resultado, solo Argentina). */
    static String url(String base, String direccion, String codigoPostal) {
        return base + "?format=jsonv2&limit=1&countrycodes=ar&q="
                + URLEncoder.encode(consulta(direccion, codigoPostal), StandardCharsets.UTF_8);
    }

    /**
     * {latitud, longitud} del primer resultado, o null si no encontró nada.
     *
     * @throws IllegalArgumentException si la respuesta no es lo esperado
     */
    static double[] leer(String json) {
        String texto = json == null ? "" : json.strip();
        if (!texto.startsWith("[")) {
            throw new IllegalArgumentException("Respuesta de Nominatim inesperada: " + recortar(texto));
        }
        if (texto.replaceAll("\\s", "").equals("[]")) {
            return null;
        }
        // Nominatim manda lat y lon como texto: "lat":"-34.5621","lon":"-58.4563".
        Matcher lat = LATITUD.matcher(texto);
        Matcher lon = LONGITUD.matcher(texto);
        if (!lat.find() || !lon.find()) {
            throw new IllegalArgumentException("Respuesta de Nominatim sin lat/lon: " + recortar(texto));
        }
        return new double[] { Double.parseDouble(lat.group(1)), Double.parseDouble(lon.group(1)) };
    }

    private static final Pattern LATITUD = Pattern.compile("\"lat\"\\s*:\\s*\"(-?\\d+(?:\\.\\d+)?)\"");
    private static final Pattern LONGITUD = Pattern.compile("\"lon\"\\s*:\\s*\"(-?\\d+(?:\\.\\d+)?)\"");

    private static String recortar(String texto) {
        return texto.length() <= 120 ? texto : texto.substring(0, 120) + "...";
    }
}
