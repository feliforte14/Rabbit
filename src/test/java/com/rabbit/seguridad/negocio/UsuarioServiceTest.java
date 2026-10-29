package com.rabbit.seguridad.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.seguridad.datos.UsuarioRepository;
import com.rabbit.seguridad.datos.model.Rol;
import com.rabbit.seguridad.datos.model.Usuario;
import com.rabbit.seguridad.dto.DatosUsuarioDTO;
import com.rabbit.seguridad.dto.UsuarioDTO;
import jakarta.ejb.SessionContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * El servicio también mira los archivos del realm de WildFly (cuentas creadas con add-user.sh). En el
 * test se apunta jboss.server.config.dir a una carpeta temporal con esos dos archivos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock private UsuarioRepository repository;
    @Mock private SessionContext contexto;
    @Mock private IConsultaComercios comercios;
    @Mock private IGestionRepartidores repartidores;

    @InjectMocks private UsuarioService servicio;

    @TempDir Path config;

    private Path usuariosRealm;
    private Path rolesRealm;

    @BeforeEach
    void realmDeArchivos() throws IOException {
        usuariosRealm = config.resolve("application-users.properties");
        rolesRealm = config.resolve("application-roles.properties");
        Files.write(usuariosRealm, List.of("#comentario", "viejo=abc123", "otro=def456"));
        Files.write(rolesRealm, List.of("viejo=OPERADOR", "otro=ADMINISTRADOR"));
        System.setProperty("jboss.server.config.dir", config.toString());
        Principal yo = () -> "admin";
        when(contexto.getCallerPrincipal()).thenReturn(yo);
    }

    @AfterEach
    void limpiar() {
        System.clearProperty("jboss.server.config.dir");
    }

    private static Usuario usuario(Long id, String username, Rol rol, boolean activo) {
        Usuario u = conId(new Usuario(), id);
        u.setUsername(username);
        u.setRol(rol);
        u.setActivo(activo);
        return u;
    }

    private static DatosUsuarioDTO datos(String username, String password, Rol rol) {
        DatosUsuarioDTO d = new DatosUsuarioDTO();
        d.username = username;
        d.password = password;
        d.rol = rol;
        return d;
    }

    @Nested
    class Alta {

        @Test
        void guardaLaCuentaActivaConLaContrasenaHasheadaConBcrypt() {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 5L));

            Long id = servicio.registrarUsuario(datos("  ana.op  ", "secreta1", Rol.OPERADOR));

            assertEquals(5L, id);
            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(repository).guardar(guardado.capture());
            Usuario u = guardado.getValue();
            assertEquals("ana.op", u.getUsername());
            assertEquals(Rol.OPERADOR, u.getRol());
            assertTrue(u.isActivo());
            assertTrue(u.getHashClave().startsWith("$2a$10$"), u.getHashClave());
            assertNotEquals("secreta1", u.getHashClave());
            assertTrue(Claves.coincide("secreta1", u.getHashClave()));
            assertNull(u.getIdComercio());
            assertNull(u.getIdRepartidor());
        }

        @Test
        void sinRolQuedaComoOperador() {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 5L));

            servicio.registrarUsuario(datos("ana.op", "secreta1", null));

            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(repository).guardar(guardado.capture());
            assertEquals(Rol.OPERADOR, guardado.getValue().getRol());
        }

        @ParameterizedTest
        @EnumSource(value = Rol.class, names = {"COMERCIO", "ERP"})
        void unaCuentaDeComercioOErpQuedaAtadaASuComercio(Rol rol) {
            DatosUsuarioDTO d = datos("tienda1", "secreta1", rol);
            d.idComercio = 7L;
            d.idRepartidor = 99L;
            when(comercios.validarComercioActivo(7L)).thenReturn(true);
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 6L));

            servicio.registrarUsuario(d);

            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(repository).guardar(guardado.capture());
            assertEquals(7L, guardado.getValue().getIdComercio());
            assertNull(guardado.getValue().getIdRepartidor());
        }

        @ParameterizedTest
        @EnumSource(value = Rol.class, names = {"COMERCIO", "ERP"})
        void unaCuentaDeComercioNecesitaUnComercioActivo(Rol rol) {
            DatosUsuarioDTO sinComercio = datos("tienda1", "secreta1", rol);
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarUsuario(sinComercio));
            assertEquals("Elegí el comercio que representa la cuenta", e.getMessage());

            DatosUsuarioDTO deBaja = datos("tienda1", "secreta1", rol);
            deBaja.idComercio = 8L;
            e = assertThrows(ValidacionException.class, () -> servicio.registrarUsuario(deBaja));
            assertEquals("El comercio elegido no existe o está dado de baja", e.getMessage());
            verify(repository, never()).guardar(any());
        }

        @Test
        void unaCuentaDeRepartidorQuedaAtadaASuRepartidor() {
            DatosUsuarioDTO d = datos("moto1", "secreta1", Rol.REPARTIDOR);
            d.idRepartidor = 3L;
            d.idComercio = 99L;
            when(repartidores.obtenerRepartidor(3L)).thenReturn(new RepartidorDTO());
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 7L));

            servicio.registrarUsuario(d);

            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(repository).guardar(guardado.capture());
            assertEquals(3L, guardado.getValue().getIdRepartidor());
            assertNull(guardado.getValue().getIdComercio());
        }

        @Test
        void unaCuentaDeRepartidorNecesitaUnRepartidorExistenteYSinCuenta() {
            DatosUsuarioDTO sinRepartidor = datos("moto1", "secreta1", Rol.REPARTIDOR);
            assertEquals("Elegí el repartidor que representa la cuenta",
                    assertThrows(ValidacionException.class, () -> servicio.registrarUsuario(sinRepartidor)).getMessage());

            DatosUsuarioDTO inexistente = datos("moto1", "secreta1", Rol.REPARTIDOR);
            inexistente.idRepartidor = 3L;
            assertEquals("El repartidor elegido no existe",
                    assertThrows(ValidacionException.class, () -> servicio.registrarUsuario(inexistente)).getMessage());

            when(repartidores.obtenerRepartidor(3L)).thenReturn(new RepartidorDTO());
            when(repository.existeUsuarioDeRepartidor(3L)).thenReturn(true);
            assertEquals("Ese repartidor ya tiene una cuenta",
                    assertThrows(ValidacionException.class, () -> servicio.registrarUsuario(inexistente)).getMessage());
            verify(repository, never()).guardar(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rechazaSinUsername(String username) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarUsuario(datos(username, "secreta1", Rol.OPERADOR)));
            assertEquals("El nombre de usuario es obligatorio", e.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ab", "nombre con espacio", "a=b", "inyección", "linea\nnueva",
                "x23456789012345678901234567890y"})
        void rechazaUsernamesQuePuedenRomperElRealm(String username) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarUsuario(datos(username, "secreta1", Rol.OPERADOR)));
            assertTrue(e.getMessage().startsWith("El nombre de usuario debe tener entre 3 y 30 caracteres"));
            verify(repository, never()).guardar(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "a.b-c_9", "x2345678901234567890123456789z"})
        void aceptaUsernamesDeTresATreintaCaracteresPermitidos(String username) {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 1L));

            assertEquals(1L, servicio.registrarUsuario(datos(username, "secreta1", Rol.OPERADOR)));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "      ")
        void rechazaSinContrasena(String password) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarUsuario(datos("ana.op", password, Rol.OPERADOR)));
            assertEquals("La contraseña es obligatoria", e.getMessage());
        }

        @Test
        void exigeAlMenosSeisCaracteresDeContrasena() {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarUsuario(datos("ana.op", "12345", Rol.OPERADOR)));
            assertEquals("La contraseña debe tener al menos 6 caracteres", e.getMessage());

            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 1L));
            assertEquals(1L, servicio.registrarUsuario(datos("ana.op", "123456", Rol.OPERADOR)));
        }

        @Test
        void rechazaUnUsernameQueYaExisteEnLaBase() {
            when(repository.existeUsername("ana.op")).thenReturn(true);

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarUsuario(datos("ana.op", "secreta1", Rol.OPERADOR)));
            assertEquals("Ya existe un usuario con el nombre \"ana.op\"", e.getMessage());
        }

        @Test
        void rechazaUnUsernameQueYaExisteEnElRealmDelServidor() {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarUsuario(datos("viejo", "secreta1", Rol.OPERADOR)));
            assertEquals("Ya existe un usuario con el nombre \"viejo\"", e.getMessage());
            verify(repository, never()).guardar(any());
        }
    }

    @Nested
    class BajaYEliminacion {

        @Test
        void laBajaLaDesactivaYLaSacaDelRealmDeArchivos() throws IOException {
            Usuario u = usuario(1L, "viejo", Rol.OPERADOR, true);
            when(repository.buscarPorId(1L)).thenReturn(u);

            servicio.darDeBaja(1L);

            assertFalse(u.isActivo());
            verify(repository).actualizar(u);
            assertEquals(List.of("#comentario", "otro=def456"), Files.readAllLines(usuariosRealm));
            assertEquals(List.of("otro=ADMINISTRADOR"), Files.readAllLines(rolesRealm));
        }

        @Test
        void eliminarBorraLaFilaYLaLineaDelRealm() throws IOException {
            Usuario u = usuario(1L, "viejo", Rol.OPERADOR, false);
            when(repository.buscarPorId(1L)).thenReturn(u);

            servicio.eliminarUsuario(1L);

            verify(repository).eliminar(u);
            assertFalse(Files.readString(usuariosRealm).contains("viejo="));
        }

        @Test
        void nadieSeDaDeBajaASiMismo() {
            when(repository.buscarPorId(2L)).thenReturn(usuario(2L, "admin", Rol.ADMINISTRADOR, true));

            assertEquals("No podés dar de baja tu propia cuenta",
                    assertThrows(ValidacionException.class, () -> servicio.darDeBaja(2L)).getMessage());
            assertEquals("No podés eliminar tu propia cuenta",
                    assertThrows(ValidacionException.class, () -> servicio.eliminarUsuario(2L)).getMessage());
            verify(repository, never()).actualizar(any());
            verify(repository, never()).eliminar(any());
        }

        @Test
        void noSeSacaAlUltimoAdministradorActivo() {
            when(repository.buscarPorId(3L)).thenReturn(usuario(3L, "otro", Rol.ADMINISTRADOR, true));
            when(repository.contarAdministradoresActivos()).thenReturn(1L);

            assertEquals("No se puede dar de baja al último administrador activo",
                    assertThrows(ValidacionException.class, () -> servicio.darDeBaja(3L)).getMessage());
            assertEquals("No se puede eliminar al último administrador activo",
                    assertThrows(ValidacionException.class, () -> servicio.eliminarUsuario(3L)).getMessage());
        }

        @Test
        void conOtroAdministradorActivoSiSePuede() {
            Usuario u = usuario(3L, "otro", Rol.ADMINISTRADOR, true);
            when(repository.buscarPorId(3L)).thenReturn(u);
            when(repository.contarAdministradoresActivos()).thenReturn(2L);

            servicio.darDeBaja(3L);

            assertFalse(u.isActivo());
        }

        @Test
        void unAdministradorYaDadoDeBajaSePuedeEliminarAunqueQuedeUnoSolo() {
            Usuario u = usuario(3L, "otro", Rol.ADMINISTRADOR, false);
            when(repository.buscarPorId(3L)).thenReturn(u);
            when(repository.contarAdministradoresActivos()).thenReturn(1L);

            servicio.eliminarUsuario(3L);

            verify(repository).eliminar(u);
        }

        @Test
        void unUsuarioInexistenteFalla() {
            assertEquals("Usuario no encontrado: 9",
                    assertThrows(ValidacionException.class, () -> servicio.darDeBaja(9L)).getMessage());
            assertThrows(ValidacionException.class, () -> servicio.eliminarUsuario(9L));
            assertThrows(ValidacionException.class, () -> servicio.obtenerUsuario(9L));
        }
    }

    @Nested
    class ReactivacionYRestablecimiento {

        @Test
        void reactivaConUnaContrasenaNueva() {
            Usuario u = usuario(4L, "ana.op", Rol.OPERADOR, false);
            u.setHashClave(Claves.hashear("vieja123"));
            when(repository.buscarPorId(4L)).thenReturn(u);

            servicio.reactivarUsuario(4L, "nueva123");

            assertTrue(u.isActivo());
            assertTrue(Claves.coincide("nueva123", u.getHashClave()));
            assertFalse(Claves.coincide("vieja123", u.getHashClave()));
            verify(repository).actualizar(u);
        }

        @Test
        void noReactivaUnaCuentaActiva() {
            when(repository.buscarPorId(4L)).thenReturn(usuario(4L, "ana.op", Rol.OPERADOR, true));

            assertEquals("El usuario ana.op ya está activo",
                    assertThrows(ValidacionException.class, () -> servicio.reactivarUsuario(4L, "nueva123")).getMessage());
        }

        @Test
        void noReactivaSiElServidorYaTieneOtraCuentaConEseNombre() {
            when(repository.buscarPorId(4L)).thenReturn(usuario(4L, "viejo", Rol.OPERADOR, false));

            assertEquals("Ya existe en el servidor otra cuenta llamada viejo",
                    assertThrows(ValidacionException.class, () -> servicio.reactivarUsuario(4L, "nueva123")).getMessage());
        }

        @Test
        void alReactivarRevalidaQueElComercioSigaActivo() {
            Usuario u = usuario(4L, "tienda1", Rol.COMERCIO, false);
            u.setIdComercio(7L);
            when(repository.buscarPorId(4L)).thenReturn(u);

            assertEquals("El comercio elegido no existe o está dado de baja",
                    assertThrows(ValidacionException.class, () -> servicio.reactivarUsuario(4L, "nueva123")).getMessage());
            assertFalse(u.isActivo());
        }

        @Test
        void alReactivarValidaLaContrasenaNueva() {
            when(repository.buscarPorId(4L)).thenReturn(usuario(4L, "ana.op", Rol.OPERADOR, false));

            assertEquals("La contraseña debe tener al menos 6 caracteres",
                    assertThrows(ValidacionException.class, () -> servicio.reactivarUsuario(4L, "123")).getMessage());
        }

        @Test
        void restableceLaContrasenaDeUnaCuentaActivaYLaSacaDelRealmDeArchivos() throws IOException {
            Usuario u = usuario(5L, "otro", Rol.ADMINISTRADOR, true);
            when(repository.buscarPorId(5L)).thenReturn(u);

            servicio.restablecerClave(5L, "nueva123");

            assertTrue(Claves.coincide("nueva123", u.getHashClave()));
            verify(repository).actualizar(u);
            assertFalse(Files.readString(usuariosRealm).contains("otro="));
            assertFalse(Files.readString(rolesRealm).contains("otro="));
        }

        @Test
        void noRestableceLaContrasenaDeUnaCuentaDadaDeBaja() {
            when(repository.buscarPorId(5L)).thenReturn(usuario(5L, "otro", Rol.OPERADOR, false));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.restablecerClave(5L, "nueva123"));
            assertTrue(e.getMessage().contains("está dado de baja"), e.getMessage());
            verify(repository, never()).actualizar(any());
        }
    }

    @Nested
    class Consultas {

        @Test
        void listaLasCuentasSinExponerElHash() {
            Usuario conHash = usuario(1L, "ana.op", Rol.OPERADOR, true);
            conHash.setHashClave(Claves.hashear("secreta1"));
            when(repository.listarTodos()).thenReturn(List.of(conHash, usuario(2L, "beto", Rol.COMERCIO, false)));

            List<UsuarioDTO> todos = servicio.listarTodos();

            assertEquals(2, todos.size());
            assertEquals("ana.op", todos.get(0).username);
            assertEquals("OPERADOR", todos.get(0).rol);
            assertTrue(todos.get(0).claveCompartida);
            assertFalse(todos.get(1).claveCompartida);
        }

        @Test
        void obtieneUnaCuenta() {
            when(repository.buscarPorId(1L)).thenReturn(usuario(1L, "ana.op", Rol.OPERADOR, true));

            assertEquals("ana.op", servicio.obtenerUsuario(1L).username);
        }
    }

    @Test
    void elRealmDeArchivosFallaClaroFueraDeWildFly() {
        System.clearProperty("jboss.server.config.dir");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ApplicationRealmSync.existeEnRealm("ana.op"));
        assertTrue(e.getMessage().contains("jboss.server.config.dir"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ApplicationRealmSync.bajaUsuario("a=b"));
    }
}
