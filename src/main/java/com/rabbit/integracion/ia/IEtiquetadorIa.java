package com.rabbit.integracion.ia;

import java.util.Optional;
import java.util.Set;

/** El gateway de IA de Rabbit: lo único que el resto del sistema conoce de la IA. */
public interface IEtiquetadorIa {

    /**
     * Saca de las instrucciones de entrega las indicaciones para el repartidor.
     *
     * @return las indicaciones (un conjunto vacío si el texto no pide nada concreto), o vacío (el fallback) si el
     *         texto no es válido, si la IA no está configurada, no responde en 5 s o contesta algo fuera de lo
     *         esperado: quien llama sigue sin etiquetas y el repartidor ve el texto como siempre
     */
    Optional<Set<IndicacionDeEntrega>> etiquetar(String instrucciones);
}
