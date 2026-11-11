package com.rabbit.inventario.negocio;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReglasDeStockTest {

    @Test
    void laCantidadPuedeBajarHastaLoReservado() {
        assertNull(ReglasDeStock.motivoParaNoCambiarCantidad(10, 3));
        assertNull(ReglasDeStock.motivoParaNoCambiarCantidad(3, 3));
        assertNull(ReglasDeStock.motivoParaNoCambiarCantidad(0, 0));
    }

    @Test
    void noQuedaPorDebajoDeLoReservadoNiNegativa() {
        String motivo = ReglasDeStock.motivoParaNoCambiarCantidad(2, 3);
        assertNotNull(motivo);
        assertTrue(motivo.contains("3 unidades reservadas"), motivo);
        assertTrue(motivo.contains("El mínimo es 3"), motivo);
        assertTrue(ReglasDeStock.motivoParaNoCambiarCantidad(0, 1).contains("1 unidad reservada"));
        assertNotNull(ReglasDeStock.motivoParaNoCambiarCantidad(-1, 0));
    }

    @Test
    void seEliminaSoloSinUnidadesReservadas() {
        assertNull(ReglasDeStock.motivoParaNoEliminar(0));
        assertTrue(ReglasDeStock.motivoParaNoEliminar(2).contains("2 unidades reservadas"));
    }
}
