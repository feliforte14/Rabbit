package com.rabbit.integracion.ia;

import java.util.Optional;

/** El gateway de IA de Rabbit: lo único que el resto del sistema conoce de la IA. */
public interface IClasificadorIa {

    /**
     * Clasifica el comentario de una calificación. Devuelve vacío (el fallback) si el texto no es válido, si la IA
     * no está configurada, no responde en 5 s o contesta algo fuera de lo esperado: quien llama sigue sin la
     * clasificación y nada más.
     */
    Optional<ClasificacionDeComentario> clasificar(String comentario);
}
