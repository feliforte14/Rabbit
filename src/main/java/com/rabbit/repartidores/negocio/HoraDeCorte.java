package com.rabbit.repartidores.negocio;

/**
 * Hora de corte de la hoja de ruta: pasada esa hora no se le suman pedidos
 * a la hoja del día de ningún repartidor, aunque le quede lugar. Lo que se
 * confirma después queda en la lista de espera y se asigna solo al día
 * siguiente, antes del corte (ver AsignadorDeEspera).
 *
 * Sale de una system property de WildFly, así se cambia sin tocar el
 * código ni redesplegar (se lee cada vez):
 *   rabbit.ruteo.hora-corte   por ejemplo 09:00
 * Sin la propiedad (o con un valor inválido) no hay corte: se asigna a
 * cualquier hora, como antes de existir esta regla.
 */

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.logging.Logger;

public final class HoraDeCorte {

    public static final String PROPIEDAD = "rabbit.ruteo.hora-corte";

    private static final Logger LOG = Logger.getLogger(HoraDeCorte.class.getName());

    private HoraDeCorte() {
    }

    /** La hora de corte configurada; null = sin corte. */
    public static LocalTime configurada() {
        return interpretar(System.getProperty(PROPIEDAD));
    }

    /** "09:00" o "9:00" → 09:00; vacío o inválido → null (sin corte). */
    static LocalTime interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        if (texto.matches("\\d:\\d\\d")) {
            texto = "0" + texto;
        }
        try {
            return LocalTime.parse(texto);
        } catch (DateTimeParseException e) {
            LOG.warning("[Repartidores] " + PROPIEDAD + "=" + valor + " no es una hora válida (HH:mm): no hay corte");
            return null;
        }
    }

    /**
     * Si a esa hora todavía se pueden sumar pedidos a la hoja del día. El
     * corte es exclusivo: con 09:00, a las 08:59 sí y a las 09:00 ya no.
     */
    public static boolean permiteAsignar(LocalTime corte, LocalTime ahora) {
        return corte == null || ahora.isBefore(corte);
    }
}
