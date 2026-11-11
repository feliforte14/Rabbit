package com.rabbit.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TarifaMensualTest {

    @AfterEach
    void limpiar() {
        System.clearProperty("rabbit.tarifa.abono");
        System.clearProperty("rabbit.tarifa.por-envio");
        System.clearProperty("rabbit.tarifa.por-kilo");
        System.getProperties().stringPropertyNames().stream()
                .filter(p -> p.startsWith("rabbit.tarifa.almacenamiento.")).forEach(System::clearProperty);
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

    // ---- almacenamiento (ADR-059) ----

    private static TarifaMensual conAlmacenamiento(String pequeno, String mediano, String grande) {
        return new TarifaMensual(new BigDecimal("15000"), new BigDecimal("1200"), new BigDecimal("80"),
                new BigDecimal(pequeno), new BigDecimal(mediano), new BigDecimal(grande));
    }

    @Test
    void elAlmacenamientoSeCobraPorUnidadYPorDiaSegunElTamano() {
        TarifaMensual t = conAlmacenamiento("10", "30", "90");
        // 100 unidades pequeñas durante 30 días son 3.000 unidades-día: $30.000 (el ejemplo de la propuesta)
        assertEquals(new BigDecimal("30000.00"), t.cargoAlmacenamiento(3000, 0, 0));
        // Cada categoría con su precio: 100 x 10 + 50 x 30 + 10 x 90 = 3.400
        assertEquals(new BigDecimal("3400.00"), t.cargoAlmacenamiento(100, 50, 10));
    }

    @Test
    void elTotalSumaElAlmacenamientoAlRepartoYSinAlmacenamientoEsElDeSiempre() {
        TarifaMensual t = conAlmacenamiento("10", "30", "90");
        BigDecimal reparto = t.total(10, new BigDecimal("5"));               // 15.000 + 12.000 + 400
        assertEquals(new BigDecimal("27400.00"), reparto);
        assertEquals(reparto.add(new BigDecimal("3400.00")), t.total(10, new BigDecimal("5"), 100, 50, 10));
        assertEquals(reparto, t.total(10, new BigDecimal("5"), 0, 0, 0), "sin unidades-día no suma nada");
    }

    @Test
    void sinPreciosDefinidosElAlmacenamientoNoCuesta() {
        TarifaMensual sinPrecios = new TarifaMensual(new BigDecimal("15000"), new BigDecimal("1200"), new BigDecimal("80"));
        assertFalse(sinPrecios.cobraAlmacenamiento());
        assertEquals(new BigDecimal("0.00"), sinPrecios.cargoAlmacenamiento(3000, 500, 100));
        assertEquals(sinPrecios.total(2, BigDecimal.ONE), sinPrecios.total(2, BigDecimal.ONE, 3000, 500, 100));
    }

    @Test
    void cobraAlmacenamientoConQueUnaCategoriaTengaPrecio() {
        assertTrue(conAlmacenamiento("0", "0", "5").cobraAlmacenamiento());
        assertFalse(conAlmacenamiento("0", "0", "0").cobraAlmacenamiento());
    }

    @Test
    void laTarifaVigenteCobraAlmacenamientoConLosPreciosPorDefecto() {
        System.getProperties().stringPropertyNames().stream()
                .filter(p -> p.startsWith("rabbit.tarifa.almacenamiento.")).forEach(System::clearProperty);
        TarifaMensual t = TarifaMensual.vigente();
        assertTrue(t.cobraAlmacenamiento());
        assertEquals(new BigDecimal("10"), t.almacenamientoPequeno());
        assertEquals(new BigDecimal("30"), t.almacenamientoMediano());
        assertEquals(new BigDecimal("90"), t.almacenamientoGrande());
    }

    @Test
    void conLosTresPreciosEnCeroElAlmacenamientoNoSeCobra() {
        System.setProperty("rabbit.tarifa.almacenamiento.pequeno", "0");
        System.setProperty("rabbit.tarifa.almacenamiento.mediano", "0");
        System.setProperty("rabbit.tarifa.almacenamiento.grande", "0");
        assertFalse(TarifaMensual.vigente().cobraAlmacenamiento());
    }

    @Test
    void losPreciosSalenDeSystemProperties() {
        try {
            System.setProperty("rabbit.tarifa.almacenamiento.pequeno", "10");
            System.setProperty("rabbit.tarifa.almacenamiento.mediano", "30");
            System.setProperty("rabbit.tarifa.almacenamiento.grande", "90");
            TarifaMensual t = TarifaMensual.vigente();
            assertTrue(t.cobraAlmacenamiento());
            assertEquals(new BigDecimal("10"), t.almacenamientoPequeno());
            assertEquals(new BigDecimal("90"), t.almacenamientoGrande());
        } finally {
            System.clearProperty("rabbit.tarifa.almacenamiento.pequeno");
            System.clearProperty("rabbit.tarifa.almacenamiento.mediano");
            System.clearProperty("rabbit.tarifa.almacenamiento.grande");
        }
    }

    // ---- Días gratis y recargo por stock antiguo ----

    private static TarifaMensual conRecargo(String porcentaje) {
        return new TarifaMensual(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10"),
                new BigDecimal("30"), new BigDecimal("90"), new BigDecimal(porcentaje));
    }

    @Test
    void lasUnidadesGratisNoSeCobran() {
        AlmacenamientoDelMes a = new AlmacenamientoDelMes(100, 0, 0, 40, 0, 0, 0, 0, 0);
        // (100 - 40) x 10
        assertEquals(new BigDecimal("600.00"), conRecargo("50").cargoAlmacenamiento(a));
        assertEquals(new BigDecimal("400.00"), conRecargo("50").ahorroPorDiasGratis(a));
    }

    @Test
    void elStockAntiguoSeCobraConElRecargoEncima() {
        AlmacenamientoDelMes a = new AlmacenamientoDelMes(100, 0, 0, 0, 0, 0, 20, 0, 0);
        // 100 x 10 + 20 x 10 x 50 %
        assertEquals(new BigDecimal("1100.00"), conRecargo("50").cargoAlmacenamiento(a));
        assertEquals(new BigDecimal("100.00"), conRecargo("50").cargoPorStockAntiguo(a));
    }

    @Test
    void combinaGratisYRecargoEnCadaTamano() {
        AlmacenamientoDelMes a = new AlmacenamientoDelMes(100, 10, 2, 10, 0, 1, 20, 4, 0);
        // pequeño: 90 x 10 + 20 x 5 = 1000 | mediano: 10 x 30 + 4 x 15 = 360 | grande: 1 x 90 = 90
        assertEquals(new BigDecimal("1450.00"), conRecargo("50").cargoAlmacenamiento(a));
        assertEquals(new BigDecimal("1450.00"), conRecargo("50").total(0, BigDecimal.ZERO, a));
    }

    @Test
    void losBeneficiosNuncaSuperanAlTotalDelTamano() {
        AlmacenamientoDelMes a = new AlmacenamientoDelMes(10, 0, 0, 50, 0, 0, 50, 0, 0);
        assertEquals(new BigDecimal("0.00"), conRecargo("50").cargoAlmacenamiento(a));
    }

    @Test
    void sinRecargoConfiguradoElStockAntiguoNoCuesta() {
        AlmacenamientoDelMes a = new AlmacenamientoDelMes(100, 0, 0, 0, 0, 0, 100, 0, 0);
        assertEquals(new BigDecimal("1000.00"), conRecargo("0").cargoAlmacenamiento(a));
        assertEquals(new BigDecimal("0.00"), conRecargo("0").cargoPorStockAntiguo(a));
        // Los constructores viejos no tienen recargo.
        TarifaMensual vieja = new TarifaMensual(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
        assertEquals(new BigDecimal("1000.00"), vieja.cargoAlmacenamiento(a));
    }

    @Test
    void elRecargoVigenteEsCincuentaPorDefectoYSeConfigura() {
        assertEquals(0, new BigDecimal("50").compareTo(TarifaMensual.vigente().recargoStockAntiguo()));
        System.setProperty("rabbit.tarifa.almacenamiento.recargo-stock-antiguo", "25");
        assertEquals(0, new BigDecimal("25").compareTo(TarifaMensual.vigente().recargoStockAntiguo()));
        System.setProperty("rabbit.tarifa.almacenamiento.recargo-stock-antiguo", "-1");
        assertEquals(0, new BigDecimal("50").compareTo(TarifaMensual.vigente().recargoStockAntiguo()));
    }

    @Test
    void elTotalViejoPorUnidadesSigueFuncionando() {
        assertEquals(new BigDecimal("1000.00"), conRecargo("50").total(0, BigDecimal.ZERO, 100, 0, 0));
    }
}
