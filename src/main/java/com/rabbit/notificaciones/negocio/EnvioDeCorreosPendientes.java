package com.rabbit.notificaciones.negocio;

/**
 * CAPA DE NEGOCIO — envío por SMTP de los mails que están en la bandeja (ver BandejaDeCorreos).
 *
 * Lo llaman dos caminos, con la misma lógica:
 * - AvisosPorMail, justo después del commit del aviso (el camino rápido: el mail sale en el momento);
 * - un timer cada minuto (la red de contención): reenvía lo que no pudo salir porque el servidor de correo
 *   estaba caído, apenas vuelve.
 *
 * Qué pasa con cada mail:
 * - Sale bien: se marca ENVIADO.
 * - El servidor no responde (caído, lento, TLS, login): queda PENDIENTE y se corta la pasada, porque los demás
 *   fallarían igual y cada intento cuesta el timeout. Se reintenta en el minuto siguiente.
 * - El servidor rechaza el destinatario de forma definitiva (dirección inexistente): se DESCARTA y se sigue
 *   con el siguiente; reintentarlo no serviría y bloquearía a los demás.
 * - Lleva más de 24 horas esperando: se DESCARTA. Un "tu pedido llega hoy" que llega al otro día confunde.
 *
 * Sin servidor SMTP configurado no se intenta (un entorno de desarrollo sin correo funciona igual, con los
 * avisos en el portal), y tampoco se registran mails en la bandeja.
 *
 * @Singleton con @Lock(READ) y un cerrojo propio (tryLock): los dos caminos no se pisan dentro del mismo servidor,
 * así que no se manda dos veces el mismo mail. Si ya hay una pasada en curso, quien llega no espera ni falla: deja
 * una marca y la pasada en curso hace otra vuelta al terminar (EXP-03: con el bloqueo del contenedor, el que
 * esperaba se rendía a los 5 s porque cada envío por SMTP tarda unos 3 s). Entre servidores distintos podría
 * repetirse uno: es "al menos una vez".
 *
 * SEC-09: el log lleva el ID del pedido y el email enmascarado, nunca completo.
 */

import com.rabbit.infraestructura.DatosPersonales;
import com.rabbit.notificaciones.negocio.BandejaDeCorreos.Correo;
import com.rabbit.notificaciones.negocio.BandejaDeCorreos.Pendiente;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
@Lock(LockType.READ)
public class EnvioDeCorreosPendientes {

    private static final Logger LOG = Logger.getLogger(EnvioDeCorreosPendientes.class.getName());

    /** Cuántos mails se mandan por pasada. */
    static final int LOTE = 50;

    /** Horas que puede esperar un mail antes de descartarse. */
    static final int HORAS_DE_VIGENCIA = 24;

    /** Los enviados se borran al día siguiente; los descartados se conservan 7 días, para poder revisarlos. */
    static final int HORAS_DE_CONSERVACION_DE_ENVIADOS = 24;
    static final int DIAS_DE_CONSERVACION_DE_DESCARTADOS = 7;

    /** Cómo terminó un intento. */
    enum Resultado { ENVIADO, RECHAZADO, FALLO }

    /** Cuántas vueltas seguidas hace una pasada si mientras tanto le pidieron otra. */
    static final int PASADAS_MAXIMAS = 3;

    private final ReentrantLock enCurso = new ReentrantLock();
    private final AtomicBoolean pidieronOtraPasada = new AtomicBoolean();

    /** Lo que dejó una pasada: cuántos salieron y si hubo que cortarla porque el servidor no respondió. */
    private record Pasada(int enviados, boolean cortada) {
    }

    @Inject
    private BandejaDeCorreos bandeja;

    @PostConstruct
    public void alArrancar() {
        LOG.info("[Notificaciones] Reenvío de mails activo: cada minuto manda los pendientes de la bandeja");
    }

