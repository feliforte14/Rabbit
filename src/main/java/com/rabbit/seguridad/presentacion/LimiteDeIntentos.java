package com.rabbit.seguridad.presentacion;

/**
 * Freno contra la prueba de contraseñas por fuerza bruta en el login web.
 *
 * Después de MAXIMO intentos fallidos seguidos para un mismo usuario, ese
 * usuario queda bloqueado BLOQUEO minutos: el login ni siquiera consulta
 * al realm. Un login correcto reinicia la cuenta. El mensaje es el mismo
 * exista o no el usuario, así no sirve para averiguar qué usuarios hay.
 *
 * Vive en memoria (@ApplicationScoped): en un cluster cada nodo lleva su
 * propia cuenta, y un redeploy la reinicia. Alcanza para frenar a alguien
 * que prueba contraseñas a mano o con un script contra la pantalla; la API
 * del ERP (HTTP Basic) la autentica el servidor y no pasa por acá.
 */

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class LimiteDeIntentos {

    static final int MAXIMO = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);

    private record Cuenta(int fallidos, Instant bloqueadoHasta) {}

    private final Map<String, Cuenta> cuentas = new ConcurrentHashMap<>();
    private Clock reloj = Clock.systemUTC();

    /** @return cuánto falta para poder volver a intentar, o Duration.ZERO si puede. */
    public Duration esperaPara(String usuario) {
        Cuenta c = cuentas.get(clave(usuario));
        if (c == null || c.bloqueadoHasta() == null) {
            return Duration.ZERO;
        }
        Duration falta = Duration.between(reloj.instant(), c.bloqueadoHasta());
        return falta.isNegative() ? Duration.ZERO : falta;
    }

    public void registrarFallo(String usuario) {
        cuentas.compute(clave(usuario), (k, c) -> {
            boolean venciElBloqueo = c != null && c.bloqueadoHasta() != null && !reloj.instant().isBefore(c.bloqueadoHasta());
            int fallidos = (c == null || venciElBloqueo ? 0 : c.fallidos()) + 1;
            return new Cuenta(fallidos, fallidos >= MAXIMO ? reloj.instant().plus(BLOQUEO) : null);
        });
    }

    public void registrarExito(String usuario) {
        cuentas.remove(clave(usuario));
    }

    // Sin distinguir mayúsculas ni espacios: "Admin" y " admin" son la misma cuenta.
    private static String clave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }

    // Para los tests: un reloj que se puede adelantar.
    void usarReloj(Clock reloj) {
        this.reloj = reloj;
    }
}
