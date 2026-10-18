package com.rabbit.notificaciones.negocio;

/**
 * Cómo conectarse al servidor de correo (ADR-047), leído de las system
 * properties de WildFly en cada envío (se cambia sin redesplegar):
 *
 *   rabbit.mail.smtp.host       servidor; sin él no se manda mail
 *   rabbit.mail.smtp.port       puerto (587 con usuario, 25 sin usuario)
 *   rabbit.mail.smtp.usuario    cuenta, si el servidor pide login (Gmail sí)
 *   rabbit.mail.smtp.password   su contraseña (en Gmail, una contraseña de aplicación)
 *   rabbit.mail.smtp.seguridad  starttls (por defecto con usuario), ssl (puerto 465) o ninguna
 *   rabbit.mail.remitente       From; con Gmail tiene que ser la misma cuenta
 *
 * Las credenciales viven solo en la configuración de cada WildFly, nunca
 * en el repositorio (es público), igual que las de la base.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import java.util.Properties;

final class ConfiguracionSmtp {

    private static final String TIMEOUT_MS = "5000";

    final String host;
    final String usuario;
    final String password;
    final String remitente;
    final Properties propiedades;

    private ConfiguracionSmtp(String host, String usuario, String password, String remitente, Properties propiedades) {
        this.host = host;
        this.usuario = usuario;
        this.password = password;
        this.remitente = remitente;
        this.propiedades = propiedades;
    }

    /** La configuración actual, o null si no hay servidor configurado. */
    static ConfiguracionSmtp desde(Properties sistema) {
        String host = valor(sistema, "rabbit.mail.smtp.host");
        if (host == null) {
            return null;
        }
        String usuario = valor(sistema, "rabbit.mail.smtp.usuario");
        String password = sistema.getProperty("rabbit.mail.smtp.password");
        boolean conLogin = usuario != null && password != null && !password.isEmpty();
        String seguridad = valor(sistema, "rabbit.mail.smtp.seguridad");
        if (seguridad == null) {
            seguridad = conLogin ? "starttls" : "ninguna";
        }
        String puertoPorDefecto = switch (seguridad.toLowerCase()) {
            case "ssl" -> "465";
            case "starttls" -> "587";
            default -> "25";
        };
        String remitente = valor(sistema, "rabbit.mail.remitente");
        if (remitente == null) {
            remitente = conLogin && usuario.contains("@") ? usuario : "avisos@rabbit.example";
        }

        Properties p = new Properties();
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", valorO(sistema, "rabbit.mail.smtp.port", puertoPorDefecto));
        p.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        p.put("mail.smtp.timeout", TIMEOUT_MS);
        p.put("mail.smtp.writetimeout", TIMEOUT_MS);
        p.put("mail.smtp.auth", String.valueOf(conLogin));
        switch (seguridad.toLowerCase()) {
            case "starttls" -> {
                p.put("mail.smtp.starttls.enable", "true");
                p.put("mail.smtp.starttls.required", "true");
                p.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            }
            case "ssl" -> {
                p.put("mail.smtp.ssl.enable", "true");
                p.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            }
            default -> {
                // Sin cifrado: solo para un servidor de prueba local.
            }
        }
        return new ConfiguracionSmtp(host, conLogin ? usuario : null, conLogin ? password : null, remitente, p);
    }

    boolean conLogin() {
        return usuario != null;
    }

    private static String valor(Properties sistema, String clave) {
        String v = sistema.getProperty(clave);
        return v == null || v.isBlank() ? null : v.strip();
    }

    private static String valorO(Properties sistema, String clave, String porDefecto) {
        String v = valor(sistema, clave);
        return v != null ? v : porDefecto;
    }
}
