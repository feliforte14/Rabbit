package com.rabbit.pedidos.negocio;

import static com.rabbit.pedidos.datos.model.EstadoPedido.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Un pedido que ya salió a reparto no se cancela: solo queda entregarlo o que vuelva al comercio. */
class ReglaDeCancelacionTest {

    @Test
    void unPedidoEnCaminoNoSeCancelaYElMensajeLoExplica() {
        String motivo = ReglaDeCancelacion.motivoParaNoCancelar(7L, EN_CAMINO, true);
        assertNotNull(motivo);
        assertTrue(motivo.contains("ya salió a reparto"));
        assertTrue(motivo.contains("vuelve al comercio"));
    }

    @Test
    void siSalioYVolvioAlDepositoParaOtraVisitaTampoco() {
        assertNotNull(ReglaDeCancelacion.motivoParaNoCancelar(7L, PENDIENTE, true));
        assertNotNull(ReglaDeCancelacion.motivoParaNoCancelar(7L, CONFIRMADO, true));
    }

    @Test
    void antesDeSalirSeSiguePudiendoCancelar() {
        assertNull(ReglaDeCancelacion.motivoParaNoCancelar(7L, PENDIENTE, false));
        // Devuelto sin haber salido (no entraba en el vehículo): todavía se cancela.
        assertNull(ReglaDeCancelacion.motivoParaNoCancelar(7L, CONFIRMADO, false));
    }

    @Test
    void losTerminadosNoSeCancelan() {
        assertNotNull(ReglaDeCancelacion.motivoParaNoCancelar(7L, ENTREGADO, true));
        assertNotNull(ReglaDeCancelacion.motivoParaNoCancelar(7L, DEVUELTO_AL_COMERCIO, true));
        assertNotNull(ReglaDeCancelacion.motivoParaNoCancelar(7L, CANCELADO, false));
    }
}
