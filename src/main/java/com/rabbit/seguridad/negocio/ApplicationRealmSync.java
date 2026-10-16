package com.rabbit.seguridad.negocio;

/**
 * Lo que queda del realm de archivos de cada WildFly
 * (application-users.properties / application-roles.properties, los de
 * add-user.sh) desde que las cuentas viven en la base (ADR-031).
 *
 * Esos archivos ya no reciben altas: el realm JDBC lee las contraseñas de
 * la tabla "usuarios". Siguen sirviendo para dos cosas: el administrador
 * inicial de cada instalación (add-user.sh) y las cuentas creadas antes de
 * ADR-031, que todavía no tienen contraseña en la base. Esta clase solo
 * consulta si un nombre está tomado ahí y borra la línea de una cuenta
 * cuando se la da de baja o se le pasa la contraseña a la base.
 *
 * SEGURIDAD: los archivos son texto "usuario=valor" por línea, así que un
 * username con salto de línea, "=" o ":" podría alterar las entradas de
 * otros usuarios. Por eso solo se acepta un username que pase
 * {@link #usernameValido}, y las escrituras se serializan.
 *
 * ATOMICIDAD: cada archivo se escribe entero en un temporal y se reemplaza
 * con un move atómico, así nunca queda a medio escribir. Los dos archivos
 * van juntos: si falla el segundo, el primero vuelve a su contenido
 * original.
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ApplicationRealmSync {

    private static final Pattern PATRON_USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");

    // Serializa las escrituras de los dos archivos (leer, filtrar y
    // reescribir no es atómico).
    private static final Object LOCK = new Object();

    private ApplicationRealmSync() {}

    /**
     * Borra la línea del usuario de los dos archivos del realm de esta
     * máquina, si la tiene. Si no la tiene, no cambia nada.
     *
     * @param username usuario a quitar de los archivos
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
}
