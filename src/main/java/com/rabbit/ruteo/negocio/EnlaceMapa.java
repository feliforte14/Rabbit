package com.rabbit.ruteo.negocio;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Link al recorrido de un pedido en Google Maps: los lugares de retiro como
 * paradas intermedias y la dirección de entrega como destino. No lleva
 * origen, así en el celular el recorrido arranca desde donde está el
 * repartidor.
 *
 * Es un link común (sin clave ni API): Rabbit no geocodifica ni guarda
 * coordenadas (ver ADR-017). Las direcciones salen hacia Google solo cuando
 * alguien toca el link.
 */
final class EnlaceMapa {

    private static final String BASE = "https://www.google.com/maps/dir/?api=1&travelmode=driving";

    private EnlaceMapa() {
    }

    /** null si el pedido no tiene dirección de entrega: no hay adónde ir. */
    static String recorrido(List<String> retiros, String entrega) {
        if (entrega == null || entrega.isBlank()) {
            return null;
        }
        StringBuilder url = new StringBuilder(BASE).append("&destination=").append(codificar(entrega));
        List<String> paradas = retiros.stream().filter(r -> r != null && !r.isBlank()).collect(Collectors.toList());
        if (!paradas.isEmpty()) {
            url.append("&waypoints=").append(paradas.stream().map(EnlaceMapa::codificar).collect(Collectors.joining("%7C")));
        }
        return url.toString();
    }

    // Las direcciones vienen sin país ("Av. Corrientes 1234, CABA"): se
    // agrega para que Maps no las busque en otro lado.
    private static String codificar(String direccion) {
        return URLEncoder.encode(direccion.trim() + ", Argentina", StandardCharsets.UTF_8);
    }
}
