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
    void porDefectoElCorteEsALas21() {
        assertEquals(LocalTime.of(21, 0), HoraDeCorte.configurada());
        assertTrue(HoraDeCorte.permiteAsignar(HoraDeCorte.configurada(), LocalTime.of(20, 59)));
        assertFalse(HoraDeCorte.permiteAsignar(HoraDeCorte.configurada(), LocalTime.of(21, 0)));
    }

    @Test
    void conNingunoNoHayCorteYSeAsignaACualquierHora() {
        System.setProperty(HoraDeCorte.PROPIEDAD, "ninguno");
        assertNull(HoraDeCorte.configurada());
        assertTrue(HoraDeCorte.permiteAsignar(null, LocalTime.of(23, 59)));
    }

    @Test
    void seLeeDeLaPropiedadSinTocarCodigo() {
        System.setProperty(HoraDeCorte.PROPIEDAD, "9:00");
        assertEquals(NUEVE, HoraDeCorte.configurada());
        System.setProperty(HoraDeCorte.PROPIEDAD, " 18:30 ");
        assertEquals(LocalTime.of(18, 30), HoraDeCorte.configurada());
    }

    @Test
    void unValorInvalidoUsaElDePorDefecto() {
        assertEquals(HoraDeCorte.POR_DEFECTO, HoraDeCorte.interpretar("nueve"));
        assertEquals(HoraDeCorte.POR_DEFECTO, HoraDeCorte.interpretar("25:00"));
        assertEquals(HoraDeCorte.POR_DEFECTO, HoraDeCorte.interpretar(""));
    }
}
