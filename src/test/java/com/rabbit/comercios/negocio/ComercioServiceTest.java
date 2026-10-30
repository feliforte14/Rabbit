package com.rabbit.comercios.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.datos.ComercioRepository;
import com.rabbit.comercios.datos.model.Comercio;
import com.rabbit.comercios.datos.model.PuntoPicking;
import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.DatosComercioDTO;
import com.rabbit.comercios.dto.DatosFiscalesDTO;
import com.rabbit.comercios.dto.DatosPuntoPickingDTO;
import com.rabbit.comercios.dto.PuntoPickingDTO;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.ejb.SessionContext;
import jakarta.enterprise.event.Event;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComercioServiceTest {

    @Mock private ComercioRepository repository;
    @Mock private IContextoUsuario contextoUsuario;
    @Mock private SessionContext contexto;
    @Mock private Event<EliminacionDeComercio> eliminacionDeComercio;
    @Mock private Event<ComercioEliminado> comercioEliminado;

    @InjectMocks private ComercioService servicio;

    private Comercio comercio(Long id, boolean activo) {
        Comercio c = conId(new Comercio(), id);
        c.setNombre("Tienda " + id);
        c.setRazonSocial("Tienda " + id + " SRL");
        c.setCuit("30-1234567" + id + "-1");
        c.setActivo(activo);
        return c;
    }

    private PuntoPicking punto(Long id, Comercio dueno, boolean activa) {
        PuntoPicking p = conId(new PuntoPicking(), id);
        p.setNombre("Depósito " + id);
        p.setDireccion("Calle " + id);
        p.setComercio(dueno);
        p.setActiva(activa);
        return p;
    }

    private static DatosComercioDTO datosValidos() {
        DatosComercioDTO d = new DatosComercioDTO();
        d.nombre = "  Almacén Don Pepe  ";
        d.razonSocial = "  Don Pepe SRL ";
        d.cuit = " 30-12345678-9 ";
        d.email = "ventas@donpepe.com";
        d.telefono = "1144445555";
        return d;
    }

    @Nested
    class AltaDeComercio {

        @Test
        void guardaElComercioActivoConLosDatosRecortados() {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 7L));

            Long id = servicio.registrarComercio(datosValidos());

            assertEquals(7L, id);
            ArgumentCaptor<Comercio> guardado = ArgumentCaptor.forClass(Comercio.class);
            verify(repository).guardar(guardado.capture());
            Comercio c = guardado.getValue();
            assertEquals("Almacén Don Pepe", c.getNombre());
            assertEquals("Don Pepe SRL", c.getRazonSocial());
            assertEquals("30-12345678-9", c.getCuit());
            assertEquals("ventas@donpepe.com", c.getEmail());
            assertEquals("1144445555", c.getTelefono());
            assertTrue(c.isActivo());
        }

        @Test
        void aceptaElCuitSinGuionesYSinEmail() {
            DatosComercioDTO d = datosValidos();
            d.cuit = "30123456789";
            d.email = null;
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 1L));

            assertEquals(1L, servicio.registrarComercio(d));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rechazaSinNombre(String nombre) {
            DatosComercioDTO d = datosValidos();
            d.nombre = nombre;

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.registrarComercio(d));
            assertEquals("El nombre del comercio es obligatorio", e.getMessage());
            verify(repository, never()).guardar(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rechazaSinRazonSocial(String razonSocial) {
            DatosComercioDTO d = datosValidos();
            d.razonSocial = razonSocial;

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.registrarComercio(d));
            assertEquals("La razón social es obligatoria", e.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void rechazaSinCuit(String cuit) {
            DatosComercioDTO d = datosValidos();
            d.cuit = cuit;

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.registrarComercio(d));
            assertEquals("El CUIT es obligatorio", e.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"30-1234567-9", "3012345678", "301234567890", "ab-12345678-9", "30_12345678_9"})
        void rechazaUnCuitConFormatoInvalido(String cuit) {
            DatosComercioDTO d = datosValidos();
            d.cuit = cuit;

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.registrarComercio(d));
            assertTrue(e.getMessage().startsWith("El CUIT debe tener el formato"), e.getMessage());
            verify(repository, never()).existeCuit(anyString(), any());
        }

        @Test
        void rechazaUnCuitRepetido() {
            when(repository.existeCuit("30-12345678-9", null)).thenReturn(true);

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarComercio(datosValidos()));
            assertTrue(e.getMessage().startsWith("Ya existe un comercio registrado con el CUIT"), e.getMessage());
            verify(repository, never()).guardar(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"sin-arroba", "dos@@arrobas.com", "sin@dominio", "con espacio@x.com"})
        void rechazaUnEmailConFormatoInvalido(String email) {
            DatosComercioDTO d = datosValidos();
            d.email = email;

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.registrarComercio(d));
            assertEquals("El email tiene un formato inválido", e.getMessage());
        }

        @Test
        void unEmailEnBlancoEsOpcional() {
            DatosComercioDTO d = datosValidos();
            d.email = "   ";
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 2L));

            assertEquals(2L, servicio.registrarComercio(d));
        }
    }

    @Nested
    class DatosFiscales {

        private DatosFiscalesDTO fiscales() {
            DatosFiscalesDTO d = new DatosFiscalesDTO();
            d.razonSocial = " Nueva Razón SA ";
            d.cuit = "33-87654321-0";
            d.email = "admin@nueva.com";
            d.telefono = "1155556666";
            return d;
        }

        @Test
        void actualizaLosDatosFiscalesSinTocarElNombre() {
            Comercio c = comercio(5L, true);
            when(repository.buscarPorId(5L)).thenReturn(c);

            servicio.actualizarDatosFiscales(5L, fiscales());

            assertEquals("Tienda 5", c.getNombre());
            assertEquals("Nueva Razón SA", c.getRazonSocial());
            assertEquals("33-87654321-0", c.getCuit());
            assertEquals("admin@nueva.com", c.getEmail());
            assertEquals("1155556666", c.getTelefono());
            verify(repository).actualizar(c);
        }

        @Test
        void excluyeAlPropioComercioDelControlDeCuitRepetido() {
            when(repository.buscarPorId(5L)).thenReturn(comercio(5L, true));

            servicio.actualizarDatosFiscales(5L, fiscales());

            verify(repository).existeCuit("33-87654321-0", 5L);
        }

        @Test
        void rechazaUnCuitQueYaUsaOtroComercio() {
            when(repository.buscarPorId(5L)).thenReturn(comercio(5L, true));
            when(repository.existeCuit("33-87654321-0", 5L)).thenReturn(true);

            assertThrows(ValidacionException.class, () -> servicio.actualizarDatosFiscales(5L, fiscales()));
            verify(repository, never()).actualizar(any());
        }

        @Test
        void rechazaUnComercioInexistente() {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.actualizarDatosFiscales(99L, fiscales()));
            assertEquals("Comercio no encontrado: 99", e.getMessage());
        }
    }

    @Nested
    class BajaYReactivacion {

        @Test
        void laBajaDelComercioArrastraASusPuntosDePickingActivos() {
            Comercio c = comercio(3L, true);
            PuntoPicking p1 = punto(31L, c, true);
            PuntoPicking p2 = punto(32L, c, true);
            when(repository.buscarPorId(3L)).thenReturn(c);
            when(repository.listarPuntosPickingActivos(3L)).thenReturn(List.of(p1, p2));

            servicio.darDeBajaComercio(3L);

            assertFalse(c.isActivo());
            assertFalse(p1.isActiva());
            assertFalse(p2.isActiva());
            verify(repository).actualizar(c);
            verify(repository).actualizarPuntoPicking(p1);
            verify(repository).actualizarPuntoPicking(p2);
        }

        @Test
        void reactivarVuelveAPonerloActivo() {
            Comercio c = comercio(3L, false);
            when(repository.buscarPorId(3L)).thenReturn(c);

            servicio.reactivarComercio(3L);

            assertTrue(c.isActivo());
            verify(repository).actualizar(c);
        }

        @Test
        void bajaYReactivacionDeUnComercioInexistenteFallan() {
            assertThrows(ValidacionException.class, () -> servicio.darDeBajaComercio(8L));
            assertThrows(ValidacionException.class, () -> servicio.reactivarComercio(8L));
        }
    }

    @Nested
    class PuntosDePicking {

        private DatosPuntoPickingDTO datos(String nombre, String direccion) {
            DatosPuntoPickingDTO d = new DatosPuntoPickingDTO();
            d.nombre = nombre;
            d.direccion = direccion;
            return d;
        }

        @Test
        void elPersonalRegistraUnPuntoEnCualquierComercioActivo() {
            Comercio c = comercio(4L, true);
            when(repository.buscarPorId(4L)).thenReturn(c);
            when(repository.guardarPuntoPicking(any())).thenAnswer(inv -> conId(inv.getArgument(0), 40L));

            Long id = servicio.registrarPuntoPicking(4L, datos(" Sucursal Centro ", " Florida 100 "));

            assertEquals(40L, id);
            ArgumentCaptor<PuntoPicking> guardado = ArgumentCaptor.forClass(PuntoPicking.class);
            verify(repository).guardarPuntoPicking(guardado.capture());
            assertEquals("Sucursal Centro", guardado.getValue().getNombre());
            assertEquals("Florida 100", guardado.getValue().getDireccion());
            assertTrue(guardado.getValue().isActiva());
            assertEquals(c, guardado.getValue().getComercio());
        }

        @Test
        void unComercioSoloRegistraPuntosEnElSuyo() {
            when(contexto.isCallerInRole("COMERCIO")).thenReturn(true);
            when(contextoUsuario.idComercioActual()).thenReturn(4L);

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarPuntoPicking(9L, datos("X", "Y")));
            assertEquals("Solo podés gestionar los puntos de picking de tu comercio", e.getMessage());
            verify(repository, never()).guardarPuntoPicking(any());
        }

        @Test
        void unComercioRegistraPuntosEnElSuyo() {
            when(contexto.isCallerInRole("COMERCIO")).thenReturn(true);
            when(contextoUsuario.idComercioActual()).thenReturn(4L);
            when(repository.buscarPorId(4L)).thenReturn(comercio(4L, true));
            when(repository.guardarPuntoPicking(any())).thenAnswer(inv -> conId(inv.getArgument(0), 41L));

            assertEquals(41L, servicio.registrarPuntoPicking(4L, datos("X", "Y")));
        }

        @Test
        void noSeAgreganPuntosAUnComercioDadoDeBaja() {
            when(repository.buscarPorId(4L)).thenReturn(comercio(4L, false));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarPuntoPicking(4L, datos("X", "Y")));
            assertEquals("No se pueden agregar puntos de picking a un comercio dado de baja", e.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = " ")
        void elPuntoNecesitaNombre(String nombre) {
            when(repository.buscarPorId(4L)).thenReturn(comercio(4L, true));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarPuntoPicking(4L, datos(nombre, "Y")));
            assertEquals("El nombre del punto de picking es obligatorio", e.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = " ")
        void elPuntoNecesitaDireccion(String direccion) {
            when(repository.buscarPorId(4L)).thenReturn(comercio(4L, true));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarPuntoPicking(4L, datos("X", direccion)));
            assertEquals("La dirección del punto de picking es obligatoria", e.getMessage());
        }

        @Test
        void bajaDeUnPunto() {
            PuntoPicking p = punto(40L, comercio(4L, true), true);
            when(repository.buscarPuntoPickingPorId(40L)).thenReturn(p);

            servicio.darDeBajaPuntoPicking(40L);

            assertFalse(p.isActiva());
            verify(repository).actualizarPuntoPicking(p);
        }

        @Test
        void unComercioNoDaDeBajaPuntosAjenos() {
            when(contexto.isCallerInRole("COMERCIO")).thenReturn(true);
            when(contextoUsuario.idComercioActual()).thenReturn(4L);
            when(repository.buscarPuntoPickingPorId(50L)).thenReturn(punto(50L, comercio(5L, true), true));

            assertThrows(ValidacionException.class, () -> servicio.darDeBajaPuntoPicking(50L));
            verify(repository, never()).actualizarPuntoPicking(any());
        }

        @Test
        void unPuntoInexistenteFalla() {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.darDeBajaPuntoPicking(77L));
            assertEquals("Punto de picking no encontrado: 77", e.getMessage());
            assertThrows(ValidacionException.class, () -> servicio.reactivarPuntoPicking(77L));
        }

        @Test
        void reactivaUnPuntoSiElComercioEstaActivo() {
            PuntoPicking p = punto(40L, comercio(4L, true), false);
            when(repository.buscarPuntoPickingPorId(40L)).thenReturn(p);

            servicio.reactivarPuntoPicking(40L);

            assertTrue(p.isActiva());
            verify(repository).actualizarPuntoPicking(p);
        }

        @Test
        void noReactivaUnPuntoSiElComercioEstaDeBaja() {
            PuntoPicking p = punto(40L, comercio(4L, false), false);
            when(repository.buscarPuntoPickingPorId(40L)).thenReturn(p);

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.reactivarPuntoPicking(40L));
            assertTrue(e.getMessage().startsWith("No se puede reactivar el punto de picking"), e.getMessage());
            assertFalse(p.isActiva());
        }

        @Test
        void listaTodosLosPuntosDelComercioComoDTO() {
            Comercio c = comercio(4L, true);
            when(repository.buscarPorId(4L)).thenReturn(c);
            when(repository.listarPuntosPickingDeComercio(4L))
                    .thenReturn(List.of(punto(40L, c, true), punto(41L, c, false)));

            List<PuntoPickingDTO> puntos = servicio.listarPuntosPickingDeComercio(4L);

            assertEquals(2, puntos.size());
            assertEquals(40L, puntos.get(0).getId());
            assertTrue(puntos.get(0).isActiva());
            assertFalse(puntos.get(1).isActiva());
        }

        @Test
        void listarLosPuntosDeUnComercioInexistenteFalla() {
            assertThrows(ValidacionException.class, () -> servicio.listarPuntosPickingDeComercio(99L));
        }

        @Test
        void listarPuntosActivosDevuelveSoloLosActivos() {
            Comercio c = comercio(4L, true);
            when(repository.listarPuntosPickingActivos(4L)).thenReturn(List.of(punto(40L, c, true)));

            List<PuntoPickingDTO> puntos = servicio.listarPuntosPicking(4L);

            assertEquals(1, puntos.size());
            assertEquals("Depósito 40", puntos.get(0).getNombre());
        }
    }

    @Nested
    class Consultas {

        @Test
        void elPersonalNoTieneRestriccionDeComercioPropio() {
            when(contexto.isCallerInRole("COMERCIO")).thenReturn(false);
            when(repository.buscarPorId(eq(9L))).thenReturn(comercio(9L, true));
            when(repository.listarPuntosPickingDeComercio(anyLong())).thenReturn(List.of());

            assertTrue(servicio.listarPuntosPickingDeComercio(9L).isEmpty());
            verify(contextoUsuario, never()).idComercioActual();
            verify(repository, never()).existeCuit(anyString(), isNull());
        }

        @Test
        void obtieneElComercioComoDTO() {
            when(repository.buscarPorId(2L)).thenReturn(comercio(2L, true));

            ComercioDTO dto = servicio.obtenerComercio(2L);

            assertEquals(2L, dto.id);
            assertEquals("Tienda 2", dto.nombre);
        }

        @Test
        void obtenerUnComercioInexistenteFalla() {
            assertThrows(ValidacionException.class, () -> servicio.obtenerComercio(2L));
        }

        @Test
        void listaTodosLosComercios() {
            when(repository.listarTodos()).thenReturn(List.of(comercio(1L, true), comercio(2L, false)));

            List<ComercioDTO> todos = servicio.listarTodos();

            assertEquals(2, todos.size());
            assertEquals(1L, todos.get(0).id);
            assertEquals(2L, todos.get(1).id);
        }

        @Test
        void unComercioPuedeOperarSoloSiExisteYEstaActivo() {
            when(repository.buscarPorId(1L)).thenReturn(comercio(1L, true));
            when(repository.buscarPorId(2L)).thenReturn(comercio(2L, false));

            assertTrue(servicio.validarComercioActivo(1L));
            assertFalse(servicio.validarComercioActivo(2L));
            assertFalse(servicio.validarComercioActivo(3L));
        }
    }

    @Nested
    class Eliminacion {

        @Test
        void eliminaUnComercioDadoDeBajaSinImpedimentos() {
            Comercio c = comercio(6L, false);
            when(repository.buscarPorId(6L)).thenReturn(c);

            servicio.eliminarComercio(6L);

            ArgumentCaptor<EliminacionDeComercio> consulta = ArgumentCaptor.forClass(EliminacionDeComercio.class);
            verify(eliminacionDeComercio).fire(consulta.capture());
            assertEquals(6L, consulta.getValue().getIdComercio());
            ArgumentCaptor<ComercioEliminado> aviso = ArgumentCaptor.forClass(ComercioEliminado.class);
            verify(comercioEliminado).fire(aviso.capture());
            assertEquals(6L, aviso.getValue().getIdComercio());
            verify(repository).eliminar(c);
        }

        @Test
        void noEliminaUnComercioActivo() {
            when(repository.buscarPorId(6L)).thenReturn(comercio(6L, true));

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.eliminarComercio(6L));
            assertEquals("No se puede eliminar un comercio activo. Debe darse de baja primero.", e.getMessage());
            verify(eliminacionDeComercio, never()).fire(any());
            verify(repository, never()).eliminar(any());
        }

        @Test
        void noEliminaSiOtroComponenteLoImpide() {
            when(repository.buscarPorId(6L)).thenReturn(comercio(6L, false));
            doAnswer(inv -> {
                EliminacionDeComercio evento = inv.getArgument(0);
                evento.impedir("2 pedidos en curso");
                evento.impedir("stock consignado");
                return null;
            }).when(eliminacionDeComercio).fire(any());

            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.eliminarComercio(6L));
            assertEquals("No se puede eliminar el comercio: tiene 2 pedidos en curso, stock consignado."
                    + " Queda dado de baja, que alcanza para que no opere.", e.getMessage());
            verify(comercioEliminado, never()).fire(any());
            verify(repository, never()).eliminar(any());
        }

        @Test
        void eliminarUnComercioInexistenteFalla() {
            assertThrows(ValidacionException.class, () -> servicio.eliminarComercio(6L));
            verify(eliminacionDeComercio, never()).fire(any());
        }
    }
}
