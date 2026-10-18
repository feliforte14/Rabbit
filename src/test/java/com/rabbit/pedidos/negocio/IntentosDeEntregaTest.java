package com.rabbit.pedidos.negocio;

import static com.rabbit.pedidos.datos.model.MotivoEntregaFallida.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Cuándo una entrega fallida cuenta como visita y cuándo el pedido vuelve al comercio (ADR-041). */
class IntentosDeEntregaTest {

    @AfterEach
    void limpiar() {
        System.clearProperty(IntentosDeEntrega.PROPIEDAD);
    }

    @Test
    void laPrimeraVisitaFallidaVuelveALaEspera() {
        IntentosDeEntrega.Desenlace d = IntentosDeEntrega.evaluar(true, CLIENTE_AUSENTE, 0, 2);
        assertEquals(1, d.intentos());
        assertFalse(d.devolverAlComercio());
    }

    @Test
    void alAgotarLasVisitasVuelveAlComercio() {
        IntentosDeEntrega.Desenlace d = IntentosDeEntrega.evaluar(true, DIRECCION_INCORRECTA, 1, 2);
        assertEquals(2, d.intentos());
        assertTrue(d.devolverAlComercio());
    }

    @Test
    void unRechazoVuelveAlComercioSinEsperarAgotarLasVisitas() {
        IntentosDeEntrega.Desenlace d = IntentosDeEntrega.evaluar(true, RECHAZADO, 0, 3);
        assertEquals(1, d.intentos());
        assertTrue(d.devolverAlComercio());
    }

    @Test
    void sinHaberSalidoNoGastaUnaVisita() {
        // Devuelto desde CONFIRMADO: todavía no fue a la dirección.
        IntentosDeEntrega.Desenlace d = IntentosDeEntrega.evaluar(false, CLIENTE_AUSENTE, 1, 2);
        assertEquals(1, d.intentos());
        assertFalse(d.devolverAlComercio());
    }

    @Test
    void noEntrarEnElVehiculoNoEsUnaVisita() {
        IntentosDeEntrega.Desenlace d = IntentosDeEntrega.evaluar(true, NO_ENTRA_EN_VEHICULO, 1, 2);
        assertEquals(1, d.intentos());
        assertFalse(d.devolverAlComercio());
    }

    @Test
    void unPaqueteDanadoVuelveAlComercioSinGastarVisita() {
        // ADR-045: aunque lo detecte al retirarlo (sin haber salido).
        IntentosDeEntrega.Desenlace alRetirar = IntentosDeEntrega.evaluar(false, PAQUETE_DANADO, 0, 2);
        assertEquals(0, alRetirar.intentos());
        assertTrue(alRetirar.devolverAlComercio());
        IntentosDeEntrega.Desenlace enCamino = IntentosDeEntrega.evaluar(true, PAQUETE_DANADO, 1, 2);
        assertEquals(1, enCamino.intentos());
        assertTrue(enCamino.devolverAlComercio());
    }

    @Test
    void unProblemaDelRepartidorNoLeCuentaVisitaAlCliente() {
        // Aunque ya estuviera en camino y fuera su última visita.
        IntentosDeEntrega.Desenlace d = IntentosDeEntrega.evaluar(true, PROBLEMA_DEL_REPARTIDOR, 1, 2);
        assertEquals(1, d.intentos());
        assertFalse(d.devolverAlComercio());
    }

    @Test
    void unRechazoSinHaberSalidoNoDevuelve() {
        // No se puede rechazar algo que no llegó: vuelve a la espera.
        IntentosDeEntrega.Desenlace d = IntentosDeEntrega.evaluar(false, RECHAZADO, 0, 2);
        assertEquals(0, d.intentos());
        assertFalse(d.devolverAlComercio());
    }

    @Test
    void losMotivosNuevosDelClienteCuentanComoVisita() {
        for (var motivo : new com.rabbit.pedidos.datos.model.MotivoEntregaFallida[] {
                NADIE_HABILITADO, DOMICILIO_CERRADO, ZONA_PELIGROSA }) {
            IntentosDeEntrega.Desenlace d = IntentosDeEntrega.evaluar(true, motivo, 0, 2);
            assertEquals(1, d.intentos(), motivo.name());
            assertFalse(d.devolverAlComercio(), motivo.name());
        }
    }

    @Test
    void elMaximoPorDefectoSonDosYSeConfigura() {
        assertEquals(2, IntentosDeEntrega.maximo());
        System.setProperty(IntentosDeEntrega.PROPIEDAD, " 3 ");
        assertEquals(3, IntentosDeEntrega.maximo());
    }

    @Test
    void unMaximoInvalidoUsaElDePorDefecto() {
        assertEquals(IntentosDeEntrega.POR_DEFECTO, IntentosDeEntrega.interpretar("tres"));
        assertEquals(IntentosDeEntrega.POR_DEFECTO, IntentosDeEntrega.interpretar("0"));
        assertEquals(IntentosDeEntrega.POR_DEFECTO, IntentosDeEntrega.interpretar("11"));
    }
}
