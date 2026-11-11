package com.rabbit.pedidos.negocio;

import com.rabbit.pedidos.datos.model.EstadoPedido;

/**
 * Regla de la calificación de la entrega (ADR-052): qué se puede calificar y
 * con qué datos. Separada del servicio para probarla sin base ni EJB.
 *
 *  - Solo se califica un pedido ENTREGADO: antes no hay nada que calificar, y
 *    uno cancelado o devuelto no tuvo entrega.
 *  - De 1 a 5 estrellas.
 *  - Comentario opcional, de hasta {@value #COMENTARIO_MAXIMO} caracteres. Es
 *    texto libre de un tercero (el seguimiento es público): se recorta, se le
 *    sacan los caracteres de control y, si queda vacío, no se guarda.
 */
public final class ReglaDeCalificacion {

    public static final int COMENTARIO_MAXIMO = 500;

    private ReglaDeCalificacion() {
    }

    /**
     * @return el comentario ya limpio, o null si no escribió ninguno
     * @throws ValidacionException si el pedido no se puede calificar o los datos no sirven
     */
    public static String validar(EstadoPedido estado, int estrellas, String comentario) {
        if (estado != EstadoPedido.ENTREGADO) {
            throw new ValidacionException("Solo se puede calificar un pedido que ya fue entregado");
        }
        if (estrellas < 1 || estrellas > 5) {
            throw new ValidacionException("Elegí de 1 a 5 estrellas");
        }
        String limpio = limpiar(comentario);
        if (limpio != null && limpio.length() > COMENTARIO_MAXIMO) {
            throw new ValidacionException("El comentario puede tener hasta " + COMENTARIO_MAXIMO + " caracteres");
        }
        return limpio;
    }

    // Saltos de línea normalizados a \n y sin otros caracteres de control (incluido el nulo).
    static String limpiar(String comentario) {
        if (comentario == null) {
            return null;
        }
        String sinControl = comentario.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", "")
                .strip();
        return sinControl.isEmpty() ? null : sinControl;
    }
}
