package com.rabbit.notificaciones.negocio;

import static com.rabbit.pedidos.datos.model.MotivoEntregaFallida.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** El aviso al comercio cuando un pedido no se pudo entregar (ADR-041, ADR-045). */
class TextosAlComercioTest {

    @Test
    void unaVisitaFallidaDiceEnCualVaQueSigueYQueHacer() {
        String texto = TextosAlComercio.entregaFallida(7L, "PENDIENTE", DIRECCION_INCORRECTA,
                "Dirección incorrecta o inexistente: no existe el número 1234", true, 1, 3, true);
        assertTrue(texto.startsWith("Tu pedido #7 no se entregó en la visita 1 de 3"));
        assertTrue(texto.contains("no existe el número 1234"));
        assertTrue(texto.contains("Rabbit lo vuelve a intentar."));
        assertTrue(texto.contains("Confirmá la dirección con tu cliente"));
        assertTrue(texto.endsWith("Detalle en Mis pedidos → Ver visitas."));
    }

    @Test
    void avisaCuandoLaProximaEsLaUltima() {
        String texto = TextosAlComercio.entregaFallida(7L, "PENDIENTE", CLIENTE_AUSENTE, null, true, 1, 2, true);
        assertTrue(texto.contains("Queda una visita más: si falla, vuelve a tu comercio."));
        assertTrue(texto.contains("alguien lo tiene que recibir"));
    }

    @Test
    void siNoContoComoVisitaNoLoCulpaAlCliente() {
        String texto = TextosAlComercio.entregaFallida(7L, "PENDIENTE", PROBLEMA_DEL_REPARTIDOR, null, false, 0, 2, true);
        assertTrue(texto.startsWith("Tu pedido #7 se demora por un problema del reparto"));
        assertTrue(texto.contains("No cuenta como visita"));
        assertTrue(texto.endsWith("No tenés que hacer nada."));
    }

    @Test
    void todosTienenElMismoTonoYEntranEnLaColumnaDelPortal() {
        // notificaciones.mensaje es de 255; con un comentario de 200 se recorta, pero sin comentario tiene que entrar.
        for (var motivo : com.rabbit.pedidos.datos.model.MotivoEntregaFallida.values()) {
            for (String estado : new String[] { "PENDIENTE", "DEVUELTO_AL_COMERCIO" }) {
                for (boolean conto : new boolean[] { true, false }) {
                    String texto = TextosAlComercio.entregaFallida(123456L, estado, motivo, null, conto, 1, 2, true);
                    assertTrue(texto.length() <= 255, motivo + " " + estado + ": " + texto.length() + " " + texto);
                    assertTrue(texto.startsWith("Tu pedido #123456 "), texto);
                    assertFalse(texto.contains("(s)") || texto.contains("INCIDENCIA") || texto.contains(" NO "), texto);
                    assertTrue(texto.endsWith("."), texto);
                }
            }
        }
    }

    @Test
    void unPaqueteDanadoNoVuelveAlStock() {
        String texto = TextosAlComercio.entregaFallida(7L, "DEVUELTO_AL_COMERCIO", PAQUETE_DANADO,
                "Paquete dañado: caja aplastada", false, 0, 2, true);
        assertTrue(texto.startsWith("Tu pedido #7 no se entregó porque el paquete está dañado"));
        assertTrue(texto.contains("la mercadería no vuelve a tu stock"));
        assertTrue(texto.contains("caja aplastada"));
    }

    @Test
    void unRechazoODevolucionPorVisitasDevuelveElStockSiHabia() {
        assertTrue(TextosAlComercio.entregaFallida(7L, "DEVUELTO_AL_COMERCIO", RECHAZADO, null, true, 1, 2, true)
                .contains("porque el cliente lo rechazó"));
        String agotadas = TextosAlComercio.entregaFallida(7L, "DEVUELTO_AL_COMERCIO", CLIENTE_AUSENTE, null, true, 2, 2, true);
        assertTrue(agotadas.contains("después de 2 visitas"));
        assertTrue(agotadas.contains("Se anula el cobro y se repone tu stock."));
        assertTrue(agotadas.contains("Coordiná otra entrega con tu cliente."));
        // De un punto de picking no hay stock en Rabbit que devolver.
        String picking = TextosAlComercio.entregaFallida(7L, "DEVUELTO_AL_COMERCIO", CLIENTE_AUSENTE, null, true, 2, 2, false);
        assertFalse(picking.contains("stock"));
        assertTrue(picking.contains("Se anula el cobro."));
    }
}
