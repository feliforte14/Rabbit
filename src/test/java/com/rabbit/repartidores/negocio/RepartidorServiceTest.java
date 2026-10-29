package com.rabbit.repartidores.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.repartidores.datos.RepartidorRepository;
import com.rabbit.repartidores.datos.model.CargaAsignada;
import com.rabbit.repartidores.datos.model.EstadoRepartidor;
import com.rabbit.repartidores.datos.model.Repartidor;
import com.rabbit.repartidores.dto.DatosRepartidorDTO;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.dto.UbicacionDTO;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.enterprise.event.Event;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Por defecto sin hora de corte, para que los tests no dependan de la hora en que corren. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepartidorServiceTest {

    @Mock private RepartidorRepository repository;
    @Mock private IContextoUsuario contextoUsuario;
    @Mock private Event<EliminacionDeRepartidor> eliminacionDeRepartidor;
    @Mock private Event<RepartidorEliminado> repartidorEliminado;

    @InjectMocks private RepartidorService servicio;

    @BeforeEach
    void sinHoraDeCorte() {
        System.setProperty(HoraDeCorte.PROPIEDAD, HoraDeCorte.SIN_CORTE);
    }

    @AfterEach
    void limpiar() {
        System.clearProperty(HoraDeCorte.PROPIEDAD);
    }

    private static Repartidor repartidor(Long id, String kg, Integer bultos, EstadoRepartidor estado) {
        Repartidor r = conId(new Repartidor(), id);
        r.setNombre("Moto " + id);
        r.setCapacidadKg(kg != null ? new BigDecimal(kg) : null);
        r.setCapacidadBultos(bultos);
        r.setEstado(estado);
        return r;
    }

    private static CargaAsignada carga(Long idRepartidor, Long idPedido, String kg, int bultos) {
        CargaAsignada c = conId(new CargaAsignada(), idPedido);
        c.setIdRepartidor(idRepartidor);
        c.setIdPedido(idPedido);
        c.setPesoKg(kg != null ? new BigDecimal(kg) : null);
        c.setBultos(bultos);
        return c;
    }

    @Nested
    class Asignacion {

        @Test
        void asignaAlPrimeroQueTieneLugarYRegistraLaCarga() {
            Repartidor lleno = repartidor(1L, "10", 2, EstadoRepartidor.OCUPADO);
            Repartidor libre = repartidor(2L, "100", 10, EstadoRepartidor.DISPONIBLE);
            when(repository.bloquearCandidatos(5L)).thenReturn(List.of(lleno, libre));
            when(repository.listarCargas()).thenReturn(List.of(carga(1L, 90L, "9", 1)));

            Long id = servicio.asignarRepartidor(100L, 5L, new BigDecimal("3"), 1, null);

            assertEquals(2L, id);
            ArgumentCaptor<CargaAsignada> c = ArgumentCaptor.forClass(CargaAsignada.class);
            verify(repository).guardarCarga(c.capture());
            assertEquals(2L, c.getValue().getIdRepartidor());
            assertEquals(100L, c.getValue().getIdPedido());
            assertEquals(new BigDecimal("3"), c.getValue().getPesoKg());
            assertEquals(1, c.getValue().getBultos());
            assertNotNull(c.getValue().getFechaAsignacion());
            assertEquals(EstadoRepartidor.OCUPADO, libre.getEstado());
            assertEquals(100L, libre.getIdPedidoActual());
            verify(repository).actualizar(libre);
        }

        @Test
        void noVuelveAlRepartidorQueLoDevolvio() {
            Repartidor excluido = repartidor(1L, "100", 10, EstadoRepartidor.DISPONIBLE);
            Repartidor otro = repartidor(2L, "100", 10, EstadoRepartidor.DISPONIBLE);
            when(repository.bloquearCandidatos(null)).thenReturn(List.of(excluido, otro));

            assertEquals(2L, servicio.asignarRepartidor(100L, null, BigDecimal.ONE, 1, 1L));
        }

        @Test
        void sinLugarLanzaSinLugarExceptionSinGuardarNada() {
            when(repository.bloquearCandidatos(5L)).thenReturn(List.of(repartidor(1L, "5", 1, EstadoRepartidor.DISPONIBLE)));

            SinLugarException e = assertThrows(SinLugarException.class,
                    () -> servicio.asignarRepartidor(100L, 5L, new BigDecimal("20"), 1, null));
            assertTrue(e.getMessage().startsWith("Ningún repartidor tiene lugar para "), e.getMessage());
            verify(repository, never()).guardarCarga(any());
        }

        @Test
        void sinRepartidoresCargadosLoDice() {
            SinLugarException e = assertThrows(SinLugarException.class,
                    () -> servicio.asignarRepartidor(100L, 5L, null, 1, null));
            assertEquals("No hay repartidores cargados", e.getMessage());
        }

        @Test
        void despuesDeLaHoraDeCorteNoAsigna() {
            System.setProperty(HoraDeCorte.PROPIEDAD, "00:00");

            SinLugarException e = assertThrows(SinLugarException.class,
                    () -> servicio.asignarRepartidor(100L, 5L, null, 1, null));
            assertEquals("Pasó la hora de corte (00:00): se asigna mañana", e.getMessage());
            verify(repository, never()).bloquearCandidatos(any());
            assertFalse(servicio.antesDelCorte());
        }

        @Test
        void sinCorteSiempreEstaAntesDelCorte() {
            assertTrue(servicio.antesDelCorte());
        }
    }

    @Nested
    class MotivoParaEsperar {

        @Test
        void conLugarNoHayMotivo() {
            when(repository.listarTodos()).thenReturn(List.of(repartidor(1L, null, null, EstadoRepartidor.DISPONIBLE)));

            assertNull(servicio.motivoParaEsperar(null, 1, null));
        }

        @Test
        void unRepartidorSinCapacidadLlevaUnPedidoALaVez() {
            when(repository.listarTodos()).thenReturn(List.of(repartidor(1L, null, null, EstadoRepartidor.OCUPADO)));
            when(repository.listarCargas()).thenReturn(List.of(carga(1L, 90L, null, 1)));

            assertEquals("Ningún repartidor tiene lugar para 1 bulto", servicio.motivoParaEsperar(null, 1, null));
        }

        @Test
        void siSoloLeEntraAlQueLoDevolvioSeEsperaAOtro() {
            when(repository.listarTodos()).thenReturn(List.of(repartidor(1L, "100", 10, EstadoRepartidor.DISPONIBLE)));

            String motivo = servicio.motivoParaEsperar(null, 2, 1L);

            assertEquals("Se espera a otro repartidor con lugar para 2 bultos: no vuelve al que lo devolvió", motivo);
        }

        @Test
        void siNoLeEntraANadieTampocoAlQueLoDevolvio() {
            when(repository.listarTodos()).thenReturn(List.of(repartidor(1L, "100", 1, EstadoRepartidor.DISPONIBLE),
                    repartidor(2L, "100", 1, EstadoRepartidor.DISPONIBLE)));

            String motivo = servicio.motivoParaEsperar(null, 5, 1L);

            assertEquals("Ningún otro repartidor tiene lugar para 5 bultos (no vuelve al que lo devolvió)", motivo);
        }

        @Test
        void despuesDelCorteElMotivoEsElCorte() {
            System.setProperty(HoraDeCorte.PROPIEDAD, "00:00");

            assertTrue(servicio.motivoParaEsperar(null, 1, null).startsWith("Pasó la hora de corte"));
        }

        @Test
        void elDiaDeEntregaDependeDelCorte() {
            System.setProperty(HoraDeCorte.PROPIEDAD, "07:00");
            LocalDateTime temprano = LocalDateTime.of(2026, 10, 20, 6, 59);
            LocalDateTime tarde = LocalDateTime.of(2026, 10, 20, 7, 0);

            assertEquals(LocalDate.of(2026, 10, 20), servicio.diaDeEntregaPara(temprano));
            assertEquals(LocalDate.of(2026, 10, 21), servicio.diaDeEntregaPara(tarde));
        }
    }

    @Nested
    class Liberacion {

        @Test
        void alTerminarSuUltimoPedidoQuedaLibre() {
            Repartidor r = repartidor(1L, "100", 10, EstadoRepartidor.OCUPADO);
            r.setIdPedidoActual(100L);
            when(repository.buscarParaActualizar(1L)).thenReturn(r);
            when(repository.borrarCargaDePedido(100L)).thenReturn(true);

            servicio.liberarRepartidor(1L, 100L);

            assertEquals(EstadoRepartidor.DISPONIBLE, r.getEstado());
            assertNull(r.getIdPedidoActual());
            verify(repository).actualizar(r);
        }

        @Test
        void siLeQuedanPedidosSigueOcupadoConElUltimo() {
            Repartidor r = repartidor(1L, "100", 10, EstadoRepartidor.OCUPADO);
            r.setIdPedidoActual(100L);
            when(repository.buscarParaActualizar(1L)).thenReturn(r);
            when(repository.borrarCargaDePedido(100L)).thenReturn(true);
            when(repository.listarCargasDe(1L)).thenReturn(List.of(carga(1L, 101L, "1", 1), carga(1L, 102L, "1", 1)));

            servicio.liberarRepartidor(1L, 100L);

            assertEquals(EstadoRepartidor.OCUPADO, r.getEstado());
            assertEquals(102L, r.getIdPedidoActual());
        }

        @Test
        void unPedidoAsignadoAntesDeLasCargasTambienLoLibera() {
            Repartidor r = repartidor(1L, null, null, EstadoRepartidor.OCUPADO);
            r.setIdPedidoActual(100L);
            when(repository.buscarParaActualizar(1L)).thenReturn(r);

            servicio.liberarRepartidor(1L, 100L);

            assertEquals(EstadoRepartidor.DISPONIBLE, r.getEstado());
        }

        @Test
        void siNoLlevabaEsePedidoNoCambiaNada() {
            Repartidor r = repartidor(1L, null, null, EstadoRepartidor.OCUPADO);
            r.setIdPedidoActual(99L);
            when(repository.buscarParaActualizar(1L)).thenReturn(r);

            servicio.liberarRepartidor(1L, 100L);
            servicio.liberarRepartidor(null, 100L);
            servicio.liberarRepartidor(2L, 100L);

            assertEquals(EstadoRepartidor.OCUPADO, r.getEstado());
            verify(repository, never()).actualizar(any());
        }
    }

    @Nested
    class AltaYCapacidad {

        private DatosRepartidorDTO datos(String nombre, String kg, Integer bultos) {
            DatosRepartidorDTO d = new DatosRepartidorDTO();
            d.nombre = nombre;
            d.telefono = "1144445555";
            d.idZona = 3L;
            d.capacidadKg = kg != null ? new BigDecimal(kg) : null;
            d.capacidadBultos = bultos;
            return d;
        }

        @Test
        void registraUnRepartidorDisponible() {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 8L));

            assertEquals(8L, servicio.registrarRepartidor(datos("  Juan  ", "150.5", 20)));

            ArgumentCaptor<Repartidor> r = ArgumentCaptor.forClass(Repartidor.class);
            verify(repository).guardar(r.capture());
            assertEquals("Juan", r.getValue().getNombre());
            assertEquals(3L, r.getValue().getIdZona());
            assertEquals(new BigDecimal("150.5"), r.getValue().getCapacidadKg());
            assertEquals(20, r.getValue().getCapacidadBultos());
            assertEquals(EstadoRepartidor.DISPONIBLE, r.getValue().getEstado());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = " ")
        void exigeNombre(String nombre) {
            assertEquals("El nombre del repartidor es obligatorio",
                    assertThrows(ValidacionException.class, () -> servicio.registrarRepartidor(datos(nombre, null, null))).getMessage());
        }

        @ParameterizedTest
        @CsvSource({"0", "-1", "10000.01"})
        void rechazaCapacidadEnKilosFueraDeRango(String kg) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarRepartidor(datos("Juan", kg, null)));
            assertEquals("La capacidad en kilos tiene que ser mayor a 0 y de hasta 10000 kg", e.getMessage());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -3, 1001})
        void rechazaCapacidadEnBultosFueraDeRango(int bultos) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarRepartidor(datos("Juan", null, bultos)));
            assertEquals("La capacidad en bultos tiene que ser de 1 a 1000", e.getMessage());
        }

        @Test
        void aceptaLosTopesExactos() {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 1L));

            assertEquals(1L, servicio.registrarRepartidor(datos("Juan", "10000", 1000)));
            assertEquals(1L, servicio.registrarRepartidor(datos("Ana", "0.01", 1)));
        }

        @Test
        void actualizaLaCapacidad() {
            Repartidor r = repartidor(1L, "10", 1, EstadoRepartidor.DISPONIBLE);
            when(repository.buscarPorId(1L)).thenReturn(r);

            servicio.actualizarCapacidad(1L, new BigDecimal("50"), 5);

            assertEquals(new BigDecimal("50"), r.getCapacidadKg());
            assertEquals(5, r.getCapacidadBultos());
            verify(repository).actualizar(r);
        }

        @Test
        void noActualizaSiElRepartidorNoExisteOLaCapacidadEsInvalida() {
            assertEquals("Repartidor no encontrado: 9",
                    assertThrows(ValidacionException.class, () -> servicio.actualizarCapacidad(9L, null, null)).getMessage());
            assertThrows(ValidacionException.class, () -> servicio.actualizarCapacidad(null, null, null));
            when(repository.buscarPorId(1L)).thenReturn(repartidor(1L, "10", 1, EstadoRepartidor.DISPONIBLE));
            assertThrows(ValidacionException.class, () -> servicio.actualizarCapacidad(1L, BigDecimal.ZERO, 1));
            verify(repository, never()).actualizar(any());
        }

        @Test
        void asignaZona() {
            Repartidor r = repartidor(1L, null, null, EstadoRepartidor.DISPONIBLE);
            when(repository.buscarPorId(1L)).thenReturn(r);

            servicio.asignarZona(1L, 7L);

            assertEquals(7L, r.getIdZona());
            verify(repository).actualizar(r);
            assertThrows(ValidacionException.class, () -> servicio.asignarZona(2L, 7L));
            assertThrows(ValidacionException.class, () -> servicio.asignarZona(null, 7L));
        }

        @Test
        void listaConLaCargaDeCadaUno() {
            when(repository.listarTodos()).thenReturn(List.of(repartidor(1L, "100", 10, EstadoRepartidor.OCUPADO),
                    repartidor(2L, "100", 10, EstadoRepartidor.DISPONIBLE)));
            when(repository.listarCargas()).thenReturn(List.of(carga(1L, 100L, "4.5", 2), carga(1L, 101L, "1", 1)));

            List<RepartidorDTO> todos = servicio.listarTodos();

            assertEquals(new BigDecimal("5.5"), todos.get(0).cargaKg);
            assertEquals(3, todos.get(0).cargaBultos);
            assertEquals(2, todos.get(0).getPedidosEnCurso());
            assertEquals(0, todos.get(1).getPedidosEnCurso());
        }

        @Test
        void obtieneUnoConSusCargasONull() {
            when(repository.buscarPorId(1L)).thenReturn(repartidor(1L, "100", 10, EstadoRepartidor.OCUPADO));
            when(repository.listarCargasDe(1L)).thenReturn(List.of(carga(1L, 100L, "2", 1)));

            assertEquals(1, servicio.obtenerRepartidor(1L).getPedidosEnCurso());
            assertNull(servicio.obtenerRepartidor(2L));
            assertNull(servicio.obtenerRepartidor(null));
        }
    }

    @Nested
    class Ubicacion {

        @Test
        void elRepartidorConEntregasCompartirSuUbicacion() {
            Repartidor r = repartidor(1L, null, null, EstadoRepartidor.OCUPADO);
            when(contextoUsuario.idRepartidorActual()).thenReturn(1L);
            when(repository.buscarPorId(1L)).thenReturn(r);

            servicio.registrarUbicacion(-34.6, -58.38);

            assertEquals(-34.6, r.getLatitud());
            assertEquals(-58.38, r.getLongitud());
            assertNotNull(r.getFechaUbicacion());
            verify(repository).actualizar(r);
        }

        @Test
        void sinEntregasEnCursoNoSeComparte() {
            when(contextoUsuario.idRepartidorActual()).thenReturn(1L);
            when(repository.buscarPorId(1L)).thenReturn(repartidor(1L, null, null, EstadoRepartidor.DISPONIBLE));

            assertEquals("La ubicación solo se comparte mientras tenés entregas en curso",
                    assertThrows(ValidacionException.class, () -> servicio.registrarUbicacion(-34.6, -58.38)).getMessage());
        }

        @ParameterizedTest
        @CsvSource({"91,0", "-91,0", "0,181", "0,-181", "NaN,0", "0,Infinity"})
        void rechazaCoordenadasImposibles(double lat, double lon) {
            assertEquals("Ubicación inválida",
                    assertThrows(ValidacionException.class, () -> servicio.registrarUbicacion(lat, lon)).getMessage());
            verify(contextoUsuario, never()).idRepartidorActual();
        }

        @Test
        void aceptaLosBordesDelMapa() {
            Repartidor r = repartidor(1L, null, null, EstadoRepartidor.OCUPADO);
            when(contextoUsuario.idRepartidorActual()).thenReturn(1L);
            when(repository.buscarPorId(1L)).thenReturn(r);

            servicio.registrarUbicacion(90, -180);

            assertEquals(90.0, r.getLatitud());
        }

        private Repartidor conUbicacion(Long id, EstadoRepartidor estado, int minutosAtras) {
            Repartidor r = repartidor(id, null, null, estado);
            r.setLatitud(-34.6);
            r.setLongitud(-58.4);
            r.setFechaUbicacion(LocalDateTime.now().minusMinutes(minutosAtras));
            return r;
        }

        @Test
        void elMapaMuestraSoloLosOcupadosConUbicacionDeLosUltimos15Minutos() {
            Repartidor reciente = conUbicacion(1L, EstadoRepartidor.OCUPADO, 5);
            Repartidor vieja = conUbicacion(2L, EstadoRepartidor.OCUPADO, 20);
            Repartidor libre = conUbicacion(3L, EstadoRepartidor.DISPONIBLE, 1);
            when(repository.listarTodos()).thenReturn(List.of(reciente, vieja, libre));
            when(repository.listarCargas()).thenReturn(List.of(carga(1L, 100L, "1", 1)));

            List<UbicacionDTO> mapa = servicio.listarUbicacionesRecientes();

            assertEquals(1, mapa.size());
            assertEquals(1L, mapa.get(0).getIdRepartidor());
            assertEquals(1, mapa.get(0).getPedidosEnCurso());
            assertEquals(-34.6, mapa.get(0).getLatitud());
        }

        @Test
        void elSeguimientoMuestraLaUbicacionSoloSiLlevaEsePedido() {
            Repartidor r = conUbicacion(1L, EstadoRepartidor.OCUPADO, 2);
            r.setIdPedidoActual(200L);
            when(repository.buscarPorId(1L)).thenReturn(r);
            when(repository.listarCargasDe(1L)).thenReturn(List.of(carga(1L, 100L, "1", 1)));

            assertNotNull(servicio.ubicacionParaPedido(1L, 100L));
            assertNotNull(servicio.ubicacionParaPedido(1L, 200L));
            assertNull(servicio.ubicacionParaPedido(1L, 300L));
            assertNull(servicio.ubicacionParaPedido(null, 100L));
        }

        @Test
        void unaUbicacionViejaNoSeMuestraEnElSeguimiento() {
            when(repository.buscarPorId(1L)).thenReturn(conUbicacion(1L, EstadoRepartidor.OCUPADO, 16));

            assertNull(servicio.ubicacionParaPedido(1L, 100L));
        }

        @Test
        void ultimaUbicacionAunqueSeaVieja() {
            when(repository.buscarPorId(1L)).thenReturn(conUbicacion(1L, EstadoRepartidor.DISPONIBLE, 600));
            when(repository.buscarPorId(2L)).thenReturn(repartidor(2L, null, null, EstadoRepartidor.DISPONIBLE));

            assertEquals(1L, servicio.ultimaUbicacion(1L).getIdRepartidor());
            assertNull(servicio.ultimaUbicacion(2L));
            assertNull(servicio.ultimaUbicacion(null));
        }
    }

    @Nested
    class Eliminacion {

        @Test
        void eliminaUnRepartidorLibreSinImpedimentos() {
            Repartidor r = repartidor(1L, null, null, EstadoRepartidor.DISPONIBLE);
            when(repository.buscarParaActualizar(1L)).thenReturn(r);

            servicio.eliminarRepartidor(1L);

            verify(eliminacionDeRepartidor).fire(any());
            verify(repartidorEliminado).fire(any());
            verify(repository).eliminar(r);
        }

        @Test
        void noEliminaConEntregasEnCurso() {
            when(repository.buscarParaActualizar(1L)).thenReturn(repartidor(1L, null, null, EstadoRepartidor.OCUPADO));
            when(repository.buscarParaActualizar(2L)).thenReturn(repartidor(2L, null, null, EstadoRepartidor.DISPONIBLE));
            when(repository.listarCargasDe(2L)).thenReturn(List.of(carga(2L, 100L, "1", 1)));

            assertEquals("No se puede eliminar a Moto 1: tiene entregas en curso. Esperá a que las termine.",
                    assertThrows(ValidacionException.class, () -> servicio.eliminarRepartidor(1L)).getMessage());
            assertThrows(ValidacionException.class, () -> servicio.eliminarRepartidor(2L));
            verify(eliminacionDeRepartidor, never()).fire(any());
        }

        @Test
        void noEliminaSiOtroComponenteLoImpide() {
            when(repository.buscarParaActualizar(1L)).thenReturn(repartidor(1L, null, null, EstadoRepartidor.DISPONIBLE));
            doAnswer(inv -> {
                ((EliminacionDeRepartidor) inv.getArgument(0)).impedir("una cuenta de usuario");
                return null;
            }).when(eliminacionDeRepartidor).fire(any());

            assertEquals("No se puede eliminar a Moto 1: tiene una cuenta de usuario.",
                    assertThrows(ValidacionException.class, () -> servicio.eliminarRepartidor(1L)).getMessage());
            verify(repository, never()).eliminar(any());
            verify(repartidorEliminado, never()).fire(any());
        }

        @Test
        void unRepartidorInexistenteFalla() {
            assertEquals("Repartidor no encontrado: 9",
                    assertThrows(ValidacionException.class, () -> servicio.eliminarRepartidor(9L)).getMessage());
            assertThrows(ValidacionException.class, () -> servicio.eliminarRepartidor(null));
        }
    }

    @Test
    void alEliminarUnaZonaSusRepartidoresQuedanSinZonaFija() {
        ZonaDeRepartidores observador = new ZonaDeRepartidores();
        List<Long> zonasQuitadas = new ArrayList<>();
        try {
            var campo = ZonaDeRepartidores.class.getDeclaredField("repository");
            campo.setAccessible(true);
            campo.set(observador, repository);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(repository.quitarZona(5L)).thenAnswer(inv -> {
            zonasQuitadas.add(inv.getArgument(0));
            return 2;
        });

        observador.alEliminarZona(new com.rabbit.ruteo.negocio.ZonaEliminada(5L));

        assertEquals(List.of(5L), zonasQuitadas);
    }
}
