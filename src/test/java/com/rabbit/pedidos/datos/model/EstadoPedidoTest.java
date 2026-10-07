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
    void entregadoYCanceladoSonFinales() {
        for (EstadoPedido destino : EstadoPedido.values()) {
            assertFalse(ENTREGADO.puedePasarA(destino));
            assertFalse(CANCELADO.puedePasarA(destino));
        }
    }

    @Test
    void noSeSaltanPasos() {
        assertFalse(PENDIENTE.puedePasarA(EN_CAMINO));
        assertFalse(PENDIENTE.puedePasarA(ENTREGADO));
        assertFalse(CONFIRMADO.puedePasarA(ENTREGADO));
    }
}
