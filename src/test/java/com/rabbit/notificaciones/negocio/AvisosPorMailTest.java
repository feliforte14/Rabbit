package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El mail de aviso, de punta a punta contra un servidor SMTP falso que corre dentro del test: lo que
 * llega es un multipart/alternative con texto plano y HTML, con el asunto, el remitente y el destino
 * correctos. Y si el servidor no responde, el motivo de fondo queda en el log (JavaMail lo esconde tras
 * "IOException while sending message").
 */
class AvisosPorMailTest {

    private static final String[] PROPIEDADES = {
            "rabbit.mail.smtp.host", "rabbit.mail.smtp.port", "rabbit.mail.remitente"};

    @AfterEach
    void limpiar() {
        for (String propiedad : PROPIEDADES) {
            System.clearProperty(propiedad);
        }
    }

    private static void configurar(int puerto) {
        System.setProperty("rabbit.mail.smtp.host", "127.0.0.1");
        System.setProperty("rabbit.mail.smtp.port", String.valueOf(puerto));
        System.setProperty("rabbit.mail.remitente", "rabbit@demo.test");
    }

    /** Servidor SMTP mínimo: acepta una conexión, guarda el mensaje del comando DATA y se cierra. */
    private static final class SmtpFalso implements AutoCloseable {
        private final ServerSocket servidor;
        private final CountDownLatch recibido = new CountDownLatch(1);
        private volatile byte[] mensaje;

        SmtpFalso() throws IOException {
            servidor = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            Thread hilo = new Thread(this::atender, "smtp-falso");
            hilo.setDaemon(true);
            hilo.start();
        }

        int puerto() {
            return servidor.getLocalPort();
        }

        MimeMessage esperarMensaje() throws Exception {
            assertTrue(recibido.await(10, TimeUnit.SECONDS), "el mail no llegó al servidor SMTP");
            return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(mensaje));
        }

