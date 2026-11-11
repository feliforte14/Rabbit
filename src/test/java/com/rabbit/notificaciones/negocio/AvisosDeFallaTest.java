package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Cuál PENDIENTE es el de una visita fallida, aunque llegue tarde (ADR-045). */
class AvisosDeFallaTest {

    private static final LocalDateTime FALLA = LocalDateTime.of(2026, 10, 17, 19, 29, 28, 531_123_456);

    @Test
    void elDeLaFallaSeReconoceAunqueLaBaseRecorteLosNanosegundos() {
        assertTrue(AvisosDeFalla.esDeLaFalla(FALLA, FALLA));
        assertTrue(AvisosDeFalla.esDeLaFalla(FALLA, FALLA.withNano(531_123_000)));
    }

    @Test
    void elPendienteDelAltaNoEsLaFalla() {
        assertFalse(AvisosDeFalla.esDeLaFalla(FALLA.minusMinutes(10), FALLA));
        assertFalse(AvisosDeFalla.esDeLaFalla(FALLA, null));
        assertFalse(AvisosDeFalla.esDeLaFalla(null, FALLA));
    }
}
