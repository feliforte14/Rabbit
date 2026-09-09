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

    /**
     * Da de alta al usuario en el ApplicationRealm: escribe su hash en
     * application-users.properties y su rol en application-roles.properties.
     * A partir de acá ya puede loguearse (ver LoginBean.login).
     *
     * @param username usuario, igual al de la tabla "usuarios"
     * @param passwordEnClaro contraseña sin hashear (se hashea acá, con el algoritmo propio del realm)
     * @param rol nombre del rol (ver Rol) que WildFly va a exponer como "group" al autenticar
     */
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

    /**
     * Quita al usuario del ApplicationRealm (borra su línea de ambos
     * archivos de properties) — a partir de acá ya no puede autenticarse,
     * aunque la fila siga existiendo en la tabla "usuarios" (ver
     * UsuarioService.darDeBaja, que es baja lógica, no elimina la fila).
     *
     * @param username usuario a remover del realm
     */
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

    // Carpeta de configuración de ESTE WildFly (donde viven los .properties
    // del realm), expuesta por el propio contenedor como system property al
    // arrancar. Falla explícito si se corre fuera de WildFly (por ejemplo,
    // un test unitario) en vez de fallar más adelante con un path nulo.
    private static Path configDir() {
        String dir = System.getProperty("jboss.server.config.dir");
        if (dir == null) {
            throw new IllegalStateException(
                    "jboss.server.config.dir no está definido — ¿se está corriendo fuera de WildFly?");
        }
        return Path.of(dir);
    }

    // Reemplaza (o agrega) la línea "username=valor" de un archivo de
    // properties del realm — primero saca la línea vieja si existía, para
    // no dejar duplicados que el properties-realm de WildFly no sabría
    // resolver.
    private static void escribir(Path archivo, String username, String valor) throws IOException {
        List<String> lineas = sinLineaDe(archivo, username);
        lineas.add(username + "=" + valor);
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }

    // Elimina la línea "username=..." de un archivo de properties del realm.
    private static void quitar(Path archivo, String username) throws IOException {
        Files.write(archivo, sinLineaDe(archivo, username), StandardCharsets.UTF_8);
    }

    // Lee un archivo de properties del realm y devuelve sus líneas sin la
    // que empieza con "username=" (si no había ninguna, no cambia nada).
    private static List<String> sinLineaDe(Path archivo, String username) throws IOException {
        return Files.readAllLines(archivo).stream()
                .filter(linea -> !linea.startsWith(username + "="))
                .collect(Collectors.toList());
    }

    // Hash MD5 de "usuario:realm:contraseña" — el formato exacto que
    // exige el properties-realm de WildFly para ApplicationRealm (ver
    // digest-realm-name en standalone.xml). Distinto del SHA-256 de
    // PasswordUtil: ese es el hash que guarda la tabla "usuarios", este es
    // el que entiende el archivo de properties del servidor.
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
