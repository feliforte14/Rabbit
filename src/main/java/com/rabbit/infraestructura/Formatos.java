package com.rabbit.infraestructura;

/**
 * Cómo se muestran kilos y montos en pantalla, en formato argentino:
 * "1 kg", "0,95 kg", "1.250,5 kg" y "$ 15.000,00". BigDecimal.toString
 * daba "1.000" para un kilo (la escala de la columna), que se lee como mil.
 * Lo usan las vistas (vía EtiquetasBean) y los beans que arman textos.
 * También el día prometido como lo diría una persona (ADR-042).
 */

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public final class Formatos {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DecimalFormatSymbols AR = DecimalFormatSymbols.getInstance(ES_AR);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private Formatos() {}

    /** "3 kg", "0,95 kg"; vacío si es null. */
    public static String kilos(BigDecimal kilos) {
        return kilos == null ? "" : new DecimalFormat("#,##0.###", AR).format(kilos) + " kg";
    }

    /** "$ 15.000,00"; vacío si es null. */
    public static String pesos(BigDecimal importe) {
        return importe == null ? "" : "$ " + new DecimalFormat("#,##0.00", AR).format(importe);
    }

    /** "hoy", "mañana" o "el viernes 17/10"; vacío si es null. */
    public static String diaRelativo(LocalDate dia, LocalDate hoy) {
        if (dia == null) {
            return "";
        }
        if (dia.equals(hoy)) {
            return "hoy";
        }
        if (dia.equals(hoy.plusDays(1))) {
            return "mañana";
        }
        return "el " + dia.getDayOfWeek().getDisplayName(TextStyle.FULL, ES_AR) + " " + dia.format(DIA);
    }
}
