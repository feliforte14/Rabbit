package com.rabbit.repartidores.negocio;

/**
 * Hora de corte de la hoja de ruta: pasada esa hora no se le suman pedidos
 * a la hoja del día de ningún repartidor, aunque le quede lugar. Lo que se
 * confirma después queda en la lista de espera y se asigna solo al día
 * siguiente, antes del corte (ver AsignadorDeEspera).
 *
 * La definió el equipo: 21:00 (16/10/2026). Se cambia con una system
 * property de WildFly, sin tocar el código ni redesplegar (se lee cada
 * vez):
 *   rabbit.ruteo.hora-corte   por ejemplo 18:30, o "ninguno" para no
 *                             tener corte (una demo de noche)
 * Un valor inválido se ignora y se usa el de por defecto.
 */

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.logging.Logger;

public final class HoraDeCorte {

    public static final String PROPIEDAD = "rabbit.ruteo.hora-corte";

    /** La que definió el equipo. */
    public static final LocalTime POR_DEFECTO = LocalTime.of(21, 0);

    /** Valor de la propiedad para no tener corte. */
    public static final String SIN_CORTE = "ninguno";

    private static final Logger LOG = Logger.getLogger(HoraDeCorte.class.getName());

    private HoraDeCorte() {
    }

    /** La hora de corte configurada; null = sin corte. */
    public static LocalTime configurada() {
        return interpretar(System.getProperty(PROPIEDAD));
    }

    /**
     * "18:30" o "9:00" → esa hora; "ninguno" → null (sin corte); sin
     * definir, vacío o inválido → la de por defecto (21:00).
     */
    static LocalTime interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return POR_DEFECTO;
        }
        String texto = valor.trim();
        if (SIN_CORTE.equalsIgnoreCase(texto)) {
            return null;
        }
        if (texto.matches("\\d:\\d\\d")) {
            texto = "0" + texto;
        }
        try {
            return LocalTime.parse(texto);
        } catch (DateTimeParseException e) {
            LOG.warning("[Repartidores] " + PROPIEDAD + "=" + valor + " no es una hora válida (HH:mm ni \""
                    + SIN_CORTE + "\"): se usa " + POR_DEFECTO);
            return POR_DEFECTO;
        }
    }

    /**
     * Si a esa hora todavía se pueden sumar pedidos a la hoja del día. El
     * corte es exclusivo: con 21:00, a las 20:59 sí y a las 21:00 ya no.
     */
    public static boolean permiteAsignar(LocalTime corte, LocalTime ahora) {
        return corte == null || ahora.isBefore(corte);
    }
}
