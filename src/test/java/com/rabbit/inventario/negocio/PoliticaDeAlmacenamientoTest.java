package com.rabbit.inventario.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.inventario.datos.model.ItemInventario;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PoliticaDeAlmacenamientoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    @AfterEach
    void limpiar() {
        System.clearProperty(PoliticaDeAlmacenamiento.PROPIEDAD_DIAS_GRATIS);
        System.clearProperty(PoliticaDeAlmacenamiento.PROPIEDAD_DIAS_RECARGO);
    }

    private static ItemInventario item(LocalDate ingreso, LocalDate movimiento) {
        ItemInventario i = new ItemInventario();
        i.setFechaUltimoIngreso(ingreso);
        i.setFechaUltimoMovimiento(movimiento);
        return i;
    }

    @Test
    void losValoresPorDefectoSonSieteYCientoVeinte() {
        assertEquals(7, PoliticaDeAlmacenamiento.diasGratis());
        assertEquals(120, PoliticaDeAlmacenamiento.diasParaRecargo());
    }

    @Test
    void laPropertyCambiaElValorYLosInvalidosSeIgnoran() {
        System.setProperty(PoliticaDeAlmacenamiento.PROPIEDAD_DIAS_GRATIS, " 15 ");
        System.setProperty(PoliticaDeAlmacenamiento.PROPIEDAD_DIAS_RECARGO, "90");
        assertEquals(15, PoliticaDeAlmacenamiento.diasGratis());
        assertEquals(90, PoliticaDeAlmacenamiento.diasParaRecargo());

        System.setProperty(PoliticaDeAlmacenamiento.PROPIEDAD_DIAS_GRATIS, "-3");
        System.setProperty(PoliticaDeAlmacenamiento.PROPIEDAD_DIAS_RECARGO, "mucho");
        assertEquals(7, PoliticaDeAlmacenamiento.diasGratis());
        assertEquals(120, PoliticaDeAlmacenamiento.diasParaRecargo());
    }

    @Test
    void esGratisSoloDentroDeLaVentanaDesdeElIngreso() {
        assertTrue(PoliticaDeAlmacenamiento.esGratis(item(HOY, HOY), HOY));
        assertTrue(PoliticaDeAlmacenamiento.esGratis(item(HOY.minusDays(6), HOY), HOY));
        assertFalse(PoliticaDeAlmacenamiento.esGratis(item(HOY.minusDays(7), HOY), HOY));
        // Un ingreso "en el futuro" no es gratis.
        assertFalse(PoliticaDeAlmacenamiento.esGratis(item(HOY.plusDays(1), HOY), HOY));
    }

    @Test
    void sinFechaDeIngresoNoHayDiasGratis() {
        assertFalse(PoliticaDeAlmacenamiento.esGratis(item(null, HOY), HOY));
    }

    @Test
    void conCeroDiasGratisNuncaEsGratis() {
        System.setProperty(PoliticaDeAlmacenamiento.PROPIEDAD_DIAS_GRATIS, "0");
        assertFalse(PoliticaDeAlmacenamiento.esGratis(item(HOY, HOY), HOY));
    }

    @Test
    void esAntiguoDesdeElDiaCientoVeinteSinMovimiento() {
        assertFalse(PoliticaDeAlmacenamiento.esAntiguo(item(null, HOY.minusDays(119)), HOY));
        assertTrue(PoliticaDeAlmacenamiento.esAntiguo(item(null, HOY.minusDays(120)), HOY));
        assertTrue(PoliticaDeAlmacenamiento.esAntiguo(item(HOY.minusDays(300), HOY.minusDays(300)), HOY));
    }

    @Test
    void sinFechaDeMovimientoNoHayRecargoRetroactivo() {
        assertFalse(PoliticaDeAlmacenamiento.esAntiguo(item(null, null), HOY));
    }

    @Test
    void conCeroDiasDeRecargoNuncaEsAntiguo() {
        System.setProperty(PoliticaDeAlmacenamiento.PROPIEDAD_DIAS_RECARGO, "0");
        assertFalse(PoliticaDeAlmacenamiento.esAntiguo(item(null, HOY.minusDays(999)), HOY));
    }

    @Test
    void siEstaEnDiasGratisNoEsAntiguoAunqueElMovimientoSeaViejo() {
        assertFalse(PoliticaDeAlmacenamiento.esAntiguo(item(HOY.minusDays(2), HOY.minusDays(500)), HOY));
    }

    @Test
    void registrarIngresoYSalidaActualizanLasFechas() {
        ItemInventario i = new ItemInventario();
        assertNull(i.getFechaUltimoIngreso());
        i.registrarIngreso(HOY);
        assertEquals(HOY, i.getFechaUltimoIngreso());
        assertEquals(HOY, i.getFechaUltimoMovimiento());
        i.registrarSalida(HOY.plusDays(3));
        assertEquals(HOY, i.getFechaUltimoIngreso());
        assertEquals(HOY.plusDays(3), i.getFechaUltimoMovimiento());
    }
}
