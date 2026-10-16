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
    void soloVuelveAlComercioUnPedidoQueYaSalio() {
        // ADR-041: al agotar las visitas (o con un rechazo) desde EN_CAMINO.
        assertTrue(EN_CAMINO.puedePasarA(DEVUELTO_AL_COMERCIO));
        assertFalse(PENDIENTE.puedePasarA(DEVUELTO_AL_COMERCIO));
        assertFalse(CONFIRMADO.puedePasarA(DEVUELTO_AL_COMERCIO));
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
}
