package com.rabbit.seguridad.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.negocio.EliminacionDeComercio;
import com.rabbit.repartidores.negocio.EliminacionDeRepartidor;
import com.rabbit.seguridad.datos.UsuarioRepository;
import com.rabbit.seguridad.datos.model.Rol;
import com.rabbit.seguridad.datos.model.Usuario;
import jakarta.ejb.SessionContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** "Cada uno ve lo suyo": el comercio y el repartidor salen siempre de la cuenta autenticada. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContextoYReferenciasDeUsuariosTest {

    @Mock private UsuarioRepository repository;

    private static Usuario usuario(String username, Rol rol, boolean activo, Long idComercio, Long idRepartidor) {
        Usuario u = conId(new Usuario(), 1L);
        u.setUsername(username);
        u.setRol(rol);
        u.setActivo(activo);
        u.setIdComercio(idComercio);
        u.setIdRepartidor(idRepartidor);
        return u;
    }

    @Nested
    class ContextoDelUsuario {

        @Mock private SessionContext contexto;
        @InjectMocks private ContextoUsuarioService servicio;

        @BeforeEach
        void inyectar() throws Exception {
            var campo = ContextoUsuarioService.class.getDeclaredField("repository");
            campo.setAccessible(true);
            campo.set(servicio, repository);
        }

        private void autenticadoComo(String username, String rol) {
            Principal p = () -> username;
            when(contexto.getCallerPrincipal()).thenReturn(p);
            when(contexto.isCallerInRole(rol)).thenReturn(true);
        }

        @Test
        void elComercioSaleDeLaCuentaComercio() {
            autenticadoComo("tienda1", "COMERCIO");
            when(repository.buscarPorUsername("tienda1")).thenReturn(usuario("tienda1", Rol.COMERCIO, true, 7L, null));

            assertEquals(7L, servicio.idComercioActual());
        }

        @Test
        void elComercioTambienSaleDeLaCuentaErp() {
            autenticadoComo("erp1", "ERP");
            when(repository.buscarPorUsername("erp1")).thenReturn(usuario("erp1", Rol.ERP, true, 8L, null));

            assertEquals(8L, servicio.idComercioActual());
        }

        @Test
        void sinRolDeComercioNoHayComercio() {
            Principal p = () -> "ana.op";
            when(contexto.getCallerPrincipal()).thenReturn(p);

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.idComercioActual());
            assertEquals("Tu usuario no está asociado a ningún comercio", e.getMessage());
            verify(repository, never()).buscarPorUsername("ana.op");
        }

        @Test
        void unaCuentaDadaDeBajaOSinComercioNoRepresentaANadie() {
            autenticadoComo("tienda1", "COMERCIO");
            when(repository.buscarPorUsername("tienda1")).thenReturn(usuario("tienda1", Rol.COMERCIO, false, 7L, null));
            assertThrows(ValidacionException.class, () -> servicio.idComercioActual());

            when(repository.buscarPorUsername("tienda1")).thenReturn(usuario("tienda1", Rol.COMERCIO, true, null, null));
            assertThrows(ValidacionException.class, () -> servicio.idComercioActual());
        }

        @Test
        void elRolDeLaBaseTieneQueCoincidirConElDelLogin() {
            autenticadoComo("tienda1", "COMERCIO");
            when(repository.buscarPorUsername("tienda1")).thenReturn(usuario("tienda1", Rol.OPERADOR, true, 7L, null));

            assertThrows(ValidacionException.class, () -> servicio.idComercioActual());
        }

        @Test
        void elRepartidorSaleDeLaCuentaRepartidor() {
            autenticadoComo("moto1", "REPARTIDOR");
            when(repository.buscarPorUsername("moto1")).thenReturn(usuario("moto1", Rol.REPARTIDOR, true, null, 3L));

            assertEquals(3L, servicio.idRepartidorActual());
        }

        @Test
        void sinRepartidorAsociadoFalla() {
            autenticadoComo("moto1", "REPARTIDOR");
            when(repository.buscarPorUsername("moto1")).thenReturn(usuario("moto1", Rol.REPARTIDOR, true, null, null));

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.idRepartidorActual());
            assertEquals("Tu usuario no está asociado a ningún repartidor", e.getMessage());
        }
    }

    @Nested
    class VetoAEliminaciones {

        @InjectMocks private ReferenciasAComercioEnUsuarios deComercio;
        @InjectMocks private ReferenciasARepartidorEnUsuarios deRepartidor;

        @BeforeEach
        void inyectar() throws Exception {
            for (Object o : List.of(deComercio, deRepartidor)) {
                var campo = o.getClass().getDeclaredField("repository");
                campo.setAccessible(true);
                campo.set(o, repository);
            }
        }

        @Test
        void unComercioConCuentasNoSeElimina() {
            EliminacionDeComercio evento = new EliminacionDeComercio(7L);
            when(repository.contarCuentasDeComercio(7L)).thenReturn(2L);

            deComercio.alEliminarComercio(evento);

            assertEquals(List.of("2 cuenta(s) de usuario"), evento.getImpedimentos());
        }

        @Test
        void unComercioSinCuentasNoTieneImpedimento() {
            EliminacionDeComercio evento = new EliminacionDeComercio(7L);

            deComercio.alEliminarComercio(evento);

            assertTrue(evento.getImpedimentos().isEmpty());
        }

        @Test
        void unRepartidorConCuentaNoSeElimina() {
            EliminacionDeRepartidor evento = new EliminacionDeRepartidor(3L);
            when(repository.contarCuentasDeRepartidor(3L)).thenReturn(1L);

            deRepartidor.alEliminarRepartidor(evento);

            assertEquals(1, evento.getImpedimentos().size());
            assertTrue(evento.getImpedimentos().get(0).startsWith("una cuenta de usuario"));
        }

        @Test
        void unRepartidorSinCuentaNoTieneImpedimento() {
            EliminacionDeRepartidor evento = new EliminacionDeRepartidor(3L);

            deRepartidor.alEliminarRepartidor(evento);

            assertTrue(evento.getImpedimentos().isEmpty());
        }
    }
}
