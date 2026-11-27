package com.rabbit.infraestructura;

/**
 * Resumen de una excepción en una sola línea, para el log.
 *
 * POR QUÉ
 * -------
 * Las fallas ESPERADAS de un sistema externo (el banco no responde, el
 * transportista tarda, un mensaje llega roto) se loguean seguido y no
 * necesitan el stack trace: el mensaje del log ya dice dónde pasó, y la
 * causa de fondo (por ejemplo "Read timed out") alcanza para diagnosticar.
 * Con el stack completo, una caída del banco de un minuto dejaba miles de
 * líneas en el log, y un log lleno de trazas es justo lo que el eje D pide
 * evitar ("sin stack traces en logs").
 *
 * Las fallas INESPERADAS (un error no controlado en la API, un timer que
 * falla) siguen logueando la excepción entera: ahí la traza sí hace falta.
 */
public final class Trazas {

    private Trazas() {
    }

    /** "Tipo: mensaje", y si la causa de fondo es otra, " (causa: Tipo: mensaje)". */
    public static String resumen(Throwable e) {
        if (e == null) {
            return "sin detalle";
        }
        Throwable raiz = e;
        // El tope evita un ciclo si una excepción se tiene de causa a sí misma.
        for (int i = 0; i < 20 && raiz.getCause() != null && raiz.getCause() != raiz; i++) {
            raiz = raiz.getCause();
        }
        String texto = unaLinea(e);
        return raiz == e ? texto : texto + " (causa: " + unaLinea(raiz) + ")";
    }

    private static String unaLinea(Throwable e) {
        String mensaje = e.getMessage();
        String tipo = e.getClass().getSimpleName();
        if (mensaje == null || mensaje.isBlank()) {
            return tipo;
        }
        // Un mensaje con saltos de línea rompería el "una línea por evento" del log.
        return tipo + ": " + mensaje.replaceAll("\\s+", " ").trim();
    }
}
