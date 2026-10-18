package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Cómo se conecta Rabbit al servidor de correo según la configuración (ADR-047). */
class ConfiguracionSmtpTest {

    private static Properties props(String... claveValor) {
        Properties p = new Properties();
        for (int i = 0; i < claveValor.length; i += 2) {
            p.setProperty(claveValor[i], claveValor[i + 1]);
        }
        return p;
    }

    @Test
    void sinServidorNoSeMandaNada() {
        assertNull(ConfiguracionSmtp.desde(props()));
        assertNull(ConfiguracionSmtp.desde(props("rabbit.mail.smtp.host", "  ")));
    }

    @Test
    void conGmailUsaLoginStartTlsYElPuerto587() {
        ConfiguracionSmtp c = ConfiguracionSmtp.desde(props(
                "rabbit.mail.smtp.host", "smtp.gmail.com",
                "rabbit.mail.smtp.usuario", "rabbit.avisos@gmail.com",
                "rabbit.mail.smtp.password", "abcd efgh ijkl mnop"));
        assertTrue(c.conLogin());
        assertEquals("587", c.propiedades.getProperty("mail.smtp.port"));
        assertEquals("true", c.propiedades.getProperty("mail.smtp.auth"));
        assertEquals("true", c.propiedades.getProperty("mail.smtp.starttls.required"));
        // Gmail exige que el remitente sea la misma cuenta.
        assertEquals("rabbit.avisos@gmail.com", c.remitente);
    }

    @Test
    void conSslUsaElPuerto465() {
        ConfiguracionSmtp c = ConfiguracionSmtp.desde(props(
                "rabbit.mail.smtp.host", "smtp.ejemplo.com",
                "rabbit.mail.smtp.usuario", "avisos@ejemplo.com",
                "rabbit.mail.smtp.password", "x",
                "rabbit.mail.smtp.seguridad", "ssl"));
        assertEquals("465", c.propiedades.getProperty("mail.smtp.port"));
        assertEquals("true", c.propiedades.getProperty("mail.smtp.ssl.enable"));
        assertNull(c.propiedades.getProperty("mail.smtp.starttls.enable"));
    }

    @Test
    void unServidorLocalSinUsuarioSigueComoAntes() {
        ConfiguracionSmtp c = ConfiguracionSmtp.desde(props("rabbit.mail.smtp.host", "127.0.0.1",
                "rabbit.mail.smtp.port", "2525"));
        assertFalse(c.conLogin());
        assertEquals("2525", c.propiedades.getProperty("mail.smtp.port"));
        assertEquals("false", c.propiedades.getProperty("mail.smtp.auth"));
        assertNull(c.propiedades.getProperty("mail.smtp.starttls.enable"));
        assertEquals("avisos@rabbit.example", c.remitente);
    }

    @Test
    void unUsuarioSinContrasenaNoIntentaLogin() {
        ConfiguracionSmtp c = ConfiguracionSmtp.desde(props("rabbit.mail.smtp.host", "smtp.gmail.com",
                "rabbit.mail.smtp.usuario", "rabbit.avisos@gmail.com"));
        assertFalse(c.conLogin());
    }
}
