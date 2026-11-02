package com.rabbit.pagos.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.ComercioEliminado;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.integracion.banco.IBancoClient;
import com.rabbit.integracion.banco.ResultadoAutorizacion;
import com.rabbit.inventario.dto.OcupacionMensualDTO;
import com.rabbit.inventario.negocio.IOcupacionDeDeposito;
import com.rabbit.pagos.datos.CobroRepository;
import com.rabbit.pagos.datos.LiquidacionRepository;
import com.rabbit.pagos.datos.model.EstadoLiquidacion;
import com.rabbit.pagos.datos.model.Liquidacion;
import com.rabbit.pagos.dto.LiquidacionDTO;
import com.rabbit.pedidos.dto.ResumenEntregasDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.ejb.EJBException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tarifa por defecto (sin system properties): $15.000 de abono, $1.200 por envío, $80 por kilo y
 * almacenamiento de $10, $30 y $90 por unidad y por día (pequeño, mediano y grande).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiquidacionesTest {

    private static final YearMonth MES_PASADO = YearMonth.now().minusMonths(1);

    private static Liquidacion liquidacion(Long id, EstadoLiquidacion estado, String total) {
        Liquidacion l = conId(new Liquidacion(), id);
        l.setIdComercio(3L);
        l.setPeriodo(MES_PASADO.toString());
        l.setEstado(estado);
        l.setTotal(new BigDecimal(total));
        return l;
    }

    @Nested
    class CobroDeCadaLiquidacion {

        @Mock private LiquidacionRepository repository;
        @Mock private IBancoClient banco;
        @InjectMocks private CobroDeLiquidaciones cobros;

        @Test
        void creaLaLiquidacionPendienteConLosCargosDeLaTarifa() {
            doAnswer(inv -> conId(inv.getArgument(0), 70L)).when(repository).guardar(any());
            TarifaMensual tarifa = new TarifaMensual(new BigDecimal("15000"), new BigDecimal("1200"),
                    new BigDecimal("80"), new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("90"));

            Long id = cobros.crear(3L, "2026-09", 4, new BigDecimal("12.5"), tarifa, 10, 2, 1);

            assertEquals(70L, id);
            org.mockito.ArgumentCaptor<Liquidacion> captor = org.mockito.ArgumentCaptor.forClass(Liquidacion.class);
            verify(repository).guardar(captor.capture());
            Liquidacion l = captor.getValue();
            assertEquals(3L, l.getIdComercio());
            assertEquals("2026-09", l.getPeriodo());
            assertEquals(4, l.getCantidadEnvios());
            assertEquals(new BigDecimal("15000.00"), l.getAbono());
            assertEquals(new BigDecimal("4800.00"), l.getCargoEnvios());
            assertEquals(new BigDecimal("1000.00"), l.getCargoKilos());
            // 10 x 10 + 2 x 30 + 1 x 90
            assertEquals(new BigDecimal("250.00"), l.getCargoAlmacenamiento());
            assertEquals(new BigDecimal("21050.00"), l.getTotal());
            assertEquals(10, l.getUnidadesDiaPequeno());
            assertEquals(EstadoLiquidacion.PENDIENTE, l.getEstado());
            assertNotNull(l.getFechaGeneracion());
        }

        @Test
        void noDuplicaLaLiquidacionDeUnComercioYMes() {
            when(repository.existe(3L, "2026-09")).thenReturn(true);

            assertNull(cobros.crear(3L, "2026-09", 4, BigDecimal.ONE, TarifaMensual.vigente(), 0, 0, 0));
            verify(repository, never()).guardar(any());
        }

        @Test
        void siElBancoApruebaQuedaCobradaConSuCodigo() {
            Liquidacion l = liquidacion(70L, EstadoLiquidacion.PENDIENTE, "21050.00");
            l.setMotivo("Todavía no se pidió el cobro al banco");
            when(repository.buscarParaActualizar(70L)).thenReturn(l);
            when(banco.autorizarReferencia("LIQ-70", new BigDecimal("21050.00"))).thenReturn(ResultadoAutorizacion.aprobado("AUT-1"));

            assertSame(l, cobros.cobrar(70L));

            assertEquals(EstadoLiquidacion.COBRADA, l.getEstado());
            assertEquals("AUT-1", l.getCodigoAutorizacion());
            assertNotNull(l.getFechaCobro());
            assertNull(l.getMotivo());
            assertEquals(1, l.getIntentos());
        }

        @Test
        void siElBancoRechazaQuedaRechazadaConElMotivo() {
            Liquidacion l = liquidacion(70L, EstadoLiquidacion.PENDIENTE, "100");
            when(repository.buscarParaActualizar(70L)).thenReturn(l);
            when(banco.autorizarReferencia(any(), any())).thenReturn(ResultadoAutorizacion.rechazado("Cuenta cerrada"));

            cobros.cobrar(70L);

            assertEquals(EstadoLiquidacion.RECHAZADA, l.getEstado());
            assertEquals("El banco rechazó el cobro: Cuenta cerrada", l.getMotivo());
        }

        @Test
        void siElBancoNoRespondeQuedaPendienteParaElReintentoAutomatico() {
            Liquidacion l = liquidacion(70L, EstadoLiquidacion.PENDIENTE, "100");
            l.setIntentos(2);
            when(repository.buscarParaActualizar(70L)).thenReturn(l);
            when(banco.autorizarReferencia(any(), any())).thenReturn(ResultadoAutorizacion.noDisponible());

            cobros.cobrar(70L);

            assertEquals(EstadoLiquidacion.PENDIENTE, l.getEstado());
            assertEquals("El banco no respondió: se reintenta solo", l.getMotivo());
            assertEquals(3, l.getIntentos());
        }

        @Test
        void unaLiquidacionYaCobradaNoSeVuelveACobrar() {
            Liquidacion l = liquidacion(70L, EstadoLiquidacion.COBRADA, "100");
            when(repository.buscarParaActualizar(70L)).thenReturn(l);

            assertSame(l, cobros.cobrar(70L));
            verifyNoInteractions(banco);
            assertEquals(0, l.getIntentos());
        }

        @Test
        void unaLiquidacionInexistenteFalla() {
            ValidacionException e = assertThrows(ValidacionException.class, () -> cobros.cobrar(99L));
            assertEquals("Liquidación no encontrada: 99", e.getMessage());
        }
    }

    @Nested
    class LiquidacionDelMes {

        @Mock private LiquidacionRepository repository;
        @Mock private CobroDeLiquidaciones cobros;
        @Mock private ISeguimientoPedido pedidos;
        @Mock private IConsultaComercios comercios;
        @Mock private IOcupacionDeDeposito ocupacion;
        @Mock private IContextoUsuario contextoUsuario;
        @InjectMocks private LiquidacionService servicio;

        private ComercioDTO comercio(Long id, boolean activo) {
            ComercioDTO c = new ComercioDTO();
            c.id = id;
            c.nombre = "C" + id;
            c.activo = activo;
            return c;
        }

        private void dosComerciosActivosYUnoDeBaja() {
            when(comercios.listarTodos()).thenReturn(List.of(comercio(1L, true), comercio(2L, true), comercio(3L, false)));
            when(pedidos.resumenEntregas(any(), any()))
                    .thenReturn(List.of(new ResumenEntregasDTO(1L, 3, new BigDecimal("10"))));
            when(ocupacion.ocupacionDelMes(any())).thenReturn(Map.of(1L, new OcupacionMensualDTO(5, 0, 1, 2)));
        }

        @Test
        void laVistaPreviaCalculaUnaPorComercioActivoAunqueNoHayaEntregado() {
            dosComerciosActivosYUnoDeBaja();

            List<LiquidacionDTO> previa = servicio.previsualizar(MES_PASADO);

            assertEquals(2, previa.size());
            LiquidacionDTO conEntregas = previa.get(0);
            assertEquals(1L, conEntregas.idComercio);
            assertEquals(3, conEntregas.cantidadEnvios);
            assertEquals(new BigDecimal("3600.00"), conEntregas.cargoEnvios);
            assertEquals(new BigDecimal("800.00"), conEntregas.cargoKilos);
            // 5 x 10 + 1 x 90
            assertEquals(new BigDecimal("140.00"), conEntregas.cargoAlmacenamiento);
            assertEquals(2, conEntregas.productosSinMedidas);
            assertEquals(new BigDecimal("19540.00"), conEntregas.total);
            assertEquals("VISTA_PREVIA", conEntregas.estado);
            LiquidacionDTO soloAbono = previa.get(1);
            assertEquals(0, soloAbono.cantidadEnvios);
            assertEquals(BigDecimal.ZERO, soloAbono.kilos);
            assertEquals(new BigDecimal("15000.00"), soloAbono.total);
            verifyNoInteractions(cobros);
        }

        @Test
        void consultaLasEntregasDelMesCompleto() {
            dosComerciosActivosYUnoDeBaja();
            YearMonth septiembre = YearMonth.of(2026, 9);

            servicio.previsualizar(septiembre);

            verify(pedidos).resumenEntregas(LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0));
            verify(ocupacion).ocupacionDelMes(septiembre);
        }

        @Test
        void liquidarCreaYCobraCadaUnaYDevuelveLasDelPeriodo() {
            dosComerciosActivosYUnoDeBaja();
            when(cobros.crear(eq(1L), any(), anyInt(), any(), any(), anyLong(), anyLong(), anyLong())).thenReturn(11L);
            when(cobros.crear(eq(2L), any(), anyInt(), any(), any(), anyLong(), anyLong(), anyLong())).thenReturn(null);
            when(repository.listarDelPeriodo(MES_PASADO.toString()))
                    .thenReturn(List.of(liquidacion(11L, EstadoLiquidacion.COBRADA, "19540")));

            List<LiquidacionDTO> resultado = servicio.liquidarMes(MES_PASADO);

            verify(cobros).crear(eq(1L), eq(MES_PASADO.toString()), eq(3), eq(new BigDecimal("10")), any(), eq(5L), eq(0L), eq(1L));
            verify(cobros).cobrar(11L);
            verify(cobros, times(1)).cobrar(anyLong());
            assertEquals(1, resultado.size());
            assertEquals("COBRADA", resultado.get(0).estado);
        }

        @Test
        void unComercioQueFallaNoFrenaALosDemas() {
            dosComerciosActivosYUnoDeBaja();
            when(cobros.crear(eq(1L), any(), anyInt(), any(), any(), anyLong(), anyLong(), anyLong()))
                    .thenThrow(new EJBException("la base no responde"));
            when(cobros.crear(eq(2L), any(), anyInt(), any(), any(), anyLong(), anyLong(), anyLong())).thenReturn(12L);

            servicio.liquidarMes(MES_PASADO);

            verify(cobros).cobrar(12L);
        }

        @Test
        void soloSeLiquidanMesesTerminados() {
            ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.liquidarMes(YearMonth.now()));
            assertTrue(e.getMessage().endsWith("todavía no terminó: solo se puede ver la vista previa"), e.getMessage());
            assertThrows(ValidacionException.class, () -> servicio.liquidarMes(YearMonth.now().plusMonths(1)));
            assertEquals("Elegí el mes a liquidar",
                    assertThrows(ValidacionException.class, () -> servicio.liquidarMes(null)).getMessage());
            assertEquals("Elegí el mes",
                    assertThrows(ValidacionException.class, () -> servicio.previsualizar(null)).getMessage());
            verifyNoInteractions(cobros);
        }

        @Test
        void reintentaLasPendientesHastaQueElBancoSigaSinResponder() {
            when(repository.listarIdsPendientes()).thenReturn(List.of(1L, 2L, 3L, 4L));
            when(cobros.cobrar(1L)).thenReturn(liquidacion(1L, EstadoLiquidacion.COBRADA, "1"));
            doThrow(new ValidacionException("no existe")).when(cobros).cobrar(2L);
            when(cobros.cobrar(3L)).thenReturn(liquidacion(3L, EstadoLiquidacion.PENDIENTE, "1"));

            servicio.reintentarPendientes();

            verify(cobros).cobrar(3L);
            verify(cobros, never()).cobrar(4L);
        }

        @Test
        void elReintentoManualDevuelveElResultado() {
            when(cobros.cobrar(5L)).thenReturn(liquidacion(5L, EstadoLiquidacion.RECHAZADA, "1"));

            assertEquals("RECHAZADA", servicio.reintentarCobro(5L).estado);
        }

        @Test
        void elComercioVeLasSuyasYLaVistaPreviaDeSuMes() {
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            when(repository.listarDeComercio(3L)).thenReturn(List.of(liquidacion(1L, EstadoLiquidacion.COBRADA, "1")));
            when(pedidos.resumenEntregasDelComercioActual(any(), any()))
                    .thenReturn(new ResumenEntregasDTO(3L, 2, new BigDecimal("1.5")));

            assertEquals(1, servicio.listarDelComercioActual().size());
            LiquidacionDTO previa = servicio.previsualizarMesEnCursoDelComercioActual();

            assertEquals(3L, previa.idComercio);
            assertEquals(YearMonth.now().toString(), previa.periodo);
            assertEquals(new BigDecimal("2400.00"), previa.cargoEnvios);
            assertEquals(new BigDecimal("120.00"), previa.cargoKilos);
            assertEquals(BigDecimal.ZERO.setScale(2), previa.cargoAlmacenamiento);
            assertEquals(new BigDecimal("17520.00"), previa.total);
        }

        @Test
        void listaPeriodosYLaTarifaVigente() {
            when(repository.listarPeriodos()).thenReturn(List.of("2026-09", "2026-08"));

            assertEquals(List.of("2026-09", "2026-08"), servicio.listarPeriodos());
            assertEquals(new BigDecimal("15000.00"), servicio.tarifaVigente().abonoMensual());
        }
    }

    @Nested
    class LimpiezaYLiquidacionAutomatica {

        @Mock private CobroRepository repository;
        @Mock private LiquidacionRepository liquidaciones;
        @InjectMocks private ReferenciasAComercioEnPagos referencias;

        @Mock private ILiquidaciones servicio;
        @InjectMocks private LiquidacionMensual timer;

        @Test
        void alEliminarUnComercioSeBorranSusCobrosYLiquidaciones() {
            referencias.alConfirmarEliminacion(new ComercioEliminado(7L));

            verify(repository).eliminarCobrosDeComercio(7L);
            verify(liquidaciones).eliminarDeComercio(7L);
        }

        @Test
        void elTimerLiquidaElMesAnteriorYReintentaLasPendientes() {
            timer.liquidar();

            verify(servicio).liquidarMes(MES_PASADO);
            verify(servicio).reintentarPendientes();
        }

        @Test
        void unaFallaDelTimerNoSePropaga() {
            doThrow(new ValidacionException("algo")).when(servicio).liquidarMes(any());

            timer.liquidar();

            verify(servicio, never()).reintentarPendientes();
        }
    }
}
