package com.rabbit.infraestructura;

/**
 * Forma mínima de un email: un solo «@», algo antes, sin espacios, y en el dominio un punto con algo antes y algo
 * después (por ejemplo ana@correo.com).
 *
 * Es lo mismo que decía la expresión regular {@code [^@\s]+@[^@\s]+\.[^@\s]+}, pero recorriendo el texto una sola vez:
 * esa expresión es ambigua (el punto puede caer en muchos lugares) y con una entrada larga y mal armada tardaba un
 * tiempo cuadrático en rechazarla.
 */
public final class FormatoDeEmail {

    private FormatoDeEmail() {
    }

    public static boolean tieneFormato(String email) {
        if (email == null) {
            return false;
        }
        int arroba = email.indexOf('@');
        if (arroba < 1 || email.indexOf('@', arroba + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < email.length(); i++) {
            if (esEspacio(email.charAt(i))) {
                return false;
            }
        }
        // Un punto en el dominio, ni primero ni último.
        int primero = arroba + 1;
        int ultimo = email.length() - 1;
        for (int i = primero + 1; i < ultimo; i++) {
            if (email.charAt(i) == '.') {
                return true;
            }
        }
        return false;
    }

    // Los mismos caracteres que \s en una expresión regular de Java.
    private static boolean esEspacio(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }
}