    /** Red de contención: lo que no salió (servidor de correo caído) sale cuando el servidor vuelve. */
    @Schedule(hour = "*", minute = "*", second = "0", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void reintentarPendientes() {
        enviarPendientes();
    }

    /** Los enviados se borran al día siguiente y los descartados a los 7 días. */
    @Schedule(hour = "3", minute = "40", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void limpiar() {
        int borrados = bandeja.limpiar(LocalDateTime.now().minusHours(HORAS_DE_CONSERVACION_DE_ENVIADOS),
                LocalDateTime.now().minusDays(DIAS_DE_CONSERVACION_DE_DESCARTADOS));
        if (borrados > 0) {
            LOG.info("[Notificaciones] Bandeja de mails: " + borrados + " resueltos borrados");
        }
    }

    /**
     * Manda los mails pendientes y marca cada uno. Devuelve cuántos salieron. Sin transacción: el envío sale en
     * el momento y cada marca abre la suya (ver BandejaDeCorreos).
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int enviarPendientes() {
        ConfiguracionSmtp smtp = ConfiguracionSmtp.desde(System.getProperties());
        if (smtp == null) {
            return 0;
        }
        if (!enCurso.tryLock()) {
            // Ya hay una pasada mandando: no se espera. Esa pasada hace otra vuelta al terminar, o el timer del
            // minuto siguiente, y el mail que dejó este aviso sale igual.
            pidieronOtraPasada.set(true);
            return 0;
        }
        try {
            int enviados = 0;
            for (int vuelta = 0; vuelta < PASADAS_MAXIMAS; vuelta++) {
                pidieronOtraPasada.set(false);
                Pasada pasada = pasada(smtp);
                enviados += pasada.enviados();
                if (pasada.cortada() || !pidieronOtraPasada.get()) {
                    break;
                }
            }
            return enviados;
        } finally {
            enCurso.unlock();
        }
    }

    private Pasada pasada(ConfiguracionSmtp smtp) {
        List<Pendiente> pendientes;
        try {
            int vencidos = bandeja.descartarVencidos(LocalDateTime.now().minusHours(HORAS_DE_VIGENCIA));
            if (vencidos > 0) {
                LOG.warning("[Notificaciones][Mail] " + vencidos + " mails descartados: llevaban más de "
                        + HORAS_DE_VIGENCIA + " horas sin poder mandarse");
            }
            pendientes = bandeja.pendientes(LOTE);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[Notificaciones][Mail] No se pudo leer la bandeja de mails", e);
            return new Pasada(0, true);
        }
        int enviados = 0;
        boolean cortada = false;
        for (Pendiente pendiente : pendientes) {
            Resultado resultado = enviar(smtp, pendiente.correo());
            try {
                if (resultado == Resultado.ENVIADO) {
                    bandeja.marcarEnviado(pendiente.id());
                    enviados++;
                } else if (resultado == Resultado.RECHAZADO) {
                    bandeja.descartar(pendiente.id(), "El servidor rechazó el destinatario");
                } else {
                    bandeja.registrarFallo(pendiente.id());
                }
            } catch (RuntimeException e) {
                // Si falla la marca de uno ya enviado puede repetirse al minuto siguiente: es "al menos una vez".
                LOG.log(Level.WARNING, "[Notificaciones][Mail] No se pudo marcar el mail del pedido "
                        + pendiente.correo().idPedido(), e);
            }
            if (resultado == Resultado.FALLO) {
                // Si el servidor no responde, los demás también fallarían: se reintenta en la próxima pasada.
                cortada = true;
                break;
            }
        }
        return new Pasada(enviados, cortada);
    }

    private Resultado enviar(ConfiguracionSmtp smtp, Correo correo) {
        String destino = correo.destino();
        try {
            Session sesion = smtp.conLogin()
                    ? Session.getInstance(smtp.propiedades, new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(smtp.usuario, smtp.password);
                        }
                    })
                    : Session.getInstance(smtp.propiedades);
            MimeMessage mail = new MimeMessage(sesion);
            mail.setFrom(new InternetAddress(smtp.remitente, "Rabbit", StandardCharsets.UTF_8.name()));
            mail.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destino.trim()));
            mail.setSubject("Rabbit · " + correo.titulo(), StandardCharsets.UTF_8.name());
            // Texto plano y HTML en el mismo mail: el cliente de correo muestra el que soporta.
            MimeMultipart alternativas = new MimeMultipart("alternative");
            MimeBodyPart plano = new MimeBodyPart();
            plano.setText(correo.texto(), StandardCharsets.UTF_8.name());
            MimeBodyPart html = new MimeBodyPart();
            html.setContent(MailHtml.render(correo.titulo(), correo.texto(), correo.enlace(),
                    correo.codigoSeguimiento(), correo.codigoEntrega()), "text/html; charset=UTF-8");
            alternativas.addBodyPart(plano);
            alternativas.addBodyPart(html);
            mail.setContent(alternativas);
            mail.setSentDate(new Date());
            enviarConClassLoaderDeLaApp(mail);
            LOG.info("[Notificaciones][Mail] Aviso del pedido " + correo.idPedido() + " enviado a "
                    + DatosPersonales.emailEnmascarado(destino));
            return Resultado.ENVIADO;
        } catch (SendFailedException e) {
            // El servidor contestó y rechazó la dirección (5xx): reintentar no serviría. Si quedó algo sin
            // enviar por un rechazo pasajero (4xx), sí se reintenta.
            Address[] definitivas = e.getInvalidAddresses();
            Address[] pasajeras = e.getValidUnsentAddresses();
            boolean definitivo = definitivas != null && definitivas.length > 0
                    && (pasajeras == null || pasajeras.length == 0);
            avisar(correo, e, definitivo ? "El servidor rechazó la dirección: se descarta." : "Se reintenta.");
            return definitivo ? Resultado.RECHAZADO : Resultado.FALLO;
        } catch (AddressException e) {
            avisar(correo, e, "La dirección no es válida: se descarta.");
            return Resultado.RECHAZADO;
        } catch (MessagingException | java.io.UnsupportedEncodingException | RuntimeException e) {
            avisar(correo, e, "Se reintenta.");
            return Resultado.FALLO;
        }
    }

    // El mensaje de JavaMail a veces incluye la dirección que falló: también se enmascara.
    private static void avisar(Correo correo, Exception e, String quePasa) {
        LOG.warning("[Notificaciones][Mail] No se pudo mandar el aviso del pedido " + correo.idPedido() + " a "
                + DatosPersonales.emailEnmascarado(correo.destino()) + ": "
                + DatosPersonales.sinEmails(e.getMessage()) + causa(e) + ". " + quePasa);
    }

    // En WildFly, JavaMail busca el manejador del tipo multipart/alternative con el
    // classloader del hilo, que en un EJB/MDB no ve el módulo de mail: falla con
    // "no object DCH for MIME type". Con el de esta clase (el de la aplicación,
    // que sí lo ve) se encuentra.
    private static void enviarConClassLoaderDeLaApp(MimeMessage mail) throws MessagingException {
        Thread hilo = Thread.currentThread();
        ClassLoader original = hilo.getContextClassLoader();
        hilo.setContextClassLoader(EnvioDeCorreosPendientes.class.getClassLoader());
        try {
            Transport.send(mail);
        } finally {
            hilo.setContextClassLoader(original);
        }
    }

    // JavaMail envuelve el motivo real ("IOException while sending message"):
    // se agrega la causa de fondo para poder diagnosticar (TLS, login, red).
    private static String causa(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz == e ? "" : " (causa: " + raiz.getClass().getSimpleName() + ": "
                + DatosPersonales.sinEmails(raiz.getMessage()) + ")";
    }
}
