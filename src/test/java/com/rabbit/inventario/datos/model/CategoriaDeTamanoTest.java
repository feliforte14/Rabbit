package com.rabbit.inventario.datos.model;

import static com.rabbit.inventario.datos.model.CategoriaDeTamano.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ADR-059: la categoría de tamaño se deduce del peso y las medidas; manda la más grande. */
class CategoriaDeTamanoTest {

    private static BigDecimal n(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }

    private static CategoriaDeTamano de(String peso, String alto, String ancho, String largo) {
        return CategoriaDeTamano.de(n(peso), n(alto), n(ancho), n(largo));
    }

    @AfterEach
    void limpiar() {
        System.getProperties().stringPropertyNames().stream()
                .filter(p -> p.startsWith("rabbit.almacenamiento.")).forEach(System::clearProperty);
    }

    // ---- ejemplos de la propuesta ----

    @Test
    void unaYerbaDeUnKiloEsPequena() {
        assertEquals(PEQUENO, de("1", "25", "10", "7"));   // 1,75 L
    }

    @Test
    void unMicroondasEsGrandePorVolumen() {
        assertEquals(GRANDE, de("8", "50", "40", "30"));   // 60 L: el peso solo daría mediano
    }

    @Test
    void unAlmohadonLivianoPeroVoluminosoEsMediano() {
        assertEquals(MEDIANO, de("0.3", "55", "40", "20")); // 44 L, lado 55: pesa 300 g y no es pequeño
    }

    // ---- bordes: los límites son "hasta" (inclusivos) ----

    @Test
    void elBordeDeCadaDatoEsInclusivo() {
        assertEquals(PEQUENO, de("1", "1", "1", "1"), "peso 1 kg justo");
        assertEquals(MEDIANO, de("1.001", "1", "1", "1"), "un gramo más");
        assertEquals(PEQUENO, de("0.5", "25", "10", "10"), "lado 25 justo, 2,5 L");
        assertEquals(MEDIANO, de("0.5", "25.1", "10", "10"), "lado 25,1");
        assertEquals(PEQUENO, de("0.5", "15", "20", "10"), "volumen 3 L justo");
        assertEquals(MEDIANO, de("0.5", "15", "20", "10.1"), "volumen apenas más de 3 L");
        assertEquals(MEDIANO, de("10", "60", "10", "10"), "peso 10 y lado 60 justos");
        assertEquals(GRANDE, de("10.001", "10", "10", "10"));
        assertEquals(GRANDE, de("50", "150", "20", "20"), "peso 50 y lado 150 justos (60 L)");
        assertEquals(GRANDE, de("1", "100", "100", "50"), "volumen 500 L justo");
        assertEquals(FUERA_DE_RANGO, de("1", "100", "100", "50.1"), "apenas más de 500 L");
    }

    @Test
    void masQueLoGrandeEstaFueraDeRango() {
        assertEquals(FUERA_DE_RANGO, de("50.1", "10", "10", "10"));
        assertEquals(FUERA_DE_RANGO, de("1", "150.1", "10", "10"));
        assertEquals(FUERA_DE_RANGO, de("1", "100", "100", "51"), "510 L");
    }

    @Test
    void mandaLaCategoriaMasGrandeDeLosTresDatos() {
        assertEquals(GRANDE, de("40", "10", "10", "10"), "pesado y chico: grande por peso");
        assertEquals(GRANDE, de("0.1", "120", "5", "5"), "liviano pero larguísimo: grande por lado");
    }

    // ---- sin medidas ----

    @Test
    void sinMedidasSeCuentaComoMediano() {
        assertEquals(MEDIANO, de("0.2", null, null, null));
        assertEquals(MEDIANO, de(null, null, null, null));
    }

    @Test
    void conMedidasIncompletasSeTrataComoSinMedidas() {
        assertEquals(MEDIANO, de("0.2", "10", null, "10"));
        assertFalse(tieneMedidas(n("10"), null, n("10")));
        assertTrue(tieneMedidas(n("10"), n("5"), n("10")));
    }

    @Test
    void sinMedidasPeroMuyPesadoSubePorElPeso() {
        assertEquals(GRANDE, de("30", null, null, null));
        assertEquals(FUERA_DE_RANGO, de("60", null, null, null));
    }

    // ---- límites configurables ----

    @Test
    void losLimitesSalenDeSystemProperties() {
        assertEquals(PEQUENO, de("1", "10", "10", "10"));
        System.setProperty("rabbit.almacenamiento.pequeno.peso-kg", "0.5");
        assertEquals(MEDIANO, de("1", "10", "10", "10"), "con el límite en 0,5 kg, 1 kg ya es mediano");
    }

    @Test
    void unValorInvalidoSeIgnora() {
        System.setProperty("rabbit.almacenamiento.pequeno.peso-kg", "mucho");
        assertEquals(PEQUENO, de("1", "10", "10", "10"), "se usa el de por defecto");
        System.setProperty("rabbit.almacenamiento.pequeno.peso-kg", "-3");
        assertEquals(PEQUENO, de("1", "10", "10", "10"));
    }

    @Test
    void elVolumenSeConfiguraEnLitros() {
        System.setProperty("rabbit.almacenamiento.pequeno.volumen-litros", "1");
        assertEquals(MEDIANO, de("0.5", "10", "10", "11"), "1,1 L supera el límite de 1 L");
        assertEquals(PEQUENO, de("0.5", "10", "10", "10"), "1 L justo");
    }

    @Test
    void lasEtiquetasSonLasDeLaPantalla() {
        assertEquals("Pequeño", PEQUENO.getEtiqueta());
        assertEquals("Fuera de rango", FUERA_DE_RANGO.getEtiqueta());
    }
}
