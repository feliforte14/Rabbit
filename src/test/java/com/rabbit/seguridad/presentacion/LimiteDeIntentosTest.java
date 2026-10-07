package com.rabbit.seguridad.presentacion;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LimiteDeIntentosTest {

    /** Un reloj que el test puede adelantar. */
    private static final class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-10-07T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zona) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private LimiteDeIntentos limite;
    private Reloj reloj;

    @BeforeEach
    void preparar() {
        limite = new LimiteDeIntentos();
        reloj = new Reloj();
        limite.usarReloj(reloj);
    }

    @Test
    void bloqueaDespuesDelMaximoDeFallos() {
        for (int i = 1; i < LimiteDeIntentos.MAXIMO; i++) {
            limite.registrarFallo("ana");
            assertEquals(Duration.ZERO, limite.esperaPara("ana"), "todavía no al intento " + i);
        }
        limite.registrarFallo("ana");
        assertEquals(LimiteDeIntentos.BLOQUEO, limite.esperaPara("ana"));
    }

    @Test
    void elBloqueoVenceConElTiempo() {
        for (int i = 0; i < LimiteDeIntentos.MAXIMO; i++) {
            limite.registrarFallo("ana");
        }
        reloj.ahora = reloj.ahora.plus(LimiteDeIntentos.BLOQUEO).plusSeconds(1);
        assertEquals(Duration.ZERO, limite.esperaPara("ana"));
        // Vencido el bloqueo, la cuenta arranca de cero: un fallo no vuelve a bloquear.
        limite.registrarFallo("ana");
        assertEquals(Duration.ZERO, limite.esperaPara("ana"));
    }

    @Test
    void unLoginCorrectoReiniciaLaCuenta() {
        for (int i = 0; i < LimiteDeIntentos.MAXIMO - 1; i++) {
            limite.registrarFallo("ana");
        }
        limite.registrarExito("ana");
        limite.registrarFallo("ana");
        assertEquals(Duration.ZERO, limite.esperaPara("ana"));
    }

    @Test
    void esPorUsuarioSinDistinguirMayusculas() {
        for (int i = 0; i < LimiteDeIntentos.MAXIMO; i++) {
            limite.registrarFallo(i % 2 == 0 ? "Ana" : " ana ");
        }
        assertTrue(limite.esperaPara("ANA").compareTo(Duration.ZERO) > 0);
        assertEquals(Duration.ZERO, limite.esperaPara("beto"));
    }
}
