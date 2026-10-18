package com.rabbit.pedidos.negocio;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Datos de quien recibe el pedido (ADR-039): nombre, teléfono y email.
 * Desde el 18/10/2026 el email es obligatorio en el alta de un pedido
 * nuevo (emailObligatorio), porque es por donde Rabbit le avisa cada cambio
 * de estado; nombre y teléfono siguen opcionales. Los pedidos que ya
 * existían sin email no se tocan (email() sigue aceptando null para
 * leerlos y para la huella). Acá se normalizan (sin espacios de más,
 * vacío = null) y se validan, igual para la API y para la pantalla.
 *
 * El formato es permisivo a propósito: el teléfono admite +, espacios,
 * guiones y paréntesis, como lo escribe la gente ("+54 11 4567-8901"). Un
 * dato raro no tiene que frenar un pedido; uno que no sirve para avisar
 * (un email sin @) sí, porque es la razón de pedirlo.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */
final class Destinatarios {

    static final int MAX_NOMBRE = 100;
    static final int MAX_TELEFONO = 30;
    static final int MAX_EMAIL = 120;

    private static final Pattern TELEFONO = Pattern.compile("\\+?[0-9(][0-9 ()-]{5,29}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private Destinatarios() {
    }

    static String nombre(String valor) {
        String nombre = limpiar(valor);
        if (nombre != null && nombre.length() > MAX_NOMBRE) {
            throw new ValidacionException("El nombre de quien recibe no puede superar los " + MAX_NOMBRE + " caracteres");
        }
        return nombre;
    }

    static String telefono(String valor) {
        String telefono = limpiar(valor);
        if (telefono != null && !TELEFONO.matcher(telefono).matches()) {
            throw new ValidacionException("El teléfono de quien recibe tiene que tener entre 6 y 30 caracteres: "
                    + "números, espacios, guiones, paréntesis y un + al principio");
        }
        return telefono;
    }

    static String email(String valor) {
        String email = limpiar(valor);
        if (email == null) {
            return null;
        }
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            throw new ValidacionException("El email de quien recibe no es válido (por ejemplo, ana@correo.com)");
        }
        return email.toLowerCase(Locale.ROOT);
    }

    /** Como email(), pero sin email no se puede dar de alta un pedido nuevo. */
    static String emailObligatorio(String valor) {
        String email = email(valor);
        if (email == null) {
            throw new ValidacionException(FALTA_EMAIL);
        }
        return email;
    }

    static final String FALTA_EMAIL = "Falta el email de quien recibe: es obligatorio porque Rabbit le avisa"
            + " por mail cada novedad del pedido (por ejemplo, ana@correo.com)";

    // Espacios de más afuera y repetidos adentro; vacío = no vino.
    private static String limpiar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim().replaceAll("\\s+", " ");
    }
}
