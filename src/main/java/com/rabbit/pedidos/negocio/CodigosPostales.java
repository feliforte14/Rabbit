package com.rabbit.pedidos.negocio;

/**
 * Código postal de entrega: lo normaliza cuando lo manda el ERP y, si no lo
 * manda, lo busca en la dirección SOLO cuando está escrito sin ambigüedad.
 *
 * Se aceptan las formas habituales en Argentina: "CP 1414", "C.P. 1414",
 * "(1414)" y el código postal argentino (CPA) "C1414ABC". Un número suelto
 * de 4 dígitos NO se toma como código postal: "Av. Corrientes 1234" es una
 * altura, no un CP. Si no se puede saber, el pedido queda sin zona y el
 * personal decide a mano (ver RuteoService).
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodigosPostales {

    private static final Pattern CP_NUMERICO = Pattern.compile("^\\d{4}$");
    private static final Pattern CPA = Pattern.compile("(?i)\\b[A-HJ-NP-Z](\\d{4})[A-Z]{3}\\b");
    private static final Pattern CP_ROTULADO = Pattern.compile("(?i)\\bC\\.?\\s?P\\.?\\s*:?\\s*(\\d{4})\\b");
    private static final Pattern CP_ENTRE_PARENTESIS = Pattern.compile("\\((\\d{4})\\)");

    private CodigosPostales() {}

    /**
     * @param informado  el código postal que mandó el ERP (puede ser null o un CPA)
     * @param direccion  la dirección de entrega
     * @return el código postal de 4 dígitos, o null si no se puede determinar
     */
    public static String resolver(String informado, String direccion) {
        if (informado != null && !informado.isBlank()) {
            String limpio = informado.trim();
            if (CP_NUMERICO.matcher(limpio).matches()) {
                return limpio;
            }
            Matcher cpa = CPA.matcher(limpio);
            if (cpa.find()) {
                return cpa.group(1);
            }
            return null;
        }
        if (direccion == null) {
            return null;
        }
        for (Pattern patron : new Pattern[] {CPA, CP_ROTULADO, CP_ENTRE_PARENTESIS}) {
            Matcher m = patron.matcher(direccion);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /** true si lo que mandó el ERP no es un código postal reconocible. */
    public static boolean esInvalido(String informado) {
        return informado != null && !informado.isBlank() && resolver(informado, null) == null;
    }
}
