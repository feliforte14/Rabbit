package com.rabbit.repartidores.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HoraDeCorteTest {

    private static final LocalTime NUEVE = LocalTime.of(9, 0);

    @AfterEach
    void limpiar() {
        System.clearProperty(HoraDeCorte.PROPIEDAD);
    }

    @Test
    void antesDelCorteSeSumanPedidosALaHojaDelDia() {
        assertTrue(HoraDeCorte.permiteAsignar(NUEVE, LocalTime.of(8, 59)));
    }

    @Test
    void desdeLaHoraDeCorteYaNoAunqueHayaLugar() {
        assertFalse(HoraDeCorte.permiteAsignar(NUEVE, LocalTime.of(9, 0)));
        assertFalse(HoraDeCorte.permiteAsignar(NUEVE, LocalTime.of(17, 30)));
    }

    @Test
    void sinCorteConfiguradoSeAsignaACualquierHora() {
        assertTrue(HoraDeCorte.permiteAsignar(null, LocalTime.of(23, 59)));
        assertNull(HoraDeCorte.configurada());
    }

    @Test
    void seLeeDeLaPropiedadSinTocarCodigo() {
        System.setProperty(HoraDeCorte.PROPIEDAD, "9:00");
        assertEquals(NUEVE, HoraDeCorte.configurada());
        System.setProperty(HoraDeCorte.PROPIEDAD, " 18:30 ");
        assertEquals(LocalTime.of(18, 30), HoraDeCorte.configurada());
    }

    @Test
    void unValorInvalidoEsSinCorte() {
        assertNull(HoraDeCorte.interpretar("nueve"));
        assertNull(HoraDeCorte.interpretar("25:00"));
        assertNull(HoraDeCorte.interpretar(""));
    }
}
