package com.rabbit.seguridad.negocio;

/**
 * Hash de contraseñas con bcrypt, para la columna usuarios.hash_clave
 * (ADR-031).
 *
 * Quien valida el login no es esta clase sino el realm JDBC de WildFly
 * (scripts/configurar-realm-compartido.cli), que lee esa columna con su
 * modular-crypt-mapper: por eso el formato tiene que ser el estándar de
 * bcrypt ("$2a$10$..."), que Elytron sabe leer. bcrypt ya incluye una sal
 * aleatoria y es lento a propósito: si la base se filtra, probar
 * contraseñas cuesta caro.
 */

import org.mindrot.jbcrypt.BCrypt;

public final class Claves {

    // Costo de bcrypt: 2^10 rondas, unos 100 ms por hash.
    private static final int COSTO = 10;

    private Claves() {}

    /** Hash bcrypt de la contraseña, con sal nueva cada vez. */
    public static String hashear(String passwordEnClaro) {
        return BCrypt.hashpw(passwordEnClaro, BCrypt.gensalt(COSTO));
    }

    /** Si la contraseña corresponde al hash (lo usan los tests; el login lo resuelve WildFly). */
    public static boolean coincide(String passwordEnClaro, String hash) {
        return hash != null && BCrypt.checkpw(passwordEnClaro, hash);
    }
}
