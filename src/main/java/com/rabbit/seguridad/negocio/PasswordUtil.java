package com.rabbit.seguridad.negocio;

/**
 * Hashing de contraseñas, compartido entre UsuarioService (al registrar) y
 * RabbitIdentityStore (al validar login) — ambos necesitan calcular el
 * mismo hash para la misma contraseña.
 *
 * SIMPLIFICACIÓN A PROPÓSITO PARA EL ALCANCE DEL TP: SHA-256 sin salt.
 * Alcanza para demostrar que la contraseña no se guarda en texto plano,
 * pero NO es apto para producción real (ahí correspondería BCrypt o
 * PBKDF2 con salt por usuario, para que dos contraseñas iguales no
 * generen el mismo hash y para encarecer un ataque de fuerza bruta).
 */

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PasswordUtil {

    private PasswordUtil() {}

    // Calcula el hash SHA-256 en hexadecimal de una contraseña en texto
    // plano; determinístico, así el mismo texto siempre produce el mismo
    // hash (necesario para poder comparar en el login).
    public static String hash(String passwordEnClaro) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(passwordEnClaro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es parte del JDK estándar: si esto pasa, el problema es el entorno, no el input.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
