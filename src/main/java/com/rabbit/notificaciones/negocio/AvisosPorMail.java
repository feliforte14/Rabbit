package com.rabbit.notificaciones.negocio;

/**
 * Canal de mail de las notificaciones: cada aviso al comercio, además de
 * quedar en su portal, se le manda al email que cargó al darse de alta. Y
 * cada aviso a quien recibe el pedido (ADR-040), al email que mandó el ERP.
 *
 * Cada mail lleva dos versiones, texto plano y HTML (MailHtml).
 *
 * DESPUÉS del commit (AFTER_SUCCESS): un servidor de correo caído o lento
 * no puede deshacer ni frenar el aviso; si el mail no sale, se loguea y el
 * aviso igual queda registrado en Rabbit. Timeouts de 5 s, como las demás
 * integraciones.
 *
 * Se configura con system properties (como el banco y el circuit breaker),
 * con login y TLS para un correo real como Gmail (ADR-047): ver
 * ConfiguracionSmtp. Sin servidor configurado no se intenta: un entorno de
 * desarrollo sin correo funciona igual, con los avisos en el portal.
 */

import com.rabbit.infraestructura.DatosPersonales;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.logging.Logger;

@ApplicationScoped
public class AvisosPorMail {

    private static final Logger LOG = Logger.getLogger(AvisosPorMail.class.getName());

    public void alRegistrarAviso(@Observes(during = TransactionPhase.AFTER_SUCCESS) AvisoRegistrado aviso) {
        // El email ya viene resuelto: acá (después del commit) no se puede
        // consultar la base.
        enviar(aviso.email(), aviso.idPedido(), "Pedido #" + aviso.idPedido(),
                aviso.texto() + ".\n\nEste aviso también está en tu portal de Rabbit.", null, null, null);
    }

    // ADR-040: el aviso al cliente final, mismo canal y mismas reglas.
    public void alRegistrarAvisoDestinatario(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) AvisoDestinatarioRegistrado aviso) {
        enviar(aviso.email(), aviso.idPedido(), aviso.asunto(), aviso.texto(), aviso.enlace(),
                aviso.codigoSeguimiento(), aviso.codigoEntrega());
    }

    private void enviar(String destino, Long idPedido, String titulo, String texto, String enlace,
                        String codigoSeguimiento, String codigoEntrega) {
        ConfiguracionSmtp smtp = ConfiguracionSmtp.desde(System.getProperties());
        if (smtp == null || destino == null) {
            return;
        }
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
            mail.setSubject("Rabbit · " + titulo, StandardCharsets.UTF_8.name());
            // Texto plano y HTML en el mismo mail: el cliente de correo muestra el que soporta.
            MimeMultipart alternativas = new MimeMultipart("alternative");
            MimeBodyPart plano = new MimeBodyPart();
            plano.setText(texto, StandardCharsets.UTF_8.name());
            MimeBodyPart html = new MimeBodyPart();
            html.setContent(MailHtml.render(titulo, texto, enlace, codigoSeguimiento, codigoEntrega),
                    "text/html; charset=UTF-8");
            alternativas.addBodyPart(plano);
            alternativas.addBodyPart(html);
            mail.setContent(alternativas);
            mail.setSentDate(new Date());
            enviarConClassLoaderDeLaApp(mail);
            // SEC-09: el log lleva el ID del pedido y el email enmascarado, nunca completo.
            LOG.info("[Notificaciones][Mail] Aviso del pedido " + idPedido + " enviado a "
                    + DatosPersonales.emailEnmascarado(destino));
        } catch (MessagingException | java.io.UnsupportedEncodingException | RuntimeException e) {
            // El mensaje de JavaMail a veces incluye la dirección que falló: también se enmascara.
            LOG.warning("[Notificaciones][Mail] No se pudo mandar el aviso del pedido " + idPedido
                    + " a " + DatosPersonales.emailEnmascarado(destino) + ": "
                    + DatosPersonales.sinEmails(e.getMessage()) + causa(e) + ". Queda registrado en Rabbit.");
        }
    }

    // En WildFly, JavaMail busca el manejador del tipo multipart/alternative con el
    // classloader del hilo, que en un EJB/MDB no ve el módulo de mail: falla con
    // "no object DCH for MIME type". Con el de esta clase (el de la aplicación,
    // que sí lo ve) se encuentra.
    private static void enviarConClassLoaderDeLaApp(MimeMessage mail) throws MessagingException {
        Thread hilo = Thread.currentThread();
        ClassLoader original = hilo.getContextClassLoader();
        hilo.setContextClassLoader(AvisosPorMail.class.getClassLoader());
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
