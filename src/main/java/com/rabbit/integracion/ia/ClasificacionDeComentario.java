package com.rabbit.integracion.ia;

/**
 * Lo que la IA dice de un comentario de calificación: cómo suena (sentimiento) y de qué habla (tema). Los dos
 * valores salen siempre de una lista cerrada (ver {@link ClasificadorIa}): nunca es texto libre del modelo.
 */
public record ClasificacionDeComentario(Sentimiento sentimiento, Tema tema) {

    public enum Sentimiento { POSITIVO, NEUTRO, NEGATIVO }

    public enum Tema { PUNTUALIDAD, TRATO, ESTADO_DEL_PAQUETE, OTRO }
}
