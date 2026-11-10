package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailHtmlTest {

    private static final String ENLACE = "https://rabbit.example.com/Rabbit/seguimiento.xhtml?codigo=RB-1";

    @Test
    @DisplayName("El aviso al cliente lleva título, botón al seguimiento y código de seguimiento")
    void avisoConBoton() {
        String texto = "Hola, Ana. Tu pedido salió a reparto.\n\nSeguilo con el código RB-1: " + ENLACE;

        String html = MailHtml.render("¡Tu pedido de Tienda llega hoy!", texto, ENLACE, "RB-1", null);

        assertTrue(html.contains("<h1"));
        assertTrue(html.contains("¡Tu pedido de Tienda llega hoy!"));
        assertTrue(html.contains("href=\"" + ENLACE + "\""));
        assertTrue(html.contains("Seguir mi pedido"));
        assertTrue(html.contains("Código de seguimiento: RB-1"));
        assertTrue(html.contains("lang=\"es\""));
    }

    @Test
    @DisplayName("La línea 'Seguilo con el código' no se repite: la reemplaza el botón")
    void sinLineaDuplicada() {
        String texto = "Hola. Tu pedido llega hoy.\n\nSeguilo con el código RB-1: " + ENLACE;

        String html = MailHtml.render("Titulo", texto, ENLACE, "RB-1", null);

        assertFalse(html.contains("Seguilo con el código"));
        assertEquals(1, html.split("<p style=\"margin:0 0 14px;\">", -1).length - 1);
    }

    @Test
    @DisplayName("El código de entrega sale resaltado")
    void codigoDeEntregaResaltado() {
        String texto = "Tu pedido llega hoy.\n\nTu código de entrega es 4821. Decíselo al repartidor.";

        String html = MailHtml.render("Titulo", texto, ENLACE, "RB-1", "4821");

        assertTrue(html.contains("<strong style=\"font-size:22px"));
        assertTrue(html.contains(">4821</strong>"));
    }

    @Test
    @DisplayName("Sin enlace (aviso al comercio) no hay botón y se conserva todo el texto")
    void sinEnlace() {
        String texto = "Tu pedido #100 está en camino.\n\nEste aviso también está en tu portal de Rabbit.";

        String html = MailHtml.render("Pedido #100", texto, null, null, null);

        assertFalse(html.contains("Seguir mi pedido"));
        assertTrue(html.contains("Este aviso también está en tu portal de Rabbit."));
    }

    @Test
    @DisplayName("Lo que viene de datos se escapa: un nombre con HTML no se interpreta")
    void escapaHtml() {
        String texto = "Hola, <script>alert(1)</script> & \"co\".";

        String html = MailHtml.render("<b>Titulo</b>", texto, null, null, null);

        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<b>Titulo</b>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;co&quot;."));
    }

    @Test
    @DisplayName("El enlace también se escapa dentro del atributo href")
    void escapaElEnlace() {
        String html = MailHtml.render("T", "Texto", "https://x.test/?a=1&b=\"2\"", "RB-1", null);

        assertTrue(html.contains("href=\"https://x.test/?a=1&amp;b=&quot;2&quot;\""));
    }

    @Test
    @DisplayName("Los saltos de línea dentro de un párrafo pasan a <br>")
    void saltosDeLinea() {
        String html = MailHtml.render("T", "Linea uno\nlinea dos", null, null, null);

        assertTrue(html.contains("Linea uno<br>linea dos"));
    }
}
