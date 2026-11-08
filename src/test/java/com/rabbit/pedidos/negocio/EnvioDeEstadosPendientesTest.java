package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Un aviso de cambio de estado no se pierde si el broker está caído: queda en la
 * bandeja y sale cuando vuelve (BandejaDeEstados + EnvioDeEstadosPendientes).
 */
@ExtendWith(MockitoExtension.class)
class EnvioDeEstadosPendientesTest {

    private static final LocalDateTime CAMBIO = LocalDateTime.of(2026, 11, 8, 10, 0);

    @Mock private ConnectionFactory connectionFactory;
    @Mock private Topic topico;
    @Mock private BandejaDeEstados bandeja;
    @Mock private JMSContext contexto;
    @Mock private JMSProducer productor;
    @Mock private TextMessage mensaje;
    @InjectMocks private EnvioDeEstadosPendientes envio;

    private static BandejaDeEstados.Pendiente pendiente(long id, long idPedido, EstadoPedido estado) {
        return new BandejaDeEstados.Pendiente(id, new EstadoPedidoCambiado(idPedido, 3L, estado, CAMBIO));
    }

    private void brokerDisponible() {
        when(connectionFactory.createContext()).thenReturn(contexto);
        when(contexto.createTextMessage(anyString())).thenReturn(mensaje);
        when(contexto.createProducer()).thenReturn(productor);
    }

    @Test
    void sinPendientesNoSeAbreLaConexionConElBroker() {
        when(bandeja.pendientes(EnvioDeEstadosPendientes.LOTE)).thenReturn(List.of());

        assertEquals(0, envio.enviarPendientes());

        verifyNoInteractions(connectionFactory);
    }

    @Test
    void mandaCadaPendienteAlTopicoConSusPropiedadesYLoMarcaPublicado() throws Exception {
        brokerDisponible();
        when(bandeja.pendientes(EnvioDeEstadosPendientes.LOTE)).thenReturn(List.of(
                pendiente(1, 100, EstadoPedido.EN_CAMINO), pendiente(2, 100, EstadoPedido.ENTREGADO)));

        assertEquals(2, envio.enviarPendientes());

        verify(contexto).createTextMessage(PublicadorEstadosPedido.construirPayload(
                new EstadoPedidoCambiado(100L, 3L, EstadoPedido.EN_CAMINO, CAMBIO)));
        verify(mensaje).setStringProperty("estado", "EN_CAMINO");
        verify(mensaje).setStringProperty("estado", "ENTREGADO");
        verify(mensaje, times(2)).setLongProperty("idPedido", 100L);
        verify(productor, times(2)).send(topico, mensaje);
        InOrder orden = inOrder(bandeja);
        orden.verify(bandeja).marcarPublicado(1L);
        orden.verify(bandeja).marcarPublicado(2L);
        verify(bandeja, never()).registrarFallo(anyLong());
        verify(contexto).close();
    }

    @Test
    void conElBrokerCaidoTodoQuedaPendienteYNoSePropagaLaExcepcion() {
        when(bandeja.pendientes(EnvioDeEstadosPendientes.LOTE)).thenReturn(List.of(pendiente(1, 100, EstadoPedido.ENTREGADO)));
        when(connectionFactory.createContext()).thenThrow(new JMSRuntimeException("broker caído"));

        assertEquals(0, envio.enviarPendientes());

        verify(bandeja, never()).marcarPublicado(anyLong());
    }

    @Test
    void siUnEnvioFallaSeCortaLaPasadaYLosRestantesEsperanAlReintento() throws Exception {
        brokerDisponible();
        when(bandeja.pendientes(EnvioDeEstadosPendientes.LOTE)).thenReturn(List.of(
                pendiente(1, 100, EstadoPedido.CONFIRMADO), pendiente(2, 100, EstadoPedido.EN_CAMINO)));
        doThrow(new JMSRuntimeException("se cayó")).when(productor).send(eq(topico), org.mockito.ArgumentMatchers.any(Message.class));

        assertEquals(0, envio.enviarPendientes());

        verify(bandeja).registrarFallo(1L);
        verify(bandeja, never()).marcarPublicado(anyLong());
        verify(productor, times(1)).send(eq(topico), org.mockito.ArgumentMatchers.any(Message.class));
    }

    @Test
    void siFallaArmarElMensajeSeRegistraElFalloYNoSeSigueConElSiguiente() throws Exception {
        when(connectionFactory.createContext()).thenReturn(contexto);
        when(contexto.createTextMessage(anyString())).thenReturn(mensaje);
        when(bandeja.pendientes(EnvioDeEstadosPendientes.LOTE)).thenReturn(List.of(
                pendiente(1, 100, EstadoPedido.CONFIRMADO), pendiente(2, 101, EstadoPedido.PENDIENTE)));
        doThrow(new JMSException("prop")).when(mensaje).setStringProperty(anyString(), anyString());

        // El primero falla al armar el mensaje: se registra el fallo y no se sigue con el segundo
        // (el orden de los avisos se conserva).
        assertEquals(0, envio.enviarPendientes());

        verify(bandeja).registrarFallo(1L);
        verify(bandeja, never()).marcarPublicado(2L);
    }

    @Test
    void siNoSePuedeLeerLaBandejaNoSeRompeElTimer() {
        when(bandeja.pendientes(EnvioDeEstadosPendientes.LOTE)).thenThrow(new IllegalStateException("base caída"));

        assertEquals(0, envio.enviarPendientes());

        verifyNoInteractions(connectionFactory);
    }

    @Nested
    class Timers {
        @Test
        void elTimerReenviaLosPendientes() {
            when(bandeja.pendientes(EnvioDeEstadosPendientes.LOTE)).thenReturn(List.of());

            envio.reenviarPendientes();

            verify(bandeja).pendientes(EnvioDeEstadosPendientes.LOTE);
        }

        @Test
        void laLimpiezaBorraLosPublicadosViejos() {
            when(bandeja.limpiarPublicadosAnterioresA(org.mockito.ArgumentMatchers.any())).thenReturn(4);

            envio.limpiarPublicados();

            verify(bandeja).limpiarPublicadosAnterioresA(org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        }
    }
}
