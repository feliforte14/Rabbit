package com.rabbit.repartidores.negocio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CapacidadRepartidorTest {

    private static BigDecimal kg(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void sinCapacidadCargadaLlevaUnPedidoALaVez() {
        assertTrue(CapacidadRepartidor.entra(null, null, null, 0, 0, kg("500"), 40));
        assertFalse(CapacidadRepartidor.entra(null, null, kg("1"), 1, 1, kg("1"), 1));
        assertFalse(CapacidadRepartidor.tieneLugar(null, null, kg("1"), 1, 1));
    }

    @Test
    void conCapacidadSumaHastaElLimiteDeKilos() {
        assertTrue(CapacidadRepartidor.entra(kg("100"), null, kg("60"), 3, 2, kg("40"), 1));
        assertFalse(CapacidadRepartidor.entra(kg("100"), null, kg("60"), 3, 2, kg("40.001"), 1));
    }

    @Test
    void conCapacidadSumaHastaElLimiteDeBultos() {
        assertTrue(CapacidadRepartidor.entra(null, 10, null, 7, 2, null, 3));
        assertFalse(CapacidadRepartidor.entra(null, 10, null, 7, 2, null, 4));
    }

    @Test
    void tieneQueEntrarEnLosDosLimites() {
        assertFalse(CapacidadRepartidor.entra(kg("100"), 10, kg("10"), 9, 3, kg("5"), 2));
        assertFalse(CapacidadRepartidor.entra(kg("100"), 10, kg("99"), 2, 1, kg("5"), 1));
        assertTrue(CapacidadRepartidor.entra(kg("100"), 10, kg("90"), 8, 3, kg("10"), 2));
    }

    @Test
    void unPedidoDePesoDesconocidoSoloSeControlaPorBultos() {
        assertTrue(CapacidadRepartidor.entra(kg("100"), 10, kg("100"), 2, 1, null, 1));
        assertFalse(CapacidadRepartidor.entra(kg("100"), 10, kg("100"), 10, 4, null, 1));
    }

    @Test
    void sinLugarCuandoLlegoAAlgunLimite() {
        assertTrue(CapacidadRepartidor.tieneLugar(kg("100"), 10, kg("50"), 5, 2));
        assertFalse(CapacidadRepartidor.tieneLugar(kg("100"), 10, kg("100"), 5, 2));
        assertFalse(CapacidadRepartidor.tieneLugar(kg("100"), 10, kg("50"), 10, 2));
    }
}
