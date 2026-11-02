package com.rabbit.integracion.banco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.xml.ws.WebServiceException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integración SOAP con el banco legado (eje C): el Fault de negocio se traduce y no llega crudo, un
 * banco que no responde (timeout de 5 s) da NO_DISPONIBLE y cuenta para el circuit breaker, y con el
 * circuito abierto no se llama al banco.
 */
class BancoClientTest {

    private final BancoLegadoService banco = mock(BancoLegadoService.class);
    private final CircuitBreakerBanco circuito = new CircuitBreakerBanco();
    private BancoClient cliente;

    @BeforeEach
    void preparar() throws Exception {
        BancoClient real = new BancoClient();
        var campo = BancoClient.class.getDeclaredField("circuito");
        campo.setAccessible(true);
        campo.set(real, circuito);
        cliente = spy(real);
        doReturn(banco).when(cliente).puerto();
    }

    private static WebServiceException timeout() {
        return new WebServiceException("Read timed out", new SocketTimeoutException("Read timed out"));
    }

    @Test
    void unPagoAprobadoDevuelveElCodigoDeAutorizacion() throws Exception {
        when(banco.autorizarPago("PEDIDO-7", new BigDecimal("2500"))).thenReturn("AUT-123");

        ResultadoAutorizacion r = cliente.autorizar(7L, new BigDecimal("2500"));

        assertEquals(ResultadoAutorizacion.Estado.APROBADO, r.getEstado());
        assertEquals("AUT-123", r.getCodigoAutorizacion());
    }

    @Test
    void elFaultDeNegocioSeTraduceAUnRechazoConElMotivo() throws Exception {
        when(banco.autorizarPago(anyString(), any()))
                .thenThrow(new PagoRechazadoException("rechazado", new PagoRechazadoFaultInfo("Fondos insuficientes")));

        ResultadoAutorizacion r = cliente.autorizarReferencia("LIQ-1", BigDecimal.TEN);

        assertEquals(ResultadoAutorizacion.Estado.RECHAZADO, r.getEstado());
        assertEquals("Fondos insuficientes", r.getMotivo());
    }

    @Test
    void unRechazoNoCuentaComoFallaDelBanco() throws Exception {
        when(banco.autorizarPago(anyString(), any()))
                .thenThrow(new PagoRechazadoException("rechazado", null));
        for (int i = 0; i < 5; i++) {
            assertEquals("rechazado", cliente.autorizarReferencia("R", BigDecimal.ONE).getMotivo());
        }
        assertEquals(CircuitBreakerBanco.Estado.CERRADO, circuito.getEstado());
    }

    @Test
    void unBancoQueNoRespondeDaNoDisponibleYAlTerceroAbreElCircuito() throws Exception {
        when(banco.autorizarPago(anyString(), any())).thenThrow(timeout());

        for (int i = 0; i < 3; i++) {
            assertEquals(ResultadoAutorizacion.Estado.NO_DISPONIBLE,
                    cliente.autorizarReferencia("R", BigDecimal.ONE).getEstado());
        }
        assertEquals(CircuitBreakerBanco.Estado.ABIERTO, circuito.getEstado());

        // Abierto: contesta al instante, sin llamar al banco.
        assertEquals(ResultadoAutorizacion.Estado.NO_DISPONIBLE,
                cliente.autorizarReferencia("R", BigDecimal.ONE).getEstado());
        verify(banco, org.mockito.Mockito.times(3)).autorizarPago(anyString(), any());
    }

    @Test
    void laReversaDevuelveSiElBancoLaConfirmo() throws Exception {
        assertTrue(cliente.reversar("AUT-123"));
        verify(banco).reversarPago("AUT-123");

        doThrow(timeout()).when(banco).reversarPago("AUT-9");
        assertFalse(cliente.reversar("AUT-9"), "si el banco no responde, queda para hacerla a mano");
    }

    @Test
    void conElCircuitoAbiertoTampocoSeIntentaLaReversa() throws Exception {
        when(banco.autorizarPago(anyString(), any())).thenThrow(timeout());
        for (int i = 0; i < 3; i++) {
            cliente.autorizarReferencia("R", BigDecimal.ONE);
        }

        assertFalse(cliente.reversar("AUT-123"));
        verify(banco, never()).reversarPago(anyString());
    }
}
