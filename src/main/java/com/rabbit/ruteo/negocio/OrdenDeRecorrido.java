package com.rabbit.ruteo.negocio;

/**
 * Arma la hoja de ruta recomendada del repartidor (ADR-038) con todas sus
 * entregas en curso:
 *
 *   1. Primero los retiros que le faltan (pedidos CONFIRMADO), sin repetir
 *      lugares: así ninguna entrega queda antes de su retiro.
 *   2. Después las entregas, encadenadas por cercanía de código postal:
 *      desde el último retiro (o desde el CP más bajo), siempre la que
 *      tenga el CP más cercano a la anterior. Varios pedidos a la misma
 *      dirección son una sola parada.
 *
 * Es una aproximación a propósito: Rabbit no geocodifica (ADR-017), así
 * que no hay distancias reales. Los CP numéricamente cercanos suelen ser
 * barrios vecinos (en CABA van de 1000 a 1499), y eso alcanza para no
 * cruzar la ciudad de punta a punta entre dos entregas. Las paradas sin CP
 * van al final, en el orden en que se asignaron.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.ParadaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OrdenDeRecorrido {

    private OrdenDeRecorrido() {
    }

    static RecorridoDTO recomendar(List<HojaDeRutaDTO> entregas) {
        Map<String, ParadaDTO> retiros = new LinkedHashMap<>();
        Map<String, ParadaDTO> destinos = new LinkedHashMap<>();
        RecorridoDTO recorrido = new RecorridoDTO();
        for (HojaDeRutaDTO hoja : entregas) {
            if ("CONFIRMADO".equals(hoja.estado) && hoja.paradasRetiro != null) {
                for (ParadaDTO retiro : hoja.paradasRetiro) {
                    sumar(retiros, retiro.tipo, retiro.descripcion, retiro.direccion, retiro.codigoPostal, hoja.idPedido);
                }
            }
            if (hoja.direccionEntrega == null || hoja.direccionEntrega.isBlank()) {
                recorrido.sinDireccion.add(hoja.idPedido);
                continue;
            }
            sumar(destinos, ParadaDTO.Tipo.ENTREGA, hoja.direccionEntrega, hoja.direccionEntrega,
                    hoja.codigoPostalEntrega, hoja.idPedido);
        }

        List<ParadaDTO> ordenRetiros = new ArrayList<>(retiros.values());
        ordenRetiros.sort(Comparator.comparing(OrdenDeRecorrido::numeroCp,
                Comparator.nullsLast(Comparator.naturalOrder())));
        recorrido.paradas.addAll(ordenRetiros);

        Integer desde = ordenRetiros.stream().map(OrdenDeRecorrido::numeroCp)
                .filter(cp -> cp != null).reduce((a, b) -> b).orElse(null);
        recorrido.paradas.addAll(encadenar(new ArrayList<>(destinos.values()), desde));

        recorrido.tramos = EnlaceMapa.tramos(recorrido.paradas.stream().map(p -> p.direccion).toList());
        return recorrido;
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

    private static void sumar(Map<String, ParadaDTO> paradas, ParadaDTO.Tipo tipo, String descripcion,
                              String direccion, String codigoPostal, Long idPedido) {
        String clave = direccion.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        ParadaDTO existente = paradas.get(clave);
        if (existente == null) {
            paradas.put(clave, new ParadaDTO(tipo, descripcion, direccion, codigoPostal, idPedido));
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
