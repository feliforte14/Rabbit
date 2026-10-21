package com.rabbit.pedidos.datos.model;

import static com.rabbit.pedidos.datos.model.EstadoPedido.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EstadoPedidoTest {

    @Test
    void caminoNormal() {
        assertTrue(PENDIENTE.puedePasarA(CONFIRMADO));
        assertTrue(CONFIRMADO.puedePasarA(EN_CAMINO));
        assertTrue(EN_CAMINO.puedePasarA(ENTREGADO));
    }

    @Test
    void seCancelaSoloAntesDeSalir() {
        assertTrue(PENDIENTE.puedePasarA(CANCELADO));
        assertTrue(CONFIRMADO.puedePasarA(CANCELADO));
        assertFalse(EN_CAMINO.puedePasarA(CANCELADO));
        assertFalse(ENTREGADO.puedePasarA(CANCELADO));
    }

    @Test
    void entregadoCanceladoYDevueltoSonFinales() {
        for (EstadoPedido destino : EstadoPedido.values()) {
            assertFalse(ENTREGADO.puedePasarA(destino));
            assertFalse(CANCELADO.puedePasarA(destino));
            assertFalse(DEVUELTO_AL_COMERCIO.puedePasarA(destino));
        }
    }

    @Test
    void vuelveAlComercioUnPedidoQueYaTieneRepartidor() {
        // ADR-041: al agotar las visitas (o con un rechazo) desde EN_CAMINO.
        assertTrue(EN_CAMINO.puedePasarA(DEVUELTO_AL_COMERCIO));
        // ADR-045: paquete dañado al retirarlo.
        assertTrue(CONFIRMADO.puedePasarA(DEVUELTO_AL_COMERCIO));
        assertFalse(PENDIENTE.puedePasarA(DEVUELTO_AL_COMERCIO));
    }

    @Test
    void noSeSaltanPasos() {
        assertFalse(PENDIENTE.puedePasarA(EN_CAMINO));
        assertFalse(PENDIENTE.puedePasarA(ENTREGADO));
        assertFalse(CONFIRMADO.puedePasarA(ENTREGADO));
    }

    @Test
    void unPedidoEnRepartoSePuedeDevolverAlDeposito() {
        assertTrue(CONFIRMADO.puedePasarA(PENDIENTE));
        assertTrue(EN_CAMINO.puedePasarA(PENDIENTE));
        assertFalse(ENTREGADO.puedePasarA(PENDIENTE));
        assertFalse(CANCELADO.puedePasarA(PENDIENTE));
    }

    @Test
    void unRetiroEnDepositoSeEntregaDesdeConfirmadoSinPasarPorReparto() {
        TipoEntrega retiro = TipoEntrega.RETIRO_EN_DEPOSITO;
        assertTrue(CONFIRMADO.puedePasarA(ENTREGADO, retiro));
        assertTrue(CONFIRMADO.puedePasarA(CANCELADO, retiro));
        assertFalse(CONFIRMADO.puedePasarA(EN_CAMINO, retiro));
        assertFalse(CONFIRMADO.puedePasarA(PENDIENTE, retiro));
    }

    @Test
    void aDomicilioLaMaquinaEsLaDeSiempre() {
        for (TipoEntrega tipo : new TipoEntrega[] {TipoEntrega.DOMICILIO, null}) {
            assertTrue(CONFIRMADO.puedePasarA(EN_CAMINO, tipo));
            assertFalse(CONFIRMADO.puedePasarA(ENTREGADO, tipo));
        }
    }

    @Test
    void elRetiroNoCambiaLosDemasEstados() {
        assertTrue(PENDIENTE.puedePasarA(CONFIRMADO, TipoEntrega.RETIRO_EN_DEPOSITO));
        assertFalse(PENDIENTE.puedePasarA(ENTREGADO, TipoEntrega.RETIRO_EN_DEPOSITO));
        assertFalse(ENTREGADO.puedePasarA(CONFIRMADO, TipoEntrega.RETIRO_EN_DEPOSITO));
    }
}
