package com.rabbit.pedidos.negocio;

/**
 * Valida la foto del remito de una entrega (ADR-033) por su contenido, no
 * por el nombre ni por el tipo que declara el navegador: los dos los elige
 * quien sube el archivo. Solo se aceptan JPEG, PNG y WEBP, reconocidos por
 * sus primeros bytes, y hasta un tamaño máximo.
 */
final class Remitos {

    /** 5 MB. La pantalla del repartidor achica la foto antes de subirla. */
    static final int TAMANO_MAXIMO = 5 * 1024 * 1024;

    private Remitos() {
    }

    /**
     * El tipo de la imagen (image/jpeg, image/png o image/webp).
     *
     * @throws ValidacionException si falta, es demasiado grande o no es una
     *         imagen de esos tipos
     */
    static String tipoDeImagen(byte[] imagen) {
        if (imagen == null || imagen.length == 0) {
            throw new ValidacionException("Sacale una foto al remito firmado para registrar la entrega");
        }
        return tipoDeImagen(imagen, "La foto del remito");
    }

    /**
     * Lo mismo para cualquier foto (también las de una visita fallida,
     * ADR-045). nombre va al principio del mensaje: "La foto 2".
     */
    static String tipoDeImagen(byte[] imagen, String nombre) {
        if (imagen == null || imagen.length == 0) {
            throw new ValidacionException(nombre + " está vacía");
        }
        if (imagen.length > TAMANO_MAXIMO) {
            throw new ValidacionException(nombre + " no puede superar los 5 MB");
        }
        if (empiezaCon(imagen, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (empiezaCon(imagen, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (empiezaCon(imagen, 0, 'R', 'I', 'F', 'F') && empiezaCon(imagen, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        throw new ValidacionException(nombre + " tiene que ser una imagen JPG, PNG o WEBP");
    }

    private static boolean empiezaCon(byte[] datos, int desde, int... firma) {
        if (datos.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((datos[desde + i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
