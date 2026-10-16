package com.rabbit.infraestructura;

/**
 * Cómo se muestran kilos y montos en pantalla, en formato argentino:
 * "1 kg", "0,95 kg", "1.250,5 kg" y "$ 15.000,00". BigDecimal.toString
 * daba "1.000" para un kilo (la escala de la columna), que se lee como mil.
 * Lo usan las vistas (vía EtiquetasBean) y los beans que arman textos.
 */

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Formatos {

    private static final DecimalFormatSymbols AR = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR"));

    private Formatos() {}

    /** "3 kg", "0,95 kg"; vacío si es null. */
    public static String kilos(BigDecimal kilos) {
        return kilos == null ? "" : new DecimalFormat("#,##0.###", AR).format(kilos) + " kg";
    }

    /** "$ 15.000,00"; vacío si es null. */
    public static String pesos(BigDecimal importe) {
        return importe == null ? "" : "$ " + new DecimalFormat("#,##0.00", AR).format(importe);
    }
}
