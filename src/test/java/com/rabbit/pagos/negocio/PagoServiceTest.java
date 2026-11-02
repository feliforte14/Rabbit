package com.rabbit.pagos.negocio;

import static com.rabbit.pruebas.Ids.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.integracion.banco.IBancoClient;
import com.rabbit.integracion.banco.ResultadoAutorizacion;
import com.rabbit.pagos.datos.CobroRepository;
import com.rabbit.pagos.datos.model.Cobro;
import com.rabbit.pagos.datos.model.EstadoCobro;
import com.rabbit.pagos.dto.CobroDTO;
import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.enterprise.event.Event;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagoServiceTest {

    @Mock private CobroRepository repository;
    @Mock private IBancoClient banco;
    @Mock private Event<PagoAutorizado> pagoAutorizado;
    @Mock private Event<CobroAnulado> cobroAnulado;
    @Mock private IContextoUsuario contextoUsuario;

    @InjectMocks private PagoService servicio;

    private static final BigDecimal IMPORTE = new BigDecimal("2500.00");

    private static Cobro cobro(Long idPedido, MedioPago medio, EstadoCobro estado, String codigo) {
        Cobro c = conId(new Cobro(), idPedido * 10);
        c.setIdPedido(idPedido);
        c.setIdComercio(3L);
        c.setImporte(IMPORTE);
        c.setMedioPago(medio);
        c.setEstado(estado);
        c.setCodigoAutorizacion(codigo);
        return c;
    }

    private Cobro guardado() {
        ArgumentCaptor<Cobro> captor = ArgumentCaptor.forClass(Cobro.class);
        verify(repository).guardar(captor.capture());
        return captor.getValue();
    }

    @Nested
    class RegistrarCobro {

        @Test
        void unPrepagoSeCobraEnElBancoYQuedaAcreditado() {
            when(banco.autorizar(100L, IMPORTE)).thenReturn(ResultadoAutorizacion.aprobado("AUT-7"));
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 50L));

            Long id = servicio.registrarCobro(100L, 3L, IMPORTE, MedioPago.PREPAGO);

            assertEquals(50L, id);
            Cobro c = guardado();
            assertEquals(EstadoCobro.ACREDITADO, c.getEstado());
            assertEquals("AUT-7", c.getCodigoAutorizacion());
            assertEquals(100L, c.getIdPedido());
            assertEquals(3L, c.getIdComercio());
            assertEquals(IMPORTE, c.getImporte());
            assertNotNull(c.getFechaCreacion());
            assertNotNull(c.getFechaAcreditacion());
            verify(pagoAutorizado).fire(new PagoAutorizado(100L, "AUT-7"));
        }

        @Test
        void unContraEntregaQuedaPendienteSinLlamarAlBanco() {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 51L));

            servicio.registrarCobro(100L, 3L, IMPORTE, MedioPago.CONTRA_ENTREGA);

            Cobro c = guardado();
            assertEquals(EstadoCobro.PENDIENTE, c.getEstado());
            assertNull(c.getCodigoAutorizacion());
            assertNull(c.getFechaAcreditacion());
            verifyNoInteractions(banco);
            verify(pagoAutorizado, never()).fire(any());
        }

        @Test
        void siElBancoRechazaNoSeGuardaNiSeAvisa() {
            when(banco.autorizar(100L, IMPORTE)).thenReturn(ResultadoAutorizacion.rechazado("Fondos insuficientes"));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarCobro(100L, 3L, IMPORTE, MedioPago.PREPAGO));
            assertEquals("Pago rechazado por el banco: Fondos insuficientes", e.getMessage());
            verify(repository, never()).guardar(any());
            verify(pagoAutorizado, never()).fire(any());
        }

        @Test
        void siElBancoNoRespondeElPedidoNoSeConfirma() {
            when(banco.autorizar(100L, IMPORTE)).thenReturn(ResultadoAutorizacion.noDisponible());

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarCobro(100L, 3L, IMPORTE, MedioPago.PREPAGO));
            assertEquals("El banco no respondió: el pedido 100 no se confirmó. Intentá de nuevo en unos minutos.",
                    e.getMessage());
            verify(repository, never()).guardar(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-1"})
        void rechazaImportesNoPositivos(String importe) {
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarCobro(100L, 3L, new BigDecimal(importe), MedioPago.PREPAGO));
            assertEquals("El pedido 100 no tiene un importe válido para cobrar", e.getMessage());
            verifyNoInteractions(banco);
        }

        @Test
        void rechazaSinImporteOSinMedioDePago() {
            assertThrows(ValidacionException.class, () -> servicio.registrarCobro(100L, 3L, null, MedioPago.PREPAGO));
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarCobro(100L, 3L, IMPORTE, null));
            assertEquals("El pedido 100 no tiene medio de pago", e.getMessage());
        }

        @Test
        void unCentavoAlcanza() {
            when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 52L));

            assertEquals(52L, servicio.registrarCobro(100L, 3L, new BigDecimal("0.01"), MedioPago.CONTRA_ENTREGA));
        }

        @Test
        void noRegistraDosCobrosParaElMismoPedido() {
            when(repository.buscarPorPedido(100L)).thenReturn(cobro(100L, MedioPago.PREPAGO, EstadoCobro.ACREDITADO, "AUT-1"));

            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> servicio.registrarCobro(100L, 3L, IMPORTE, MedioPago.PREPAGO));
            assertEquals("El pedido 100 ya tiene un cobro registrado", e.getMessage());
            verifyNoInteractions(banco);
        }
    }

    @Nested
    class ContraEntrega {

        @Test
        void alEntregarseAcreditaElCobroPendiente() {
            Cobro c = cobro(100L, MedioPago.CONTRA_ENTREGA, EstadoCobro.PENDIENTE, null);
            when(repository.buscarPorPedidoParaActualizar(100L)).thenReturn(c);

            servicio.registrarCobroContraEntrega(100L);

            assertEquals(EstadoCobro.ACREDITADO, c.getEstado());
            assertNotNull(c.getFechaAcreditacion());
            verify(repository).actualizar(c);
        }

        @Test
        void esIdempotenteYNoTocaPrepagosNiPedidosSinCobro() {
            Cobro yaAcreditado = cobro(100L, MedioPago.CONTRA_ENTREGA, EstadoCobro.ACREDITADO, null);
            Cobro prepago = cobro(101L, MedioPago.PREPAGO, EstadoCobro.ACREDITADO, "AUT-1");
            Cobro anulado = cobro(102L, MedioPago.CONTRA_ENTREGA, EstadoCobro.ANULADO, null);
            when(repository.buscarPorPedidoParaActualizar(100L)).thenReturn(yaAcreditado);
            when(repository.buscarPorPedidoParaActualizar(101L)).thenReturn(prepago);
            when(repository.buscarPorPedidoParaActualizar(102L)).thenReturn(anulado);

            servicio.registrarCobroContraEntrega(100L);
            servicio.registrarCobroContraEntrega(101L);
            servicio.registrarCobroContraEntrega(102L);
            servicio.registrarCobroContraEntrega(103L);

            verify(repository, never()).actualizar(any());
            assertEquals(EstadoCobro.ANULADO, anulado.getEstado());
        }
    }

    @Nested
    class Anulacion {

        @Test
        void anularUnPrepagoPideLaReversaDespuesDelCommit() {
            Cobro c = cobro(100L, MedioPago.PREPAGO, EstadoCobro.ACREDITADO, "AUT-9");
            when(repository.buscarPorPedidoParaActualizar(100L)).thenReturn(c);

            servicio.anularCobro(100L);

            assertEquals(EstadoCobro.ANULADO, c.getEstado());
            verify(repository).actualizar(c);
            verify(cobroAnulado).fire(new CobroAnulado(100L, "AUT-9"));
        }

        @Test
        void anularUnContraEntregaNoPideReversa() {
            Cobro c = cobro(100L, MedioPago.CONTRA_ENTREGA, EstadoCobro.PENDIENTE, null);
            when(repository.buscarPorPedidoParaActualizar(100L)).thenReturn(c);

            servicio.anularCobro(100L);

            assertEquals(EstadoCobro.ANULADO, c.getEstado());
            verify(cobroAnulado, never()).fire(any());
        }

        @Test
        void anularDosVecesNoReversaDosVeces() {
            when(repository.buscarPorPedidoParaActualizar(100L))
                    .thenReturn(cobro(100L, MedioPago.PREPAGO, EstadoCobro.ANULADO, "AUT-9"));

            servicio.anularCobro(100L);
            servicio.anularCobro(200L);

            verify(repository, never()).actualizar(any());
            verify(cobroAnulado, never()).fire(any());
        }

        @Test
        void laDevolucionAlComercioAnulaYReversaElPrepago() {
            Cobro c = cobro(100L, MedioPago.PREPAGO, EstadoCobro.ACREDITADO, "AUT-3");
            when(repository.buscarPorPedidoParaActualizar(100L)).thenReturn(c);

            servicio.anularCobroPorDevolucion(100L);

            assertEquals(EstadoCobro.ANULADO, c.getEstado());
            verify(cobroAnulado).fire(new CobroAnulado(100L, "AUT-3"));
        }

        @Test
        void laDevolucionEsIdempotente() {
            when(repository.buscarPorPedidoParaActualizar(100L))
                    .thenReturn(cobro(100L, MedioPago.PREPAGO, EstadoCobro.ANULADO, "AUT-3"));
            Cobro contraEntrega = cobro(101L, MedioPago.CONTRA_ENTREGA, EstadoCobro.PENDIENTE, null);
            when(repository.buscarPorPedidoParaActualizar(101L)).thenReturn(contraEntrega);

            servicio.anularCobroPorDevolucion(100L);
            servicio.anularCobroPorDevolucion(101L);
            servicio.anularCobroPorDevolucion(102L);

            assertEquals(EstadoCobro.ANULADO, contraEntrega.getEstado());
            verify(repository).actualizar(contraEntrega);
            verify(cobroAnulado, never()).fire(any());
        }
    }

    @Nested
    class Consultas {

        @Test
        void obtieneElCobroDeUnPedidoONull() {
            when(repository.buscarPorPedido(100L)).thenReturn(cobro(100L, MedioPago.PREPAGO, EstadoCobro.ACREDITADO, "AUT-1"));

            CobroDTO dto = servicio.obtenerCobroDePedido(100L);

            assertEquals(100L, dto.getIdPedido());
            assertEquals("ACREDITADO", dto.estado);
            assertNull(servicio.obtenerCobroDePedido(200L));
        }

        @Test
        void listaTodosOLosDelComercioDeQuienLlama() {
            when(repository.listarTodos()).thenReturn(List.of(cobro(1L, MedioPago.PREPAGO, EstadoCobro.ACREDITADO, "A"),
                    cobro(2L, MedioPago.CONTRA_ENTREGA, EstadoCobro.PENDIENTE, null)));
            when(contextoUsuario.idComercioActual()).thenReturn(3L);
            when(repository.listarDeComercio(3L)).thenReturn(List.of(cobro(1L, MedioPago.PREPAGO, EstadoCobro.ACREDITADO, "A")));

            assertEquals(2, servicio.listarTodos().size());
            assertEquals(1, servicio.listarCobrosDelComercioActual().size());
            verify(repository).listarDeComercio(3L);
        }
    }

    @Nested
    class Compensaciones {

        @Mock private IBancoClient bancoDeReversas;
        @InjectMocks private ReversasBancarias reversas;

        @Test
        void siLaConfirmacionSeDeshaceDespuesDeCobrarSeReversa() {
            when(bancoDeReversas.reversar("AUT-5")).thenReturn(true);

            reversas.compensarPagoDeshecho(new PagoAutorizado(100L, "AUT-5"));

            verify(bancoDeReversas).reversar("AUT-5");
        }

        @Test
        void unCobroAnuladoSeDevuelveEnElBancoAunqueElBancoNoConfirme() {
            when(bancoDeReversas.reversar("AUT-6")).thenReturn(false);

            reversas.devolverCobroAnulado(new CobroAnulado(100L, "AUT-6"));

            verify(bancoDeReversas).reversar("AUT-6");
        }
    }

    @Nested
    class SuscriptorDelTopico {

        @Mock private IRegistroCobros cobros;
        @Mock private Message mensaje;
        @InjectMocks private SuscriptorPagosEstadoPedido suscriptor;

        @Test
        void entregadoAcreditaElContraEntrega() throws JMSException {
            when(mensaje.getLongProperty("idPedido")).thenReturn(100L);
            when(mensaje.getStringProperty("estado")).thenReturn("ENTREGADO");

            suscriptor.onMessage(mensaje);

            verify(cobros).registrarCobroContraEntrega(100L);
            verify(cobros, never()).anularCobroPorDevolucion(anyLong());
        }

        @Test
        void devueltoAlComercioAnulaElCobro() throws JMSException {
            when(mensaje.getLongProperty("idPedido")).thenReturn(100L);
            when(mensaje.getStringProperty("estado")).thenReturn("DEVUELTO_AL_COMERCIO");

            suscriptor.onMessage(mensaje);

            verify(cobros).anularCobroPorDevolucion(100L);
            verify(cobros, never()).registrarCobroContraEntrega(anyLong());
        }

        @Test
        void unMensajeIlegibleSeConsumeSinTocarCobros() throws JMSException {
            when(mensaje.getLongProperty("idPedido")).thenThrow(new JMSException("sin propiedad"));

            suscriptor.onMessage(mensaje);

            verifyNoInteractions(cobros);
        }

        @Test
        void unIdQueNoEsNumeroTambienSeConsume() throws JMSException {
            when(mensaje.getLongProperty("idPedido")).thenThrow(new NumberFormatException("abc"));

            suscriptor.onMessage(mensaje);

            verifyNoInteractions(cobros);
        }
    }

    @ParameterizedTest
    @EnumSource(MedioPago.class)
    void cadaMedioDePagoDejaElCobroEnSuEstadoInicial(MedioPago medio) {
        when(banco.autorizar(any(), any())).thenReturn(ResultadoAutorizacion.aprobado("AUT-1"));
        when(repository.guardar(any())).thenAnswer(inv -> conId(inv.getArgument(0), 1L));

        servicio.registrarCobro(100L, 3L, IMPORTE, medio);

        EstadoCobro esperado = medio == MedioPago.PREPAGO ? EstadoCobro.ACREDITADO : EstadoCobro.PENDIENTE;
        assertEquals(esperado, guardado().getEstado());
        assertTrue(guardado().getFechaCreacion() != null);
    }
}
