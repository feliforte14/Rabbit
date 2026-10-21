package com.rabbit.notificaciones.negocio;

/**
 * Canal de mail de las notificaciones: cada aviso al comercio, además de
 * quedar en su portal, se le manda al email que cargó al darse de alta. Y
 * cada aviso a quien recibe el pedido (ADR-040), al email que mandó el ERP.
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
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.logging.Logger;

@ApplicationScoped
public class AvisosPorMail {

    private static final Logger LOG = Logger.getLogger(AvisosPorMail.class.getName());

    public void alRegistrarAviso(@Observes(during = TransactionPhase.AFTER_SUCCESS) AvisoRegistrado aviso) {
        // El email ya viene resuelto: acá (después del commit) no se puede
        // consultar la base.
        enviar(aviso.email(), aviso.idPedido(), "Rabbit · Pedido #" + aviso.idPedido(),
                aviso.texto() + ".\n\nEste aviso también está en tu portal de Rabbit.");
    }

    // ADR-040: el aviso al cliente final, mismo canal y mismas reglas.
    public void alRegistrarAvisoDestinatario(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) AvisoDestinatarioRegistrado aviso) {
        enviar(aviso.email(), aviso.idPedido(), "Rabbit · " + aviso.asunto(), aviso.texto());
    }

    private void enviar(String destino, Long idPedido, String asunto, String texto) {
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
            mail.setSubject(asunto, StandardCharsets.UTF_8.name());
            mail.setText(texto, StandardCharsets.UTF_8.name());
            mail.setSentDate(new Date());
            Transport.send(mail);
            // SEC-09: el log lleva el ID del pedido y el email enmascarado, nunca completo.
            LOG.info("[Notificaciones][Mail] Aviso del pedido " + idPedido + " enviado a "
                    + DatosPersonales.emailEnmascarado(destino));
        } catch (MessagingException | java.io.UnsupportedEncodingException | RuntimeException e) {
            // El mensaje de JavaMail a veces incluye la dirección que falló: también se enmascara.
            LOG.warning("[Notificaciones][Mail] No se pudo mandar el aviso del pedido " + idPedido
                    + " a " + DatosPersonales.emailEnmascarado(destino) + ": "
                    + DatosPersonales.sinEmails(e.getMessage()) + ". Queda registrado en Rabbit.");
        }
    }
}
