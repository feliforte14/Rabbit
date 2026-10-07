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
 * SEGURIDAD: los archivos son texto "usuario=valor" por línea, así que un
 * username con salto de línea, "=" o ":" podría alterar las entradas de
 * otros usuarios. Por eso solo se acepta un username que pase
 * {@link #usernameValido} (se revisa acá además de en UsuarioService), no
 * se pisa un usuario que ya exista en el realm (por ejemplo el del ERP,
 * creado con add-user.sh), y las escrituras se serializan para que dos
 * altas simultáneas no se pisen entre sí.
 *
 * ATOMICIDAD: cada archivo se escribe entero en un temporal y se reemplaza
 * con un move atómico, así nunca queda a medio escribir. Los dos archivos
 * van juntos: si falla el segundo, el primero vuelve a su contenido
 * original, así un usuario nunca queda en uno solo de los dos.
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ApplicationRealmSync {

    private static final String REALM = "ApplicationRealm";

    private static final Pattern PATRON_USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");

    // Serializa las escrituras de los dos archivos (leer, filtrar y
    // reescribir no es atómico).
    private static final Object LOCK = new Object();

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
        exigirUsernameValido(username);
        try {
            Path configDir = configDir();
            Path usuarios = configDir.resolve("application-users.properties");
            synchronized (LOCK) {
                if (tieneLinea(usuarios, username)) {
                    throw new IllegalStateException("El usuario " + username + " ya existe en el servidor");
                }
                Path roles = configDir.resolve("application-roles.properties");
                Map<Path, List<String>> nuevos = new LinkedHashMap<>();
                nuevos.put(usuarios, conLinea(usuarios, username, hashDigest(username, passwordEnClaro)));
                nuevos.put(roles, conLinea(roles, username, rol));
                reemplazar(nuevos);
            }
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
        exigirUsernameValido(username);
        try {
            Path configDir = configDir();
            Path usuarios = configDir.resolve("application-users.properties");
            Path roles = configDir.resolve("application-roles.properties");
            synchronized (LOCK) {
                Map<Path, List<String>> nuevos = new LinkedHashMap<>();
                nuevos.put(usuarios, sinLineaDe(usuarios, username));
                nuevos.put(roles, sinLineaDe(roles, username));
                reemplazar(nuevos);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo dar de baja el usuario en el ApplicationRealm del servidor", e);
        }
    }

    /**
     * true si el usuario ya existe en el realm del servidor, aunque no esté
     * en la tabla "usuarios" (por ejemplo, creado con add-user.sh).
     */
    public static boolean existeEnRealm(String username) {
        try {
            return tieneLinea(configDir().resolve("application-users.properties"), username);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el ApplicationRealm del servidor", e);
        }
    }

    /** Letras, números, punto, guion y guion bajo; entre 3 y 30 caracteres. */
    public static boolean usernameValido(String username) {
        return username != null && PATRON_USERNAME.matcher(username).matches();
    }

    private static void exigirUsernameValido(String username) {
        if (!usernameValido(username)) {
            throw new IllegalArgumentException("Username inválido para el realm");
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
    private static List<String> conLinea(Path archivo, String username, String valor) throws IOException {
        List<String> lineas = sinLineaDe(archivo, username);
        lineas.add(username + "=" + valor);
        return lineas;
    }

    // Reemplaza varios archivos como una unidad: si falla uno, los que ya
    // se reemplazaron vuelven a su contenido original.
    private static void reemplazar(Map<Path, List<String>> nuevos) throws IOException {
        Map<Path, List<String>> originales = new LinkedHashMap<>();
        for (Path archivo : nuevos.keySet()) {
            originales.put(archivo, Files.readAllLines(archivo, StandardCharsets.UTF_8));
        }
        List<Path> reemplazados = new ArrayList<>();
        try {
            for (Map.Entry<Path, List<String>> e : nuevos.entrySet()) {
                escribirAtomico(e.getKey(), e.getValue());
                reemplazados.add(e.getKey());
            }
        } catch (IOException | RuntimeException e) {
            for (Path archivo : reemplazados) {
                try {
                    escribirAtomico(archivo, originales.get(archivo));
                } catch (IOException restaurar) {
                    e.addSuppressed(restaurar);
                }
            }
            throw e;
        }
    }

    // Escribe el archivo completo en un temporal de la misma carpeta y lo
    // mueve encima del original: el que lee (WildFly) ve el archivo viejo
    // o el nuevo, nunca uno truncado.
    private static void escribirAtomico(Path archivo, List<String> lineas) throws IOException {
        Path temporal = Files.createTempFile(archivo.getParent(), archivo.getFileName().toString(), ".tmp");
        try {
            Files.write(temporal, lineas, StandardCharsets.UTF_8);
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static boolean tieneLinea(Path archivo, String username) throws IOException {
        return Files.readAllLines(archivo).stream().anyMatch(linea -> linea.startsWith(username + "="));
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
    // digest-realm-name en standalone.xml). Es la única copia de la
    // credencial: la tabla "usuarios" no guarda contraseñas.
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
