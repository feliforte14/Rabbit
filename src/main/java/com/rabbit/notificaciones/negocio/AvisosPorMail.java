package com.rabbit.notificaciones.negocio;

/**
 * Canal de mail de las notificaciones: cada aviso al comercio, además de
 * quedar en su portal, se le manda al email que cargó al darse de alta.
 *
 * DESPUÉS del commit (AFTER_SUCCESS): un servidor de correo caído o lento
 * no puede deshacer ni frenar el aviso; si el mail no sale, se loguea y el
 * aviso igual queda en el portal. Timeouts de 5 s, como las demás
 * integraciones.
 *
 * Se configura con system properties (como el banco y el circuit breaker):
 *   rabbit.mail.smtp.host   servidor SMTP; si no está, no se manda mail
 *   rabbit.mail.smtp.port   puerto (25 por defecto)
 *   rabbit.mail.remitente   From (avisos@rabbit.example por defecto)
 * Sin servidor configurado no se intenta: un entorno de desarrollo sin
 * correo funciona igual, con los avisos en el portal.
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;

@ApplicationScoped
public class AvisosPorMail {

    private static final Logger LOG = Logger.getLogger(AvisosPorMail.class.getName());
    private static final String TIMEOUT_MS = "5000";

    public void alRegistrarAviso(@Observes(during = TransactionPhase.AFTER_SUCCESS) AvisoRegistrado aviso) {
        String host = System.getProperty("rabbit.mail.smtp.host");
        if (host == null || host.isBlank()) {
            return;
        }
        // El email ya viene resuelto: acá (después del commit) no se puede
        // consultar la base.
        String destino = aviso.email();
        if (destino == null) {
            return;
        }
        try {
            Properties config = new Properties();
            config.put("mail.smtp.host", host);
            config.put("mail.smtp.port", System.getProperty("rabbit.mail.smtp.port", "25"));
            config.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
            config.put("mail.smtp.timeout", TIMEOUT_MS);
            config.put("mail.smtp.writetimeout", TIMEOUT_MS);
            MimeMessage mail = new MimeMessage(Session.getInstance(config));
            mail.setFrom(new InternetAddress(System.getProperty("rabbit.mail.remitente", "avisos@rabbit.example")));
            mail.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destino.trim()));
            mail.setSubject("Rabbit · Pedido #" + aviso.idPedido(), StandardCharsets.UTF_8.name());
            mail.setText(aviso.texto() + ".\n\nEste aviso también está en tu portal de Rabbit.", StandardCharsets.UTF_8.name());
            mail.setSentDate(new Date());
            Transport.send(mail);
            LOG.info("[Notificaciones][Mail] Aviso del pedido " + aviso.idPedido() + " enviado a " + destino);
        } catch (MessagingException | RuntimeException e) {
            LOG.warning("[Notificaciones][Mail] No se pudo mandar el aviso del pedido " + aviso.idPedido()
                    + " a " + destino + ": " + e.getMessage() + ". Queda en el portal.");
        }
    }
}
