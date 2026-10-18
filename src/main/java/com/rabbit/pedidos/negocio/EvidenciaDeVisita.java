package com.rabbit.pedidos.negocio;

/**
 * Valida y ordena la evidencia de una visita fallida (ADR-045): el
 * comentario, las fotos y la ubicación del repartidor.
 *
 * - Comentario: hasta 500 caracteres; obligatorio con los motivos que lo
 *   piden (rechazo y "Otro", ver MotivoEntregaFallida.isPideComentario).
 * - Fotos: hasta 3, JPEG/PNG/WEBP de hasta 5 MB cada una (las valida
 *   Remitos por su contenido); al menos una con un paquete dañado.
 * - Ubicación: la del celular en ese momento si la mandó y es válida; si
 *   no, la última que el repartidor compartió mientras repartía (ADR-035),
 *   si es reciente. Si no hay ninguna, la visita se registra igual: no se
 *   le puede trabar el trabajo al repartidor por falta de señal.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.datos.model.OrigenUbicacion;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class EvidenciaDeVisita {

    static final int MAXIMO_FOTOS = 3;
    static final int LARGO_COMENTARIO = 500;
    // Una ubicación compartida más vieja que esto ya no dice dónde estaba.
    static final int MINUTOS_ULTIMA_COMPARTIDA = 10;
    // Más error que esto no ubica una casa (el celular sin GPS da kilómetros).
    static final double PRECISION_MAXIMA_METROS = 5000;

    private EvidenciaDeVisita() {
    }

    /** Una foto ya validada. */
    record Foto(byte[] imagen, String tipo) {
    }

    /** Dónde estaba el repartidor. precisionMetros puede ser null. */
    record Ubicacion(double latitud, double longitud, Integer precisionMetros, OrigenUbicacion origen,
                     LocalDateTime fecha) {
    }

    /**
     * El comentario sin espacios de más, o null si no escribió nada.
     *
     * @throws ValidacionException si el motivo lo pide y falta, o es muy largo
     */
    static String comentario(MotivoEntregaFallida motivo, String texto) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty()) {
            if (motivo.isPideComentario()) {
                throw new ValidacionException(motivo == MotivoEntregaFallida.RECHAZADO
                        ? "Contá por qué lo rechazó (dañado, no lo pidió, llegó tarde...)"
                        : "Contá qué pasó");
            }
            return null;
        }
        if (limpio.length() > LARGO_COMENTARIO) {
            throw new ValidacionException("El comentario no puede tener más de " + LARGO_COMENTARIO + " caracteres");
        }
        return limpio;
    }

    /**
     * Las fotos validadas, sin las vacías (un campo de foto que no se usó).
     *
     * @throws ValidacionException si son más de 3, alguna no es una imagen
     *         válida, o el motivo pide foto y no hay ninguna
     */
    static List<Foto> fotos(MotivoEntregaFallida motivo, List<byte[]> cargadas) {
        List<Foto> fotos = new ArrayList<>();
        if (cargadas != null) {
            for (byte[] imagen : cargadas) {
                if (imagen == null || imagen.length == 0) {
                    continue;
                }
                if (fotos.size() == MAXIMO_FOTOS) {
                    throw new ValidacionException("Podés subir hasta " + MAXIMO_FOTOS + " fotos");
                }
                fotos.add(new Foto(imagen, Remitos.tipoDeImagen(imagen, "La foto " + (fotos.size() + 1))));
            }
        }
        if (fotos.isEmpty() && motivo.isPideFoto()) {
            throw new ValidacionException("Sacale al menos una foto al paquete dañado: es la prueba para el comercio");
        }
        return fotos;
    }

    /**
     * La ubicación de la visita, o null si no hay ninguna confiable.
     *
     * @param latitud/longitud/precision las del celular, o null
     * @param ultimaLatitud/ultimaLongitud/ultimaFecha la última compartida, o null
     */
    static Ubicacion ubicacion(Double latitud, Double longitud, Double precision,
                               Double ultimaLatitud, Double ultimaLongitud, LocalDateTime ultimaFecha,
                               LocalDateTime ahora) {
        if (valida(latitud, longitud) && (precision == null || (precision >= 0 && precision <= PRECISION_MAXIMA_METROS))) {
            Integer metros = precision != null ? (int) Math.round(precision) : null;
            return new Ubicacion(latitud, longitud, metros, OrigenUbicacion.DISPOSITIVO, ahora);
        }
        if (valida(ultimaLatitud, ultimaLongitud) && ultimaFecha != null
                && !ultimaFecha.isBefore(ahora.minusMinutes(MINUTOS_ULTIMA_COMPARTIDA))) {
            return new Ubicacion(ultimaLatitud, ultimaLongitud, null, OrigenUbicacion.ULTIMA_COMPARTIDA, ultimaFecha);
        }
        return null;
    }

    private static boolean valida(Double latitud, Double longitud) {
        return latitud != null && longitud != null && Double.isFinite(latitud) && Double.isFinite(longitud)
                && latitud >= -90 && latitud <= 90 && longitud >= -180 && longitud <= 180
                // 0,0 es lo que mandan algunos navegadores cuando no saben.
                && !(latitud == 0 && longitud == 0);
    }
}
