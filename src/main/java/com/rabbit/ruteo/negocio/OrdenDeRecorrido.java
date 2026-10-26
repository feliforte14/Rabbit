package com.rabbit.ruteo.negocio;

/**
 * Arma la hoja de ruta recomendada del repartidor (ADR-038, ADR-046) con
 * todas sus entregas en curso:
 *
 *   0. Si todavía tiene pedidos por retirar, la salida del depósito Rabbit
 *      (DepositoRabbit): el camión sale de ahí a buscarlos.
 *   1. Los retiros que le faltan (pedidos CONFIRMADO), desde la salida y sin
 *      repetir lugares: así ninguna entrega queda antes de su retiro.
 *   2. Después las entregas, desde el punto de partida: el último retiro o,
 *      si ya retiró todo, el depósito o punto de picking de donde salieron.
 *      Siempre va a la entrega más cercana a la anterior. Varios pedidos a
 *      la misma dirección son una sola parada.
 *
 * "Más cercana" es la distancia en línea recta entre las coordenadas de
 * OpenStreetMap (ADR-046, las completa RuteoService desde la caché). Las
 * paradas que todavía no se ubicaron en el mapa van después, encadenadas
 * por cercanía de código postal (los CP numéricamente cercanos suelen ser
 * barrios vecinos; en CABA van de 1000 a 1499), y al final las que no
 * tienen CP, en el orden en que se asignaron.
 *
 * Es una aproximación a propósito: línea recta y vecino más cercano, no
 * calles ni el mejor orden posible. Alcanza para no cruzar la ciudad de
 * punta a punta entre dos entregas.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.ParadaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OrdenDeRecorrido {

    private static final double RADIO_TIERRA_KM = 6371.0;

    private OrdenDeRecorrido() {
    }

    static RecorridoDTO recomendar(List<HojaDeRutaDTO> entregas, ParadaDTO salida) {
        Map<String, ParadaDTO> retiros = new LinkedHashMap<>();
        Map<String, ParadaDTO> destinos = new LinkedHashMap<>();
        ParadaDTO origen = null;
        RecorridoDTO recorrido = new RecorridoDTO();
        for (HojaDeRutaDTO hoja : entregas) {
            if (hoja.paradasRetiro != null) {
                for (ParadaDTO retiro : hoja.paradasRetiro) {
                    if (origen == null) {
                        origen = retiro;
                    }
                    if ("CONFIRMADO".equals(hoja.estado)) {
                        sumar(retiros, retiro.tipo, retiro.descripcion, retiro.direccion, retiro.codigoPostal,
                                retiro.latitud, retiro.longitud, hoja.idPedido);
                    }
                }
            }
            if (hoja.direccionEntrega == null || hoja.direccionEntrega.isBlank()) {
                recorrido.sinDireccion.add(hoja.idPedido);
                continue;
            }
            sumar(destinos, ParadaDTO.Tipo.ENTREGA, hoja.direccionEntrega, hoja.direccionEntrega,
                    hoja.codigoPostalEntrega, hoja.latitudEntrega, hoja.longitudEntrega, hoja.idPedido);
        }

        // El día arranca en el depósito Rabbit, mientras quede algo por retirar.
        boolean porRetirar = entregas.stream().anyMatch(h -> "CONFIRMADO".equals(h.estado));
        if (porRetirar && salida != null) {
            recorrido.paradas.add(salida);
        }
        List<ParadaDTO> ordenRetiros = encadenar(new ArrayList<>(retiros.values()),
                porRetirar && salida != null ? salida : (ParadaDTO) null);
        recorrido.paradas.addAll(ordenRetiros);

        // Punto de partida de las entregas: el último retiro, o de donde
        // salieron los pedidos que ya lleva.
        ParadaDTO partida = !ordenRetiros.isEmpty() ? ordenRetiros.get(ordenRetiros.size() - 1) : origen;
        recorrido.paradas.addAll(encadenar(new ArrayList<>(destinos.values()), partida));

        // La salida sin dirección no va a Maps: el primer tramo arranca donde esté el repartidor.
        recorrido.tramos = EnlaceMapa.tramos(recorrido.paradas.stream()
                .map(p -> p.direccion).filter(d -> d != null && !d.isBlank()).toList());
        return recorrido;
    }

    /**
     * Vecino más cercano: primero las ubicadas en el mapa, por distancia;
     * después las demás, por código postal. Sin punto de partida (o sin
     * ubicar), arranca por la de CP más cercano al de partida.
     */
    static List<ParadaDTO> encadenar(List<ParadaDTO> paradas, ParadaDTO desde) {
        List<ParadaDTO> ubicadas = new ArrayList<>();
        List<ParadaDTO> resto = new ArrayList<>();
        for (ParadaDTO p : paradas) {
            (p.isUbicada() ? ubicadas : resto).add(p);
        }
        List<ParadaDTO> orden = new ArrayList<>();
        ParadaDTO actual = desde;
        if (!ubicadas.isEmpty() && (actual == null || !actual.isUbicada())) {
            ParadaDTO primera = encadenar(ubicadas, actual != null ? numeroCp(actual) : null).get(0);
            ubicadas.remove(primera);
            orden.add(primera);
            actual = primera;
        }
        while (!ubicadas.isEmpty()) {
            ParadaDTO siguiente = ubicadas.get(0);
            for (ParadaDTO candidata : ubicadas) {
                if (distanciaKm(actual, candidata) < distanciaKm(actual, siguiente)) {
                    siguiente = candidata;
                }
            }
            ubicadas.remove(siguiente);
            orden.add(siguiente);
            actual = siguiente;
        }
        orden.addAll(encadenar(resto, actual != null ? numeroCp(actual) : null));
        return orden;
    }

    // Vecino más cercano por CP. Sin punto de partida, arranca por el CP
    // más bajo (en una recta, eso deja las paradas en orden creciente).
    static List<ParadaDTO> encadenar(List<ParadaDTO> paradas, Integer desde) {
        List<ParadaDTO> conCp = new ArrayList<>();
        List<ParadaDTO> sinCp = new ArrayList<>();
        for (ParadaDTO p : paradas) {
            (numeroCp(p) != null ? conCp : sinCp).add(p);
        }
        List<ParadaDTO> orden = new ArrayList<>();
        Integer actual = desde;
        while (!conCp.isEmpty()) {
            ParadaDTO siguiente = conCp.get(0);
            for (ParadaDTO candidata : conCp) {
                if (actual == null ? numeroCp(candidata) < numeroCp(siguiente)
                        : Math.abs(numeroCp(candidata) - actual) < Math.abs(numeroCp(siguiente) - actual)) {
                    siguiente = candidata;
                }
            }
            conCp.remove(siguiente);
            orden.add(siguiente);
            actual = numeroCp(siguiente);
        }
        orden.addAll(sinCp);
        return orden;
    }

    /** Distancia en línea recta (haversine), en km. */
    static double distanciaKm(ParadaDTO a, ParadaDTO b) {
        double lat1 = Math.toRadians(a.latitud);
        double lat2 = Math.toRadians(b.latitud);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitud - a.longitud);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RADIO_TIERRA_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private static void sumar(Map<String, ParadaDTO> paradas, ParadaDTO.Tipo tipo, String descripcion,
                              String direccion, String codigoPostal, Double latitud, Double longitud, Long idPedido) {
        String clave = direccion.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        ParadaDTO existente = paradas.get(clave);
        if (existente == null) {
            ParadaDTO nueva = new ParadaDTO(tipo, descripcion, direccion, codigoPostal, idPedido);
            nueva.latitud = latitud;
            nueva.longitud = longitud;
            paradas.put(clave, nueva);
        } else if (!existente.pedidos.contains(idPedido)) {
            existente.pedidos.add(idPedido);
        }
    }

    private static Integer numeroCp(ParadaDTO parada) {
        String cp = parada.codigoPostal;
        if (cp == null || !cp.trim().matches("\\d{4}")) {
            return null;
        }
        return Integer.valueOf(cp.trim());
    }
}
