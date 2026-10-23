package com.rabbit.pedidos.negocio;

/**
 * Calcula el tablero de indicadores (ADR-044) a partir de los pedidos
 * recibidos en un período. No hace falta guardar nada nuevo: todo sale de
 * columnas que ya tiene el pedido (fechaCreacion, fechaEntrega,
 * idRepartidor) más la fecha prometida y los intentos (ADR-041 y 042).
 *
 * Se calcula en memoria sobre los pedidos del período y no con consultas
 * agregadas: son pocos miles como mucho, y así la regla de cada indicador
 * queda escrita en un solo lugar y se prueba sin base de datos.
 */

import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.dto.IndicadoresDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class CalculoDeIndicadores {

    private CalculoDeIndicadores() {
    }

    static IndicadoresDTO calcular(List<Pedido> pedidos, LocalDate desde, LocalDate hasta, long enEsperaAhora) {
        return calcular(pedidos, desde, hasta, enEsperaAhora, Map.of());
    }

    /** @param estrellasPorPedido la calificación (1 a 5) de cada pedido calificado, por ID de pedido */
    static IndicadoresDTO calcular(List<Pedido> pedidos, LocalDate desde, LocalDate hasta, long enEsperaAhora,
                                   Map<Long, Integer> estrellasPorPedido) {
        IndicadoresDTO dto = new IndicadoresDTO();
        dto.desde = desde;
        dto.hasta = hasta;
        dto.enEsperaAhora = enEsperaAhora;

        Map<Long, IndicadoresDTO.PorRepartidor> porRepartidor = new LinkedHashMap<>();
        Map<Long, long[]> minutosPorRepartidor = new LinkedHashMap<>();
        Map<String, Integer> motivos = new TreeMap<>();
        Map<LocalDate, IndicadoresDTO.Dia> dias = new LinkedHashMap<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            dias.put(d, new IndicadoresDTO.Dia(d));
        }
        long minutosTotales = 0;
        long entregasConDuracion = 0;

        for (Pedido p : pedidos) {
            dto.recibidos++;
            EstadoPedido estado = p.getEstado();
            // Con visitas fallidas, o devuelto al comercio sin visita (paquete dañado, ADR-045).
            if (p.getMotivoEntregaFallida() != null
                    && (p.getIntentosFallidos() > 0 || estado == EstadoPedido.DEVUELTO_AL_COMERCIO)) {
                motivos.merge(p.getMotivoEntregaFallida().getDescripcion(), 1, Integer::sum);
            }
            IndicadoresDTO.PorRepartidor fila = p.getIdRepartidor() == null ? null
                    : porRepartidor.computeIfAbsent(p.getIdRepartidor(), id -> {
                        IndicadoresDTO.PorRepartidor nueva = new IndicadoresDTO.PorRepartidor();
                        nueva.idRepartidor = id;
                        return nueva;
                    });
            switch (estado) {
                case PENDIENTE -> dto.pendientes++;
                case CONFIRMADO, EN_CAMINO -> dto.enCurso++;
                case CANCELADO -> dto.cancelados++;
                case DEVUELTO_AL_COMERCIO -> {
                    dto.devueltos++;
                    if (fila != null) {
                        fila.terminados++;
                    }
                }
                case ENTREGADO -> {
                    dto.entregados++;
                    Integer estrellas = p.getId() == null ? null : estrellasPorPedido.get(p.getId());
                    if (estrellas != null) {
                        dto.calificaciones++;
                        dto.sumaEstrellas += estrellas;
                        if (fila != null) {
                            fila.calificaciones++;
                            fila.sumaEstrellas += estrellas;
                        }
                    }
                    boolean primerIntento = p.getIntentosFallidos() == 0;
                    Boolean aTiempo = aTiempo(p);
                    Long minutos = minutosHastaEntregar(p);
                    if (primerIntento) {
                        dto.alPrimerIntento++;
                    }
                    if (aTiempo != null) {
                        dto.entregadosConPromesa++;
                        dto.entregadosATiempo += aTiempo ? 1 : 0;
                    }
                    IndicadoresDTO.Dia dia = p.getFechaEntrega() == null ? null
                            : dias.get(p.getFechaEntrega().toLocalDate());
                    if (dia != null) {
                        if (aTiempo == null) {
                            dia.sinPromesa++;
                        } else if (aTiempo) {
                            dia.aTiempo++;
                        } else {
                            dia.fueraDeTermino++;
                        }
                    }
                    if (minutos != null) {
                        minutosTotales += minutos;
                        entregasConDuracion++;
                    }
                    if (fila != null) {
                        fila.entregas++;
                        fila.terminados++;
                        fila.alPrimerIntento += primerIntento ? 1 : 0;
                        if (aTiempo != null) {
                            fila.conPromesa++;
                            fila.aTiempo += aTiempo ? 1 : 0;
                        }
                        if (minutos != null) {
                            long[] acumulado = minutosPorRepartidor.computeIfAbsent(fila.idRepartidor, id -> new long[2]);
                            acumulado[0] += minutos;
                            acumulado[1]++;
                        }
                    }
                }
            }
        }

        dto.horasPromedioEntrega = horas(minutosTotales, entregasConDuracion);
        for (IndicadoresDTO.PorRepartidor fila : porRepartidor.values()) {
            long[] acumulado = minutosPorRepartidor.get(fila.idRepartidor);
            fila.horasPromedio = acumulado == null ? null : horas(acumulado[0], acumulado[1]);
        }
        // Solo los que terminaron algo, el que más entregó primero.
        List<IndicadoresDTO.PorRepartidor> filas = new ArrayList<>(porRepartidor.values());
        filas.removeIf(f -> f.terminados == 0);
        filas.sort(Comparator.comparingInt((IndicadoresDTO.PorRepartidor f) -> f.entregas).reversed());
        dto.porRepartidor = filas;
        dto.porDia = new ArrayList<>(dias.values());
        motivos.forEach((descripcion, cantidad) -> dto.motivosFallidos.add(new IndicadoresDTO.Motivo(descripcion, cantidad)));
        dto.motivosFallidos.sort(Comparator.comparingInt(IndicadoresDTO.Motivo::getCantidad).reversed());
        return dto;
    }

    /** Si se entregó el día prometido o antes; null si no tenía promesa o no se sabe cuándo se entregó. */
    static Boolean aTiempo(Pedido p) {
        if (p.getFechaPrometida() == null || p.getFechaEntrega() == null) {
            return null;
        }
        return !p.getFechaEntrega().toLocalDate().isAfter(p.getFechaPrometida());
    }

    private static Long minutosHastaEntregar(Pedido p) {
        if (p.getFechaCreacion() == null || p.getFechaEntrega() == null) {
            return null;
        }
        return Math.max(0, Duration.between(p.getFechaCreacion(), p.getFechaEntrega()).toMinutes());
    }

    private static BigDecimal horas(long minutos, long cantidad) {
        if (cantidad == 0) {
            return null;
        }
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(cantidad * 60), 1, RoundingMode.HALF_UP);
    }
}
