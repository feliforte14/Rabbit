package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.notificaciones.negocio.BandejaDeCorreos.Correo;
import com.rabbit.notificaciones.negocio.BandejaDeCorreos.Pendiente;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * El envío de los mails de la bandeja, de punta a punta contra un servidor SMTP falso que corre dentro del test:
 * lo que llega es un multipart/alternative (texto plano y HTML); si el servidor no responde el mail sigue
 * pendiente y se corta la pasada; si rechaza el destinatario para siempre se descarta y se sigue con el próximo;
 * y lo que lleva más de 24 horas esperando se descarta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnvioDeCorreosPendientesTest {

    private static final String[] PROPIEDADES = {
            "rabbit.mail.smtp.host", "rabbit.mail.smtp.port", "rabbit.mail.remitente"};

    @Mock private BandejaDeCorreos bandeja;
    @InjectMocks private EnvioDeCorreosPendientes envio;

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

    private static Correo correo(Long idPedido, String destino) {
        return new Correo(idPedido, destino, "¡Tu pedido llega hoy!", "Hola, Ana.\n\nTu código de entrega es 4821.",
                "https://rabbit.test/Rabbit/seguimiento.xhtml?codigo=RB-1", "RB-1", "4821");
    }

    /** Servidor SMTP mínimo: acepta conexiones una tras otra y guarda cada mensaje del comando DATA. */
    private static final class SmtpFalso implements AutoCloseable {
        private final ServerSocket servidor;
        private final List<byte[]> mensajes = new CopyOnWriteArrayList<>();
        private final Function<String, String> respuestaAlDestinatario;

        SmtpFalso() throws IOException {
            this(destinatario -> "250 ok");
        }

        SmtpFalso(Function<String, String> respuestaAlDestinatario) throws IOException {
            this.respuestaAlDestinatario = respuestaAlDestinatario;
            servidor = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
            Thread hilo = new Thread(this::atender, "smtp-falso");
            hilo.setDaemon(true);
            hilo.start();
        }

        int puerto() {
            return servidor.getLocalPort();
        }

        MimeMessage mensaje(int posicion) throws Exception {
            return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(mensajes.get(posicion)));
        }

        int recibidos() {
            return mensajes.size();
        }

        private void atender() {
            while (!servidor.isClosed()) {
                try (Socket socket = servidor.accept()) {
                    conversar(socket);
                } catch (IOException e) {
                    // el test cierra el servidor al terminar
                }
            }
        }

        private void conversar(Socket socket) throws IOException {
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            Writer salida = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1);
            responder(salida, "220 listo");
            ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
            boolean enDatos = false;
            String linea;
            while ((linea = entrada.readLine()) != null) {
                if (enDatos) {
                    if (linea.equals(".")) {
                        enDatos = false;
                        mensajes.add(cuerpo.toByteArray());
                        cuerpo.reset();
                        responder(salida, "250 recibido");
                    } else {
                        cuerpo.write(linea.getBytes(StandardCharsets.ISO_8859_1));
                        cuerpo.write('\r');
                        cuerpo.write('\n');
                    }
                    continue;
                }
                String comando = linea.toUpperCase();
                if (comando.startsWith("RCPT TO")) {
                    responder(salida, respuestaAlDestinatario.apply(linea));
                } else if (comando.startsWith("DATA")) {
                    enDatos = true;
                    responder(salida, "354 mandá");
                } else if (comando.startsWith("QUIT")) {
                    responder(salida, "221 chau");
                    return;
                } else {
                    responder(salida, "250 ok");
                }
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

    /** Un puerto que acaba de cerrarse: la conexión se rechaza. */
    private static int puertoCerrado() throws IOException {
        try (ServerSocket libre = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return libre.getLocalPort();
        }
    }

    /** Lo que se loguea mientras corre el bloque. */
    private static List<String> loguear(Runnable bloque) {
        List<String> mensajes = new ArrayList<>();
        Logger logger = Logger.getLogger(EnvioDeCorreosPendientes.class.getName());
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
            bloque.run();
        } finally {
            logger.removeHandler(captura);
        }
        return mensajes;
    }

    @Test
    @DisplayName("Manda el mail como texto plano y HTML, con asunto, remitente, destino, botón y código resaltado")
    void mandaYMarcaEnviado() throws Exception {
        try (SmtpFalso smtp = new SmtpFalso()) {
            configurar(smtp.puerto());
            when(bandeja.pendientes(anyInt())).thenReturn(List.of(new Pendiente(11L, correo(7L, "ana@demo.test"))));

            assertEquals(1, envio.enviarPendientes());

            MimeMessage mail = smtp.mensaje(0);
            assertEquals("Rabbit · ¡Tu pedido llega hoy!", mail.getSubject());
            assertEquals("Rabbit <rabbit@demo.test>", mail.getFrom()[0].toString());
            assertEquals("ana@demo.test", mail.getRecipients(Message.RecipientType.TO)[0].toString());
            assertTrue(mail.isMimeType("multipart/alternative"));
            MimeMultipart partes = (MimeMultipart) mail.getContent();
            assertEquals(2, partes.getCount());
            assertTrue(partes.getBodyPart(0).isMimeType("text/plain"));
            assertTrue(partes.getBodyPart(1).isMimeType("text/html"));
            String html = (String) partes.getBodyPart(1).getContent();
            assertTrue(html.contains("Seguir mi pedido"));
            assertTrue(html.contains("<strong"), "el código de entrega va resaltado");
            verify(bandeja).marcarEnviado(11L);
        }
    }

    @Test
    @DisplayName("Un aviso al comercio, sin enlace, sale sin botón al seguimiento")
    void avisoAlComercioSinBoton() throws Exception {
        try (SmtpFalso smtp = new SmtpFalso()) {
            configurar(smtp.puerto());
            Correo aviso = new Correo(100L, "ventas@tienda.test", "Pedido #100",
                    "Tu pedido #100 está en camino.\n\nEste aviso también está en tu portal de Rabbit.", null, null, null);
            when(bandeja.pendientes(anyInt())).thenReturn(List.of(new Pendiente(5L, aviso)));

            assertEquals(1, envio.enviarPendientes());

            String html = (String) ((MimeMultipart) smtp.mensaje(0).getContent()).getBodyPart(1).getContent();
            assertTrue(html.contains("Tu pedido #100 está en camino."));
            assertFalse(html.contains("Seguir mi pedido"));
        }
    }

    @Test
    @DisplayName("Con el servidor caído el mail queda pendiente, se corta la pasada y el log trae la causa sin el email")
    void servidorCaido() throws Exception {
        configurar(puertoCerrado());
        when(bandeja.pendientes(anyInt())).thenReturn(List.of(
                new Pendiente(1L, correo(7L, "ana@demo.test")), new Pendiente(2L, correo(8L, "luis@demo.test"))));

        int[] enviados = new int[1];
        List<String> log = loguear(() -> enviados[0] = envio.enviarPendientes());

        assertEquals(0, enviados[0]);
        verify(bandeja).registrarFallo(1L);
        // Si el servidor no responde, el segundo también fallaría: no se intenta hasta la próxima pasada.
        verify(bandeja, never()).registrarFallo(2L);
        verify(bandeja, never()).marcarEnviado(anyLong());
        verify(bandeja, never()).descartar(anyLong(), anyString());
        String aviso = log.stream().filter(m -> m.contains("No se pudo mandar")).findFirst().orElse(null);
        assertNotNull(aviso, log.toString());
        assertTrue(aviso.contains("(causa: "), aviso);
        assertTrue(aviso.endsWith("Se reintenta."), aviso);
        assertFalse(aviso.contains("ana@demo.test"), "el email no va completo en el log: " + aviso);
    }

    @Test
    @DisplayName("Cuando el servidor vuelve, el mail que estaba pendiente sale")
    void elReintentoSale() throws Exception {
        when(bandeja.pendientes(anyInt())).thenReturn(List.of(new Pendiente(1L, correo(7L, "ana@demo.test"))));

        configurar(puertoCerrado());
        assertEquals(0, envio.enviarPendientes());
        verify(bandeja).registrarFallo(1L);

        try (SmtpFalso smtp = new SmtpFalso()) {
            configurar(smtp.puerto());
            // El timer corre cada minuto la misma pasada.
            envio.reintentarPendientes();
            assertEquals(1, smtp.recibidos());
            verify(bandeja).marcarEnviado(1L);
        }
    }

    @Test
    @DisplayName("Un destinatario que el servidor rechaza para siempre se descarta y no frena a los demás")
    void destinatarioRechazado() throws Exception {
        try (SmtpFalso smtp = new SmtpFalso(rcpt -> rcpt.contains("malo@") ? "550 no existe esa casilla" : "250 ok")) {
            configurar(smtp.puerto());
            when(bandeja.pendientes(anyInt())).thenReturn(List.of(
                    new Pendiente(1L, correo(7L, "malo@demo.test")), new Pendiente(2L, correo(8L, "ana@demo.test"))));

            List<String> log = new ArrayList<>();
            int[] enviados = new int[1];
            log.addAll(loguear(() -> enviados[0] = envio.enviarPendientes()));

            // El primero se descarta (reintentarlo no serviría) y el segundo igual sale.
            assertEquals(1, enviados[0]);
            verify(bandeja).descartar(1L, "El servidor rechazó el destinatario");
            verify(bandeja, never()).registrarFallo(anyLong());
            verify(bandeja).marcarEnviado(2L);
            assertEquals(1, smtp.recibidos());
            assertTrue(log.stream().anyMatch(m -> m.endsWith("se descarta.")), log.toString());
        }
    }

    @Test
    @DisplayName("Un rechazo pasajero del servidor (4xx) no descarta: se reintenta")
    void rechazoPasajero() throws Exception {
        try (SmtpFalso smtp = new SmtpFalso(rcpt -> "450 probá más tarde")) {
            configurar(smtp.puerto());
            when(bandeja.pendientes(anyInt())).thenReturn(List.of(new Pendiente(1L, correo(7L, "ana@demo.test"))));

            assertEquals(0, envio.enviarPendientes());

            verify(bandeja).registrarFallo(1L);
            verify(bandeja, never()).descartar(anyLong(), anyString());
        }
    }

    @Test
    @DisplayName("Una dirección con formato inválido se descarta")
    void direccionInvalida() {
        configurar(1);
        when(bandeja.pendientes(anyInt())).thenReturn(List.of(new Pendiente(1L, correo(7L, "esto no es un mail @@"))));

        assertEquals(0, envio.enviarPendientes());

        verify(bandeja).descartar(1L, "El servidor rechazó el destinatario");
        verify(bandeja, never()).registrarFallo(anyLong());
    }

    @Test
    @DisplayName("Lo que lleva más de 24 horas esperando se descarta antes de intentar")
    void descartaLosVencidos() {
        configurar(1);
        when(bandeja.descartarVencidos(any())).thenReturn(3);
        when(bandeja.pendientes(anyInt())).thenReturn(List.of());
        LocalDateTime antes = LocalDateTime.now();

        List<String> log = loguear(() -> envio.enviarPendientes());

        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(bandeja).descartarVencidos(limite.capture());
        assertTrue(!limite.getValue().isBefore(antes.minusHours(24).minusSeconds(5)), limite.getValue().toString());
        assertTrue(!limite.getValue().isAfter(LocalDateTime.now().minusHours(24).plusSeconds(5)));
        assertTrue(log.stream().anyMatch(m -> m.contains("3 mails descartados")), log.toString());
    }

    @Test
    @DisplayName("Sin servidor de correo configurado no toca la bandeja")
    void sinServidor() {
        assertEquals(0, envio.enviarPendientes());

        verify(bandeja, never()).pendientes(anyInt());
        verify(bandeja, never()).descartarVencidos(any());
    }

    @Test
    @DisplayName("Si no se puede leer la bandeja, devuelve 0 y no propaga el error")
    void falloLaBandeja() {
        configurar(1);
        when(bandeja.pendientes(anyInt())).thenThrow(new IllegalStateException("sin base"));

        assertEquals(0, envio.enviarPendientes());
    }

    @Test
    @DisplayName("Si falla marcar uno que ya se mandó, no sale el error")
    void falloLaMarca() throws Exception {
        try (SmtpFalso smtp = new SmtpFalso()) {
            configurar(smtp.puerto());
            when(bandeja.pendientes(anyInt())).thenReturn(List.of(new Pendiente(1L, correo(7L, "ana@demo.test"))));
            doThrow(new IllegalStateException("sin base")).when(bandeja).marcarEnviado(1L);

            assertEquals(0, envio.enviarPendientes());

            assertEquals(1, smtp.recibidos());
        }
    }

    @Test
    @DisplayName("La limpieza borra los enviados a las 24 horas y los descartados a los 7 días")
    void limpieza() {
        LocalDateTime antes = LocalDateTime.now();

        envio.limpiar();

        ArgumentCaptor<LocalDateTime> enviados = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> descartados = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(bandeja).limpiar(enviados.capture(), descartados.capture());
        assertTrue(!enviados.getValue().isBefore(antes.minusHours(24).minusSeconds(5)), enviados.getValue().toString());
        assertTrue(!descartados.getValue().isBefore(antes.minusDays(7).minusSeconds(5)), descartados.getValue().toString());
        assertTrue(enviados.getValue().isAfter(descartados.getValue()));
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
