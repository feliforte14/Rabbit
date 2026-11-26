package com.rabbit.integracion.ia;

/**
 * Lo que la IA puede sacar de las instrucciones que deja quien recibe (ADR-057): indicaciones concretas para el
 * repartidor. Es una lista cerrada: el modelo solo puede elegir de acá (ver {@link EtiquetadorIa}), nunca escribe
 * texto libre. El repartidor las ve como etiquetas arriba del texto original, que se sigue mostrando siempre.
 */
public enum IndicacionDeEntrega {

    LLAMAR_ANTES("Llamar antes"),
    DEJAR_EN_PORTERIA("Dejar en portería"),
    DEJAR_CON_VECINO("Dejar con un vecino"),
    TIMBRE_NO_FUNCIONA("Timbre no funciona"),
    HORARIO_RESTRINGIDO("Horario restringido"),
    ACCESO_DIFICIL("Acceso difícil"),
    MASCOTA("Hay una mascota");

    private final String texto;

    IndicacionDeEntrega(String texto) {
        this.texto = texto;
    }

    /** Cómo se muestra en pantalla. */
    public String getTexto() {
        return texto;
    }
}
