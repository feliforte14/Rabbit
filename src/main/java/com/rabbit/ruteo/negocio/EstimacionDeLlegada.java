package com.rabbit.ruteo.negocio;

/**
 * Horario estimado de un pedido en camino (ADR-043), con lo que Rabbit ya
 * sabe: el orden de las paradas de la hoja de ruta recomendada del
 * repartidor (ADR-038). Es lo que hace Amazon ("faltan 4 paradas"), sin ETA
 * por tráfico: no hay geocodificación (ADR-017).
 *
 * Cada parada se estima en un tiempo fijo (viaje + entrega). La franja va
 * de "si todo sale rápido" (solo las paradas de antes) a "con una parada
 * de margen", redondeada a cuartos de hora para no prometer minutos que no
 * se pueden cumplir:
 *
 *   desde = ahora + paradasAntes x minutos            (hacia abajo, nunca antes de ahora)
 *   hasta = ahora + (paradasAntes + 2) x minutos      (hacia arriba)
 *
 * "Nunca antes de ahora": redondear hacia abajo la próxima parada daba una
 * franja que empezaba en el pasado ("entre las 20:15..." a las 20:27). Ahí
 * el desde es la hora actual redondeada a 5 minutos hacia arriba.
 *
 * Los minutos por parada se configuran con una system property de WildFly,
 * leída en cada uso:
 *   rabbit.ruteo.minutos-por-parada   de 5 a 120 (20 por defecto)
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.ruteo.dto.LlegadaEstimadaDTO;
import com.rabbit.ruteo.dto.ParadaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

final class EstimacionDeLlegada {

    static final String PROPIEDAD = "rabbit.ruteo.minutos-por-parada";
    static final int POR_DEFECTO = 20;

    private static final Logger LOG = Logger.getLogger(EstimacionDeLlegada.class.getName());

    private EstimacionDeLlegada() {
    }

    static int minutosPorParada() {
        return interpretar(System.getProperty(PROPIEDAD));
    }

    static int interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return POR_DEFECTO;
        }
        try {
            int minutos = Integer.parseInt(valor.trim());
            if (minutos >= 5 && minutos <= 120) {
                return minutos;
            }
        } catch (NumberFormatException e) {
            // Cae al aviso de abajo.
        }
        LOG.warning("[Ruteo] " + PROPIEDAD + "=" + valor + " no es un número de 5 a 120: se usa " + POR_DEFECTO);
        return POR_DEFECTO;
    }

    /**
     * @return la estimación, o null si el pedido no está en el recorrido
     *         (por ejemplo, no tiene dirección de entrega)
     */
    static LlegadaEstimadaDTO estimar(RecorridoDTO recorrido, Long idPedido, LocalDateTime ahora, int minutosPorParada) {
        List<ParadaDTO> paradas = recorrido.paradas;
        int indice = -1;
        for (int i = 0; i < paradas.size(); i++) {
            ParadaDTO parada = paradas.get(i);
            if (parada.tipo == ParadaDTO.Tipo.ENTREGA && parada.pedidos.contains(idPedido)) {
                indice = i;
                break;
            }
        }
        if (indice < 0) {
            return null;
        }
        LlegadaEstimadaDTO llegada = new LlegadaEstimadaDTO();
        llegada.numeroParada = indice + 1;
        llegada.totalParadas = paradas.size();
        LocalDateTime desde = haciaAbajo(ahora.plusMinutes((long) indice * minutosPorParada));
        llegada.desde = (desde.isBefore(ahora) ? cincoMinutosArriba(ahora) : desde).toLocalTime();
        llegada.hasta = haciaArriba(ahora.plusMinutes((long) (indice + 2) * minutosPorParada)).toLocalTime();
        return llegada;
    }

    private static LocalDateTime haciaAbajo(LocalDateTime hora) {
        LocalDateTime t = hora.truncatedTo(ChronoUnit.MINUTES);
        return t.minusMinutes(t.getMinute() % 15);
    }

    private static LocalDateTime cincoMinutosArriba(LocalDateTime hora) {
        LocalDateTime t = hora.truncatedTo(ChronoUnit.MINUTES);
        int resto = t.getMinute() % 5;
        return resto == 0 && t.equals(hora) ? t : t.plusMinutes(5 - resto);
    }

    private static LocalDateTime haciaArriba(LocalDateTime hora) {
        LocalDateTime abajo = haciaAbajo(hora);
        return abajo.equals(hora) ? abajo : abajo.plusMinutes(15);
    }
}
