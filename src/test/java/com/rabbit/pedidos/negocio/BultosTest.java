package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** ADR-056: cantidad de bultos (etiquetas con QR) de un pedido. */
class BultosTest {

    @Test
    void sinInformarEsUnBulto() {
        assertEquals(1, Bultos.validar(null));
    }

    @Test
    void deUnoANoventaYNueveSeAcepta() {
        assertEquals(1, Bultos.validar(1));
        assertEquals(3, Bultos.validar(3));
        assertEquals(99, Bultos.validar(99));
    }

    @Test
    void fueraDeRangoSeRechaza() {
        for (int mal : new int[] {0, -1, 100, 1000}) {
            ValidacionException e = assertThrows(ValidacionException.class, () -> Bultos.validar(mal), "bultos " + mal);
            assertTrue(e.getMessage().contains("entre 1 y 99"));
        }
    }
}
