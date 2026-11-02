package com.rabbit.integracion.banco;

import static com.rabbit.integracion.banco.CircuitBreakerBanco.Estado.*;
import static com.rabbit.integracion.banco.CircuitBreakerBanco.SIN_PERMISO;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Circuit breaker del banco (eje C: "SOAP externo no responde en 5 s → circuit breaker, sin hilos
 * colgados"). Umbral por defecto: 3 fallas seguidas; espera: 30 s, que el test adelanta moviendo el
 * momento en que se abrió el circuito.
 */
class CircuitBreakerBancoTest {

    private CircuitBreakerBanco circuito;

    @BeforeEach
    void preparar() {
        circuito = new CircuitBreakerBanco();
    }

    /** Simula que pasó la espera de 30 s desde que se abrió (o desde la llamada de prueba). */
    private void pasoLaEspera() throws Exception {
        var desde = CircuitBreakerBanco.class.getDeclaredField("desde");
        desde.setAccessible(true);
        desde.setLong(circuito, System.currentTimeMillis() - 31_000);
    }

    private void fallarSeguidas(int veces) {
        for (int i = 0; i < veces; i++) {
            circuito.registrarFalla(circuito.intentarLlamada());
        }
    }

    @Test
    void cerradoDejaPasarLasLlamadas() {
        assertEquals(CERRADO, circuito.getEstado());
        assertNotEquals(SIN_PERMISO, circuito.intentarLlamada());
    }

    @Test
    void seAbreRecienAlTercerFallaSeguida() {
        fallarSeguidas(2);
        assertEquals(CERRADO, circuito.getEstado());
        fallarSeguidas(1);
        assertEquals(ABIERTO, circuito.getEstado());
    }

    @Test
    void unaRespuestaDelBancoReiniciaLaCuentaDeFallas() {
        fallarSeguidas(2);
        circuito.registrarExito(circuito.intentarLlamada()); // por ejemplo, un pago rechazado: el banco respondió
        fallarSeguidas(2);
        assertEquals(CERRADO, circuito.getEstado(), "las fallas tienen que ser seguidas");
    }

    @Test
    void abiertoCortaSinLlamarAlBancoHastaQuePasaLaEspera() throws Exception {
        fallarSeguidas(3);
        assertEquals(SIN_PERMISO, circuito.intentarLlamada(), "no espera los 5 s del timeout");

        pasoLaEspera();
        long prueba = circuito.intentarLlamada();
        assertNotEquals(SIN_PERMISO, prueba);
        assertEquals(SEMIABIERTO, circuito.getEstado());
        assertEquals(SIN_PERMISO, circuito.intentarLlamada(), "mientras hay una prueba en curso, el resto se corta");
    }

    @Test
    void siLaPruebaRespondeSeCierra() throws Exception {
        fallarSeguidas(3);
        pasoLaEspera();
        circuito.registrarExito(circuito.intentarLlamada());
        assertEquals(CERRADO, circuito.getEstado());
        assertNotEquals(SIN_PERMISO, circuito.intentarLlamada());
    }

    @Test
    void siLaPruebaFallaVuelveAAbrirse() throws Exception {
        fallarSeguidas(3);
        pasoLaEspera();
        circuito.registrarFalla(circuito.intentarLlamada());
        assertEquals(ABIERTO, circuito.getEstado());
        assertEquals(SIN_PERMISO, circuito.intentarLlamada());
    }

    @Test
    void elResultadoDeUnaLlamadaViejaNoCambiaElEstadoNuevo() throws Exception {
        long vieja = circuito.intentarLlamada(); // sale con el circuito CERRADO y tarda
        fallarSeguidas(3);
        pasoLaEspera();
        long prueba = circuito.intentarLlamada();

        circuito.registrarExito(vieja);
        assertEquals(SEMIABIERTO, circuito.getEstado(), "un éxito viejo no cierra el circuito");
        circuito.registrarFalla(vieja);
        assertEquals(SEMIABIERTO, circuito.getEstado(), "una falla vieja no lo reabre");

        circuito.registrarExito(prueba);
        assertEquals(CERRADO, circuito.getEstado());
    }

    @Test
    void siLaPruebaNuncaRegistraResultadoSePermiteOtraDespuesDeLaEspera() throws Exception {
        fallarSeguidas(3);
        pasoLaEspera();
        long perdida = circuito.intentarLlamada(); // el hilo murió sin registrar nada
        pasoLaEspera();
        long nueva = circuito.intentarLlamada();
        assertNotEquals(SIN_PERMISO, nueva);
        circuito.registrarExito(perdida);
        assertEquals(SEMIABIERTO, circuito.getEstado(), "la prueba perdida quedó invalidada");
        circuito.registrarExito(nueva);
        assertEquals(CERRADO, circuito.getEstado());
    }
}
