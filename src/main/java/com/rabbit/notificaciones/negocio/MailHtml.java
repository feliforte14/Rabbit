package com.rabbit.notificaciones.negocio;

/**
 * Arma la versión HTML de los mails de aviso (la de texto plano sigue
 * yendo en el mismo mail, para los clientes que no muestran HTML).
 *
 * Es una plantilla fija: una franja con el nombre, el título, los párrafos
 * del aviso, un botón al seguimiento y el código de entrega resaltado. Todo
 * lo que viene de datos (nombres, motivos, comentarios) se escapa antes de
 * entrar al HTML, y los estilos van en línea porque los clientes de correo
 * ignoran las hojas de estilo.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import java.util.ArrayList;
import java.util.List;

final class MailHtml {

    // Los colores de la marca (estilos.css): navy, naranja y sus apoyos.
    private static final String NAVY = "#0f1b3d";
    private static final String NARANJA = "#f97316";
    private static final String NARANJA_SUAVE = "#fff1e6";
    private static final String ACENTO_TEXTO = "#c2410c";
    private static final String TEXTO = "#111827";
    private static final String TEXTO_SUAVE = "#6b7280";
    private static final String FONDO = "#f9fafb";
    private static final String BORDE = "#e4e4e7";
    private static final String PREFIJO_SEGUIMIENTO = "Seguilo con el código";

    private MailHtml() {
    }

    /**
     * @param titulo el asunto, sin el prefijo del remitente
     * @param texto el aviso en texto plano, con los párrafos separados por una línea en blanco
     * @param enlace el link al seguimiento, o null si no hay (avisos al comercio)
     * @param codigoSeguimiento el código que se muestra junto al botón, o null
     * @param codigoEntrega el código que el cliente le dice al repartidor, o null
     */
    static String render(String titulo, String texto, String enlace, String codigoSeguimiento,
                         String codigoEntrega) {
        StringBuilder html = new StringBuilder(2048);
        html.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(escapar(titulo)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:" + FONDO + ";font-family:Arial,Helvetica,sans-serif;")
                .append("color:" + TEXTO + ";\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:" + FONDO + ";padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:560px;background:#ffffff;border:1px solid " + BORDE + ";border-radius:12px;overflow:hidden;\">")
                .append("<tr><td style=\"background:").append(NAVY)
                .append(";color:#ffffff;padding:16px 24px;font-size:20px;font-weight:bold;")
                .append("border-bottom:4px solid ").append(NARANJA).append(";\">Rabbit</td></tr>")
                .append("<tr><td style=\"padding:24px;font-size:16px;line-height:1.5;\">")
                .append("<h1 style=\"margin:0 0 16px;font-size:20px;line-height:1.3;color:" + NAVY + ";\">")
                .append(escapar(titulo)).append("</h1>");

        for (String parrafo : parrafos(texto, enlace != null)) {
            html.append("<p style=\"margin:0 0 14px;\">")
                    .append(conSaltos(resaltar(escapar(parrafo), codigoEntrega)))
                    .append("</p>");
        }

        if (enlace != null) {
            html.append("<p style=\"margin:24px 0 8px;\"><a href=\"").append(escapar(enlace))
                    .append("\" style=\"background:").append(NARANJA)
                    .append(";color:").append(NAVY).append(";text-decoration:none;padding:12px 20px;border-radius:6px;")
                    .append("display:inline-block;font-weight:bold;\">Seguir mi pedido</a></p>");
            if (codigoSeguimiento != null) {
                html.append("<p style=\"margin:0;font-size:13px;color:" + TEXTO_SUAVE + ";\">Código de seguimiento: ")
                        .append(escapar(codigoSeguimiento)).append("</p>");
            }
        }

        html.append("</td></tr><tr><td style=\"padding:16px 24px;background:#f8fafc;font-size:12px;")
                .append("color:" + TEXTO_SUAVE + ";\">Este mail lo mandó Rabbit de forma automática. ")
                .append("No hace falta que lo respondas.</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    /** Los párrafos del texto; si hay botón se saca la línea "Seguilo con el código…", que el botón reemplaza. */
    private static List<String> parrafos(String texto, boolean conBoton) {
        List<String> lista = new ArrayList<>();
        for (String parrafo : texto.split("\\n\\s*\\n")) {
            String limpio = parrafo.strip();
            if (limpio.isEmpty() || (conBoton && limpio.startsWith(PREFIJO_SEGUIMIENTO))) {
                continue;
            }
            lista.add(limpio);
        }
        return lista;
    }

    /** El código de entrega, en grande, donde aparezca en el párrafo (ya escapado). */
    private static String resaltar(String parrafoEscapado, String codigoEntrega) {
        if (codigoEntrega == null || codigoEntrega.isBlank()) {
            return parrafoEscapado;
        }
        String codigo = escapar(codigoEntrega);
        return parrafoEscapado.replace(codigo,
                "<strong style=\"font-size:22px;letter-spacing:3px;background:" + NARANJA_SUAVE + ";color:" + ACENTO_TEXTO
                        + ";padding:2px 8px;border-radius:4px;\">" + codigo + "</strong>");
    }

    private static String conSaltos(String texto) {
        return texto.replace("\n", "<br>");
    }

    static String escapar(String texto) {
        StringBuilder salida = new StringBuilder(texto.length() + 16);
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '&' -> salida.append("&amp;");
                case '<' -> salida.append("&lt;");
                case '>' -> salida.append("&gt;");
                case '"' -> salida.append("&quot;");
                case '\'' -> salida.append("&#39;");
                default -> salida.append(c);
            }
        }
        return salida.toString();
    }
}
