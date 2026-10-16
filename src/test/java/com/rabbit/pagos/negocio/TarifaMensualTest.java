package com.rabbit.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TarifaMensualTest {

    @AfterEach
    void limpiar() {
        System.clearProperty("rabbit.tarifa.abono");
        System.clearProperty("rabbit.tarifa.por-envio");
        System.clearProperty("rabbit.tarifa.por-kilo");
    }

    @Test
    void sumaAbonoEnviosYKilos() {
        TarifaMensual t = new TarifaMensual(new BigDecimal("15000"), new BigDecimal("1200"), new BigDecimal("80"));
        assertEquals(new BigDecimal("15000.00"), t.total(0, BigDecimal.ZERO));
        // 15000 + 3 x 1200 + 12.5 x 80
        assertEquals(new BigDecimal("19600.00"), t.total(3, new BigDecimal("12.5")));
        assertEquals(new BigDecimal("15000.00"), t.total(0, null));
    }

    @Test
    void redondeaACentavos() {
        TarifaMensual t = new TarifaMensual(new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("80.5"));
        assertEquals(new BigDecimal("0.81"), t.cargoKilos(new BigDecimal("0.01")));
    }

    @Test
    void tomaLosValoresDeLasSystemPropertiesYIgnoraLosInvalidos() {
        System.setProperty("rabbit.tarifa.abono", "20000");
        System.setProperty("rabbit.tarifa.por-envio", "no-es-un-numero");
        System.setProperty("rabbit.tarifa.por-kilo", "-5");
        TarifaMensual t = TarifaMensual.vigente();
        assertEquals(new BigDecimal("20000"), t.abono());
        assertEquals(new BigDecimal("1200"), t.porEnvio());
        assertEquals(new BigDecimal("80"), t.porKilo());
    }
}
