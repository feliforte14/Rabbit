package com.rabbit.seguridad.negocio;

/**
 * Sincroniza el alta/baja de usuarios de la app con el ApplicationRealm
 * nativo de WildFly. Sin esto, registrarse en usuarios.xhtml guardaba una
 * fila en la tabla "usuarios" pero la persona seguía sin poder loguearse:
 * la autenticación real la resuelve el dominio de seguridad del servidor
 * (ver LoginBean, que explica por qué se usa ApplicationRealm en vez de
 * un IdentityStore propio), no esta tabla.
 *
 * SIMPLIFICACIÓN A PROPÓSITO PARA EL ALCANCE DEL TP: escribe directo a los
 * mismos archivos de properties que usa add-user.sh
 * (application-users.properties / application-roles.properties), con el
 * mismo formato de hash (MD5 de "usuario:ApplicationRealm:contraseña",
 * ver digest-realm-name="ApplicationRealm" en standalone.xml). Funciona
 * sin reiniciar el servidor porque el properties-realm de WildFly relee
 * esos archivos por timestamp en cada intento de login — el mismo
 * mecanismo que permite que add-user.sh no necesite reiniciar nada.
 *
 * No es el camino para producción real — ahí correspondería un Elytron
 * custom realm respaldado por la tabla "usuarios", o delegar en un
 * Identity Provider externo.
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

public final class ApplicationRealmSync {

    private static final String REALM = "ApplicationRealm";

    private ApplicationRealmSync() {}

    public static void altaUsuario(String username, String passwordEnClaro, String rol) {
        try {
            Path configDir = configDir();
            escribir(configDir.resolve("application-users.properties"), username, hashDigest(username, passwordEnClaro));
            escribir(configDir.resolve("application-roles.properties"), username, rol);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo sincronizar el usuario con el ApplicationRealm del servidor", e);
        }
    }

    public static void bajaUsuario(String username) {
        try {
            Path configDir = configDir();
            quitar(configDir.resolve("application-users.properties"), username);
            quitar(configDir.resolve("application-roles.properties"), username);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo dar de baja el usuario en el ApplicationRealm del servidor", e);
        }
    }

    private static Path configDir() {
        String dir = System.getProperty("jboss.server.config.dir");
        if (dir == null) {
            throw new IllegalStateException(
                    "jboss.server.config.dir no está definido — ¿se está corriendo fuera de WildFly?");
        }
        return Path.of(dir);
    }

    private static void escribir(Path archivo, String username, String valor) throws IOException {
        List<String> lineas = sinLineaDe(archivo, username);
        lineas.add(username + "=" + valor);
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }

    private static void quitar(Path archivo, String username) throws IOException {
        Files.write(archivo, sinLineaDe(archivo, username), StandardCharsets.UTF_8);
    }

    private static List<String> sinLineaDe(Path archivo, String username) throws IOException {
        return Files.readAllLines(archivo).stream()
                .filter(linea -> !linea.startsWith(username + "="))
                .collect(Collectors.toList());
    }

    private static String hashDigest(String username, String passwordEnClaro) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String entrada = username + ":" + REALM + ":" + passwordEnClaro;
            return HexFormat.of().formatHex(md5.digest(entrada.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 no disponible en esta JVM", e);
        }
    }
}
