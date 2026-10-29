package com.rabbit.pedidos.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.comercios.negocio.ComercioEliminado;
import com.rabbit.comercios.negocio.EliminacionDeComercio;
import com.rabbit.inventario.negocio.EliminacionDeItem;
import com.rabbit.pedidos.datos.PedidoRepository;
import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.OrigenPedido;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.datos.model.PedidoExterno;
import com.rabbit.pedidos.dto.IndicadoresDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.repartidores.negocio.EliminacionDeRepartidor;
import com.rabbit.repartidores.negocio.IAsignacionRepartidores;
import com.rabbit.repartidores.negocio.RepartidorEliminado;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import com.rabbit.transportistas.datos.model.EstadoEnvio;
import com.rabbit.transportistas.negocio.EstadoEnvioCambiado;
import jakarta.ejb.EJBException;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.jms.BytesMessage;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidosEnSegundoPlanoTest {

    private static PedidoExterno externo(Long id) {
        return conId(new PedidoExterno(), id);
    }

    @Nested
    class Sincronizador {

        @Mock private PedidoRepository repository;
        @Mock private IGestionPedidos gestionPedidos;
        @InjectMocks private SincronizadorDePedidos sincronizador;

        @AfterEach
        void reanudar() {
            System.clearProperty("rabbit.sincronizador.pausado");
        }

        @Test
        void cadaPedidoSeJuegaSuSuerteSinFrenarALosDemas() {
            when(repository.listarNoSincronizados()).thenReturn(List.of(externo(1L), externo(2L), externo(3L),
                    externo(4L), externo(5L), externo(6L)));
            when(gestionPedidos.sincronizarPedidoExterno(1L)).thenReturn(100L);
            when(gestionPedidos.sincronizarPedidoExterno(2L)).thenThrow(new PedidoYaSincronizadoException(2L));
            when(gestionPedidos.sincronizarPedidoExterno(3L)).thenThrow(new ConflictoDeStockException("choque"));
            when(gestionPedidos.sincronizarPedidoExterno(4L)).thenThrow(new ValidacionException("sin stock"));
            when(gestionPedidos.sincronizarPedidoExterno(5L)).thenThrow(new IllegalStateException("base caída"));
            when(gestionPedidos.sincronizarPedidoExterno(6L)).thenThrow(new ValidacionException("comercio de baja"));
            doThrow(new IllegalStateException("tampoco")).when(gestionPedidos).descartarPedidoExterno(6L, "comercio de baja");

            sincronizador.alArrancar();
            sincronizador.sincronizarPendientes();

            verify(gestionPedidos).descartarPedidoExterno(4L, "sin stock");
            verify(gestionPedidos).descartarPedidoExterno(6L, "comercio de baja");
            verify(gestionPedidos, times(2)).descartarPedidoExterno(anyLong(), anyString());
            verify(gestionPedidos).sincronizarPedidoExterno(6L);
        }

        @Test
        void sinPendientesOPausadoNoHaceNada() {
            when(repository.listarNoSincronizados()).thenReturn(List.of());
            sincronizador.sincronizarPendientes();
            verifyNoInteractions(gestionPedidos);

            System.setProperty("rabbit.sincronizador.pausado", "true");
            sincronizador.sincronizarPendientes();
            verify(repository, times(1)).listarNoSincronizados();
        }
    }

    @Nested
    class ListaDeEspera {

        @Mock private PedidoRepository repository;
        @Mock private IGestionPedidos gestion;
        @Mock private IAsignacionRepartidores repartidores;
        @InjectMocks private AsignadorDeEspera asignador;

        @Test
        void recorreLaFilaYSacaLoQueNoSePuedeAsignar() {
            when(repartidores.antesDelCorte()).thenReturn(true);
            when(repository.listarIdsEnEspera()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L));
            when(gestion.asignarDesdeEspera(1L)).thenReturn(5L);
            when(gestion.asignarDesdeEspera(2L)).thenReturn(null);
            when(gestion.asignarDesdeEspera(3L)).thenThrow(new SinRepartidorConLugarException("lleno"));
            when(gestion.asignarDesdeEspera(4L)).thenThrow(new ValidacionException("sin zona"));
            when(gestion.asignarDesdeEspera(5L)).thenThrow(new EJBException("base"));
            when(gestion.asignarDesdeEspera(6L)).thenThrow(new ValidacionException("cancelado"));
            doThrow(new IllegalStateException("x")).when(gestion).sacarDeEspera(6L, "cancelado");

            asignador.cadaMinuto();

            verify(gestion).sacarDeEspera(4L, "sin zona");
            verify(gestion).sacarDeEspera(6L, "cancelado");
            verify(gestion, never()).sacarDeEspera(eq(3L), any());
            verify(gestion, never()).sacarDeEspera(eq(5L), any());
        }

        @Test
        void despuesDeLaHoraDeCorteNoAsigna() {
            when(repartidores.antesDelCorte()).thenReturn(false);

            asignador.procesar();

            verifyNoInteractions(repository, gestion);
        }

        @Test
        void unPedidoQueLlegaMientrasRecorreProvocaOtraVuelta() {
            when(repartidores.antesDelCorte()).thenReturn(true);
            when(repository.listarIdsEnEspera()).thenReturn(List.of(1L));
            int[] llamadas = {0};
            doAnswer(inv -> {
                if (llamadas[0]++ == 0) {
                    asignador.procesar();
                }
                return null;
            }).when(gestion).asignarDesdeEspera(1L);

            asignador.procesar();

            verify(gestion, times(2)).asignarDesdeEspera(1L);
            asignador.procesar();
            verify(gestion, times(3)).asignarDesdeEspera(1L);
        }

        @Nested
        class Avisos {

            @Mock private TimerService timers;
            @Mock private AsignadorDeEspera asignadorMock;
            @InjectMocks private AvisosDeEspera avisos;

            @ParameterizedTest
            @EnumSource(value = EstadoPedido.class, names = {"ENTREGADO", "CANCELADO", "PENDIENTE", "DEVUELTO_AL_COMERCIO"})
            void unRepartidorQueSeLiberaDespiertaALaFila(EstadoPedido estado) {
                avisos.alCambiarEstado(new EstadoPedidoCambiado(1L, 3L, estado, LocalDateTime.now()));

                verify(timers).createSingleActionTimer(eq(500L), any(TimerConfig.class));
            }

            @ParameterizedTest
            @EnumSource(value = EstadoPedido.class, names = {"CONFIRMADO", "EN_CAMINO"})
            void tomarUnPedidoNoLibera(EstadoPedido estado) {
                avisos.alCambiarEstado(new EstadoPedidoCambiado(1L, 3L, estado, LocalDateTime.now()));

                verifyNoInteractions(timers);
            }

            @Test
            void alVencerProcesaLaFila() throws Exception {
                var campo = AvisosDeEspera.class.getDeclaredField("asignador");
                campo.setAccessible(true);
                campo.set(avisos, asignadorMock);

                avisos.alVencer();

                verify(asignadorMock).procesar();
            }
        }
    }

    @Nested
    class ListenerDePedidosExternos {

        @Mock private IGestionPedidos gestionPedidos;
        @InjectMocks private PedidoExternoListener listener;

        private TextMessage mensaje(String texto) throws JMSException {
            TextMessage m = mock(TextMessage.class);
            when(m.getText()).thenReturn(texto);
            return m;
        }

        @Test
        void sincronizaElPedidoDelMensaje() throws Exception {
            listener.onMessage(mensaje("{\"idPedidoExterno\":77}"));

            verify(gestionPedidos).sincronizarPedidoExterno(77L);
        }

        @Test
        void unoYaSincronizadoSeIgnoraYUnoInvalidoSeDescarta() throws Exception {
            when(gestionPedidos.sincronizarPedidoExterno(77L)).thenThrow(new PedidoYaSincronizadoException(77L));
            listener.onMessage(mensaje("{\"idPedidoExterno\":77}"));
            verify(gestionPedidos, never()).descartarPedidoExterno(anyLong(), any());

            when(gestionPedidos.sincronizarPedidoExterno(78L)).thenThrow(new ValidacionException("sin stock"));
            listener.onMessage(mensaje("{\"idPedidoExterno\":78}"));
            verify(gestionPedidos).descartarPedidoExterno(78L, "sin stock");
        }

        @Test
        void losErroresPasajerosVuelvenAlContenedorParaReintentar() throws Exception {
            ConflictoDeStockException choque = new ConflictoDeStockException("choque");
            when(gestionPedidos.sincronizarPedidoExterno(77L)).thenThrow(choque);
            assertSame(choque, assertThrows(ConflictoDeStockException.class,
                    () -> listener.onMessage(mensaje("{\"idPedidoExterno\":77}"))));

            IllegalStateException caida = new IllegalStateException("base");
            when(gestionPedidos.sincronizarPedidoExterno(78L)).thenThrow(caida);
            assertSame(caida, assertThrows(IllegalStateException.class,
                    () -> listener.onMessage(mensaje("{\"idPedidoExterno\":78}"))));
            verify(gestionPedidos, never()).descartarPedidoExterno(anyLong(), any());
        }

        @Test
        void unMensajeIlegibleSeDescartaSinSincronizar() throws Exception {
            listener.onMessage(mock(BytesMessage.class));
            listener.onMessage(mensaje("no es json"));
            listener.onMessage(mensaje("{\"otro\":1}"));
            listener.onMessage(mensaje("{\"idPedidoExterno\":\"77\"}"));
            listener.onMessage(mensaje(null));
            TextMessage roto = mock(TextMessage.class);
            when(roto.getText()).thenThrow(new JMSException("roto"));
            listener.onMessage(roto);

            verifyNoInteractions(gestionPedidos);
        }
    }

    @Nested
    class EnviosDeTransportistas {

        @Mock private IGestionPedidos gestion;
        @Mock private ISeguimientoPedido seguimiento;
        @InjectMocks private ActualizacionDePedidosPorEnvio actualizacion;

        private void enEstado(String estado) {
            PedidoDTO p = new PedidoDTO();
            p.estado = estado;
            when(seguimiento.consultarEstadoPedido(100L)).thenReturn(p);
        }

        private void cambia(EstadoEnvio nuevo) {
            actualizacion.alCambiarEnvio(new EstadoEnvioCambiado(100L, EstadoEnvio.SOLICITADO, nuevo));
        }

        @Test
        void enTransitoDespachaUnPedidoConfirmado() {
            enEstado("CONFIRMADO");
            cambia(EstadoEnvio.EN_TRANSITO);
            verify(gestion).despacharPedido(100L);

            enEstado("EN_CAMINO");
            cambia(EstadoEnvio.EN_TRANSITO);
            verify(gestion, times(1)).despacharPedido(100L);
        }

        @Test
        void entregadoDespachaSiHaceFaltaYRegistraLaEntrega() {
            enEstado("CONFIRMADO");
            cambia(EstadoEnvio.ENTREGADO);
            verify(gestion).despacharPedido(100L);
            verify(gestion).registrarEntregaDeTransportista(100L);

            enEstado("EN_CAMINO");
            cambia(EstadoEnvio.ENTREGADO);
            verify(gestion, times(1)).despacharPedido(100L);
            verify(gestion, times(2)).registrarEntregaDeTransportista(100L);

            enEstado("ENTREGADO");
            cambia(EstadoEnvio.ENTREGADO);
            verify(gestion, times(2)).registrarEntregaDeTransportista(100L);
        }

        @Test
        void unaCancelacionOUnaSolicitudNoMuevenElPedido() {
            enEstado("CONFIRMADO");
            cambia(EstadoEnvio.CANCELADO);
            cambia(EstadoEnvio.SOLICITADO);

            verify(gestion, never()).despacharPedido(any());
            verify(gestion, never()).registrarEntregaDeTransportista(any());
        }
    }

    @Nested
    class Publicadores {

        @Mock private ConnectionFactory connectionFactory;
        @Mock private Topic topicoEstados;
        @Mock private Queue colaPedidosExternos;
        @Mock private JMSContext contexto;
        @Mock private JMSProducer productor;
        @Mock private TextMessage mensaje;
        @InjectMocks private PublicadorEstadosPedido estados;
        @InjectMocks private PublicadorPedidosExternos externos;

        @BeforeEach
        void jms() {
            when(connectionFactory.createContext()).thenReturn(contexto);
            when(contexto.createTextMessage(anyString())).thenReturn(mensaje);
            when(contexto.createProducer()).thenReturn(productor);
        }

        @Test
        void publicaElCambioDeEstadoEnElTopico() throws Exception {
            EstadoPedidoCambiado evento = new EstadoPedidoCambiado(100L, 3L, EstadoPedido.EN_CAMINO,
                    LocalDateTime.of(2026, 10, 29, 10, 0));

            estados.publicarCambioDeEstado(evento);

            verify(contexto).createTextMessage(PublicadorEstadosPedido.construirPayload(evento));
            verify(mensaje).setStringProperty("estado", "EN_CAMINO");
            verify(mensaje).setLongProperty("idPedido", 100L);
            verify(productor).send(topicoEstados, mensaje);
            verify(contexto).close();
        }

        @Test
        void publicaElPedidoExternoEnLaCola() throws Exception {
            externos.publicarPedidoExternoRegistrado(new PedidoExternoRegistrado(77L, OrigenPedido.PUNTO_PICKING));

            verify(contexto).createTextMessage("{\"idPedidoExterno\":77}");
            verify(mensaje).setStringProperty("origen", "PUNTO_PICKING");
            verify(productor).send(colaPedidosExternos, mensaje);
        }

        @Test
        void siElBrokerFallaNoRompeLaOperacion() throws Exception {
            doThrow(new JMSException("prop")).when(mensaje).setStringProperty(anyString(), anyString());
            estados.publicarCambioDeEstado(new EstadoPedidoCambiado(100L, 3L, EstadoPedido.CANCELADO, LocalDateTime.now()));
            externos.publicarPedidoExternoRegistrado(new PedidoExternoRegistrado(77L, OrigenPedido.STOCK_CONSIGNADO));

            when(connectionFactory.createContext()).thenThrow(new JMSRuntimeException("caído"));
            estados.publicarCambioDeEstado(new EstadoPedidoCambiado(100L, 3L, EstadoPedido.CANCELADO, LocalDateTime.now()));
            externos.publicarPedidoExternoRegistrado(new PedidoExternoRegistrado(77L, OrigenPedido.STOCK_CONSIGNADO));

            verify(productor, never()).send(any(jakarta.jms.Destination.class), any(jakarta.jms.Message.class));
        }
    }

    @Nested
    class Indicadores {

        @Mock private PedidoRepository repository;
        @Mock private IContextoUsuario contextoUsuario;
        @InjectMocks private IndicadoresService indicadores;

        private Pedido pedido(Long id, EstadoPedido estado, LocalDateTime creado) {
            Pedido p = conId(new Pedido(), id);
            p.setEstado(estado);
            p.setFechaCreacion(creado);
            p.setFechaActualizacion(creado);
            p.setLineas(List.of());
            return p;
        }

        @Test
        void comparaElPeriodoConElAnteriorYBuscaEstrellasSoloDeLoEntregado() {
            LocalDate hoy = LocalDate.now();
            LocalDateTime inicio = hoy.minusDays(6).atStartOfDay();
            when(repository.contarEnEspera(3L)).thenReturn(2L);
            when(repository.listarRecibidosEntre(inicio, hoy.plusDays(1).atStartOfDay(), 3L)).thenReturn(List.of(
                    pedido(1L, EstadoPedido.ENTREGADO, hoy.atTime(9, 0)), pedido(2L, EstadoPedido.PENDIENTE, hoy.atTime(10, 0))));
            when(repository.listarRecibidosEntre(hoy.minusDays(13).atStartOfDay(), inicio, 3L)).thenReturn(List.of());
            when(repository.estrellasDePedidos(List.of(1L))).thenReturn(Map.of(1L, 5));

            IndicadoresDTO tablero = indicadores.calcular(7, 3L);

            assertEquals(hoy.minusDays(6), tablero.desde);
            assertEquals(hoy, tablero.hasta);
            assertEquals(2, tablero.recibidos);
            assertEquals(1, tablero.entregados);
            assertEquals(0, tablero.anterior.recibidos);
            assertEquals(hoy.minusDays(7), tablero.anterior.hasta);
            assertEquals(hoy.minusDays(13), tablero.anterior.desde);
            verify(repository, times(1)).estrellasDePedidos(any());
        }

        @Test
        void elComercioVeSoloLoSuyoYElPeriodoEsFijo() {
            when(contextoUsuario.idComercioActual()).thenReturn(4L);
            when(repository.listarRecibidosEntre(any(), any(), eq(4L))).thenReturn(List.of());

            assertEquals(0, indicadores.calcularDelComercioActual(30).recibidos);
            verify(repository).contarEnEspera(4L);

            assertEquals("El período tiene que ser de [7, 30, 90] días",
                    assertThrows(ValidacionException.class, () -> indicadores.calcular(15, null)).getMessage());
        }
    }

    @Nested
    class ReferenciasDeOtrosComponentes {

        @Mock private PedidoRepository repository;
        @InjectMocks private ReferenciasAItemEnPedidos items;
        @InjectMocks private ReferenciasARepartidorEnPedidos repartidores;
        @InjectMocks private ReferenciasAComercioEnPedidos comercios;

        @Test
        void unItemEnPedidosEnCursoODelErpNoSeElimina() {
            when(repository.listarIdsEnCursoConItem(10L)).thenReturn(List.of(100L));
            when(repository.contarPedidosExternosPendientesConItem(10L)).thenReturn(1L);
            EliminacionDeItem uno = new EliminacionDeItem(10L);
            items.alEliminarItem(uno);
            assertEquals(List.of("lo usa el pedido #100, en curso", "lo pide 1 pedido del ERP que todavía no se procesó"),
                    uno.getImpedimentos());

            when(repository.listarIdsEnCursoConItem(11L)).thenReturn(List.of(100L, 101L));
            when(repository.contarPedidosExternosPendientesConItem(11L)).thenReturn(3L);
            EliminacionDeItem varios = new EliminacionDeItem(11L);
            items.alEliminarItem(varios);
            assertEquals(List.of("lo usan los pedidos #100, #101, en curso",
                    "lo piden 3 pedidos del ERP que todavía no se procesaron"), varios.getImpedimentos());

            when(repository.listarIdsEnCursoConItem(12L)).thenReturn(List.of());
            EliminacionDeItem libre = new EliminacionDeItem(12L);
            items.alEliminarItem(libre);
            assertTrue(libre.getImpedimentos().isEmpty());
        }

        @Test
        void unRepartidorConPedidosSinEntregarNoSeElimina() {
            when(repository.contarPedidosEnCursoDeRepartidor(5L)).thenReturn(2L);
            EliminacionDeRepartidor ocupado = new EliminacionDeRepartidor(5L);
            repartidores.alEliminarRepartidor(ocupado);
            assertEquals(List.of("2 pedido(s) asignado(s) sin entregar"), ocupado.getImpedimentos());

            EliminacionDeRepartidor libre = new EliminacionDeRepartidor(6L);
            repartidores.alEliminarRepartidor(libre);
            assertTrue(libre.getImpedimentos().isEmpty());

            repartidores.alConfirmarEliminacion(new RepartidorEliminado(6L));
            verify(repository).desvincularRepartidor(6L);
        }

        @Test
        void unComercioConPedidosNoSeEliminaYAlEliminarloSeBorranLosCancelados() {
            when(repository.contarPedidosNoCanceladosDeComercio(3L)).thenReturn(4L);
            when(repository.contarPedidosExternosPendientesDeComercio(3L)).thenReturn(1L);
            EliminacionDeComercio conPedidos = new EliminacionDeComercio(3L);
            comercios.alEliminarComercio(conPedidos);
            assertEquals(List.of("4 pedido(s) en curso o entregado(s)", "1 pedido(s) del ERP sin sincronizar"),
                    conPedidos.getImpedimentos());

            EliminacionDeComercio sinPedidos = new EliminacionDeComercio(9L);
            comercios.alEliminarComercio(sinPedidos);
            assertTrue(sinPedidos.getImpedimentos().isEmpty());

            when(repository.eliminarPedidosDeComercio(9L)).thenReturn(new long[] {2, 1});
            comercios.alConfirmarEliminacion(new ComercioEliminado(9L));
            verify(repository).eliminarPedidosDeComercio(9L);
        }
    }
}
