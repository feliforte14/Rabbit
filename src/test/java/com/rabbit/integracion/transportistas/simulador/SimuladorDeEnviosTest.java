package com.rabbit.integracion.transportistas.simulador;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.integracion.transportistas.simulador.SimuladorDeEnvios.ResultadoCancelacion;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimuladorDeEnviosTest {

    private SimuladorDeEnvios simulador;

    @BeforeEach
    void preparar() {
        System.setProperty("rabbit.transportista.simulador.segundos", "1");
        simulador = new SimuladorDeEnvios("Prueba", "PR-");
    }

    @AfterEach
    void limpiar() {
        System.clearProperty("rabbit.transportista.simulador.segundos");
    }

    @Test
    void rechazaSinBultosOMasDeCincuenta() {
        assertNotNull(simulador.motivoDeRechazo(0));
        assertNotNull(simulador.motivoDeRechazo(51));
        assertNull(simulador.motivoDeRechazo(50));
    }

    @Test
    void cotizaPorBultoConRecargoPorCobrarAlEntregar() {
        assertEquals(0, new BigDecimal("3200").compareTo(simulador.precio(2, false)));
        assertEquals(0, new BigDecimal("3700").compareTo(simulador.precio(2, true)));
    }

    @Test
    void avanzaConElTiempo() throws InterruptedException {
        String codigo = simulador.registrar("PEDIDO-1", "Av. Corrientes 1234");
        assertEquals(SimuladorDeEnvios.Estado.SOLICITADO, simulador.estado(codigo));
        Thread.sleep(2100);
        assertEquals(SimuladorDeEnvios.Estado.ENTREGADO, simulador.estado(codigo));
    }

    @Test
    void cancelarAntesDeEntregarYDeNuevoEsIdempotente() {
        String codigo = simulador.registrar("PEDIDO-1", "Av. Corrientes 1234");
        assertEquals(ResultadoCancelacion.CANCELADO, simulador.cancelar(codigo));
        assertEquals(ResultadoCancelacion.CANCELADO, simulador.cancelar(codigo));
        assertEquals(SimuladorDeEnvios.Estado.CANCELADO, simulador.estado(codigo));
    }

    // Hallazgo 1 de la revisión: lo entregado no se puede dar por cancelado.
    @Test
    void loEntregadoNoSeCancela() throws InterruptedException {
        String codigo = simulador.registrar("PEDIDO-1", "Av. Corrientes 1234");
        Thread.sleep(2100);
        assertEquals(ResultadoCancelacion.YA_ENTREGADO, simulador.cancelar(codigo));
        assertEquals(SimuladorDeEnvios.Estado.ENTREGADO, simulador.estado(codigo));
    }

    @Test
    void codigoInexistente() {
        assertEquals(ResultadoCancelacion.INEXISTENTE, simulador.cancelar("PR-NO-EXISTE"));
        assertNull(simulador.estado("PR-NO-EXISTE"));
    }
}