        private void atender() {
            try (Socket socket = servidor.accept();
                 BufferedReader entrada = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                 Writer salida = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1)) {
                responder(salida, "220 listo");
                ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
                boolean enDatos = false;
                String linea;
                while ((linea = entrada.readLine()) != null) {
                    if (enDatos) {
                        if (linea.equals(".")) {
                            enDatos = false;
                            mensaje = cuerpo.toByteArray();
                            recibido.countDown();
                            responder(salida, "250 recibido");
                        } else {
                            cuerpo.write(linea.getBytes(StandardCharsets.ISO_8859_1));
                            cuerpo.write('\r');
                            cuerpo.write('\n');
                        }
                        continue;
                    }
                    String comando = linea.toUpperCase();
                    if (comando.startsWith("DATA")) {
                        enDatos = true;
                        responder(salida, "354 mandá");
                    } else if (comando.startsWith("QUIT")) {
                        responder(salida, "221 chau");
                        return;
                    } else {
                        responder(salida, "250 ok");
                    }
                }
            } catch (IOException e) {
                // el test cierra el servidor al terminar
            }
        }

        private static void responder(Writer salida, String texto) throws IOException {
            salida.write(texto + "\r\n");
            salida.flush();
        }

        @Override
        public void close() throws IOException {
            servidor.close();
        }
    }

    @Test
    @DisplayName("El aviso al cliente llega como texto plano y HTML, con el botón y el código resaltado")
    void avisoAlDestinatario() throws Exception {
        String enlace = "https://rabbit.test/Rabbit/seguimiento.xhtml?codigo=RB-1";
        try (SmtpFalso smtp = new SmtpFalso()) {
            configurar(smtp.puerto());

            new AvisosPorMail().alRegistrarAvisoDestinatario(new AvisoDestinatarioRegistrado(7L, "ana@demo.test",
                    "¡Tu pedido llega hoy!",
                    "Hola, Ana.\n\nTu código de entrega es 4821.\n\nSeguilo con el código RB-1: " + enlace,
                    enlace, "RB-1", "4821"));

            MimeMessage mail = smtp.esperarMensaje();
            assertEquals("Rabbit · ¡Tu pedido llega hoy!", mail.getSubject());
            assertEquals("Rabbit <rabbit@demo.test>", mail.getFrom()[0].toString());
            assertEquals("ana@demo.test", mail.getRecipients(Message.RecipientType.TO)[0].toString());

            assertTrue(mail.isMimeType("multipart/alternative"));
            MimeMultipart partes = (MimeMultipart) mail.getContent();
            assertEquals(2, partes.getCount());
            // El cliente de correo muestra la última parte que entiende: el HTML va después del texto plano.
            assertTrue(partes.getBodyPart(0).isMimeType("text/plain"));
            assertTrue(partes.getBodyPart(1).isMimeType("text/html"));

            String plano = (String) partes.getBodyPart(0).getContent();
            assertTrue(plano.contains("Seguilo con el código RB-1: " + enlace));
            String html = (String) partes.getBodyPart(1).getContent();
            assertTrue(html.contains("Seguir mi pedido"));
            assertTrue(html.contains("<strong"), "el código de entrega va resaltado");
            assertFalse(html.contains("Seguilo con el código"), "la línea del enlace la reemplaza el botón");
        }
    }

    @Test
    @DisplayName("El aviso al comercio también lleva las dos versiones, pero sin botón al seguimiento")
    void avisoAlComercio() throws Exception {
        try (SmtpFalso smtp = new SmtpFalso()) {
            configurar(smtp.puerto());

            new AvisosPorMail().alRegistrarAviso(
                    new AvisoRegistrado(3L, "ventas@tienda.test", 100L, "Tu pedido #100 está en camino"));

            MimeMessage mail = smtp.esperarMensaje();
            assertEquals("Rabbit · Pedido #100", mail.getSubject());
            MimeMultipart partes = (MimeMultipart) mail.getContent();
            assertEquals(2, partes.getCount());
            String html = (String) partes.getBodyPart(1).getContent();
            assertTrue(html.contains("Tu pedido #100 está en camino"));
            assertTrue(html.contains("Este aviso también está en tu portal de Rabbit."));
            assertFalse(html.contains("Seguir mi pedido"));
        }
    }

    @Test
    @DisplayName("El cambio de entrega llega con lo que quedó guardado, sin el código de entrega")
    void cambioDeEntrega() throws Exception {
        try (SmtpFalso smtp = new SmtpFalso()) {
            configurar(smtp.puerto());

            new AvisosPorMail().alModificarseLaEntrega(new com.rabbit.pedidos.negocio.EntregaModificada(7L,
                    "ana@demo.test", "Ana", "Tienda Feliz", "RB-1", java.time.LocalDate.now().plusDays(2),
                    "Dejar en portería", "Marta Gómez"));

            MimeMessage mail = smtp.esperarMensaje();
            assertEquals("Rabbit · Guardamos los cambios de tu entrega de Tienda Feliz", mail.getSubject());
            assertEquals("ana@demo.test", mail.getRecipients(Message.RecipientType.TO)[0].toString());
            MimeMultipart partes = (MimeMultipart) mail.getContent();
            assertEquals(2, partes.getCount());
            String plano = (String) partes.getBodyPart(0).getContent();
            assertTrue(plano.contains("Instrucciones para el repartidor: Dejar en portería"), plano);
            assertTrue(plano.contains("Autorizaste a Marta Gómez"), plano);
            assertTrue(plano.contains("codigo=RB-1"), plano);
            String html = (String) partes.getBodyPart(1).getContent();
            assertTrue(html.contains("Seguir mi pedido"));
            assertTrue(html.contains("Dejar en portería"));
        }
    }

    @Test
    @DisplayName("El cambio de entrega sin email de quien recibe no intenta mandar nada")
    void cambioDeEntregaSinEmail() {
        // Un puerto cerrado: si intentara mandar, fallaría y quedaría un aviso en el log.
        configurar(1);
        List<String> mensajes = new ArrayList<>();
        Logger logger = Logger.getLogger(AvisosPorMail.class.getName());
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord registro) {
                mensajes.add(registro.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(captura);
        try {
            new AvisosPorMail().alModificarseLaEntrega(new com.rabbit.pedidos.negocio.EntregaModificada(7L, null,
                    "Ana", "Tienda Feliz", "RB-1", null, null, null));
        } finally {
            logger.removeHandler(captura);
        }
        assertTrue(mensajes.isEmpty(), "no debería haber intentado enviar: " + mensajes);
    }

    @Test
    @DisplayName("Si el servidor no responde, el log trae la causa de fondo y no el email completo")
    void fallaConCausa() throws Exception {
        // Un puerto que acaba de cerrarse: la conexión se rechaza.
        int puertoCerrado;
        try (ServerSocket libre = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            puertoCerrado = libre.getLocalPort();
        }
        configurar(puertoCerrado);

        List<String> mensajes = new ArrayList<>();
        Logger logger = Logger.getLogger(AvisosPorMail.class.getName());
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord registro) {
                mensajes.add(registro.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(captura);
        Level anterior = logger.getLevel();
        logger.setLevel(Level.ALL);
        try {
            new AvisosPorMail().alRegistrarAviso(
                    new AvisoRegistrado(3L, "ventas@tienda.test", 100L, "Tu pedido #100 está en camino"));
        } finally {
            logger.removeHandler(captura);
            logger.setLevel(anterior);
        }

        String aviso = mensajes.stream().filter(m -> m.contains("No se pudo mandar")).findFirst().orElse(null);
        assertNotNull(aviso, "tiene que loguear que no se pudo mandar: " + mensajes);
        assertTrue(aviso.contains("(causa: "), "falta la causa de fondo: " + aviso);
        assertFalse(aviso.contains("ventas@tienda.test"), "el email no va completo en el log: " + aviso);
        assertTrue(aviso.contains("Queda registrado en Rabbit."));
    }
}
