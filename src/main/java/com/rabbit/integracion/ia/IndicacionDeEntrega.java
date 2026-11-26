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

    /** Guardado: los nombres separados por coma, o null si no hay ninguna. */
    public static String guardar(java.util.Collection<IndicacionDeEntrega> indicaciones) {
        return indicaciones.isEmpty() ? null : indicaciones.stream().map(Enum::name)
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** Lo guardado, como textos para mostrar. Ignora cualquier nombre que ya no exista. */
    public static java.util.List<String> textos(String guardado) {
        if (guardado == null || guardado.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> textos = new java.util.ArrayList<>();
        for (String nombre : guardado.split(",")) {
            try {
                textos.add(valueOf(nombre.trim()).getTexto());
            } catch (IllegalArgumentException e) {
                // un nombre que ya no está en la lista cerrada: se ignora
            }
        }
        return textos;
    }

    /** Cómo se muestra en pantalla. */
    public String getTexto() {
        return texto;
    }
}
