package com.rabbit.infraestructura;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Para que los logs no lleven datos personales (SEC-09): el log del servidor
 * lo puede leer más gente que la que ve el pedido, y se guarda mucho tiempo.
 * En un log va el ID del pedido; el email se enmascara y la dirección de
 * entrega no se escribe.
 *
 * Enmascarar el email deja el primer carácter y el dominio ("c******@correo.test"):
 * alcanza para distinguir a quién se le avisó al investigar un problema, sin
 * dejar el email completo. Los asteriscos son siempre 6, así tampoco se filtra
 * el largo del nombre.
 */
public final class DatosPersonales {

    private static final String ASTERISCOS = "******";
    private static final Pattern EMAIL = Pattern.compile("[^\\s,;<>\"'()]+@[^\\s,;<>\"'()]+");

    private DatosPersonales() {
    }

    /**
     * Enmascara un email, o una lista de emails separados por coma o punto y
     * coma (así los acepta {@code InternetAddress.parse}). Lo que no parece
     * un email se reemplaza entero, por las dudas.
     */
    public static String emailEnmascarado(String emails) {
        if (emails == null || emails.isBlank()) {
            return "(sin email)";
        }
        StringBuilder resultado = new StringBuilder();
        for (String uno : emails.split("[,;]")) {
            String limpio = uno.trim();
            if (limpio.isEmpty()) {
                continue;
            }
            if (resultado.length() > 0) {
                resultado.append(", ");
            }
            resultado.append(enmascararUno(limpio));
        }
        return resultado.length() == 0 ? "(sin email)" : resultado.toString();
    }

    /**
     * Reemplaza por su versión enmascarada cualquier email que aparezca dentro
     * de un texto. Sirve para los mensajes de error de JavaMail, que a veces
     * incluyen la dirección que falló.
     */
    public static String sinEmails(String texto) {
        if (texto == null) {
            return "";
        }
        Matcher m = EMAIL.matcher(texto);
        StringBuilder resultado = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(resultado, Matcher.quoteReplacement(enmascararUno(m.group())));
        }
        m.appendTail(resultado);
        return resultado.toString();
    }

    private static String enmascararUno(String email) {
        int arroba = email.lastIndexOf('@');
        if (arroba < 1 || arroba == email.length() - 1) {
            return ASTERISCOS; // no parece un email: no se deja ningún pedazo
        }
        return email.charAt(0) + ASTERISCOS + email.substring(arroba);
    }
}
