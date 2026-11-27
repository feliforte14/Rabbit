package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TrazasTest {

    @Test
    void unaExcepcionSinCausaEsTipoYMensaje() {
        assertEquals("IllegalStateException: base caída", Trazas.resumen(new IllegalStateException("base caída")));
    }

    @Test
    void sinMensajeQuedaSoloElTipo() {
        assertEquals("IllegalStateException", Trazas.resumen(new IllegalStateException()));
        assertEquals("IllegalStateException", Trazas.resumen(new IllegalStateException("   ")));
    }

    @Test
    void conCausaMuestraLaDeFondoYNoLasIntermedias() {
        Exception fondo = new java.net.SocketTimeoutException("Read timed out");
        Exception medio = new IllegalArgumentException("intermedia", fondo);
        Exception afuera = new IllegalStateException("Could not receive Message.", medio);

        assertEquals("IllegalStateException: Could not receive Message. (causa: SocketTimeoutException: Read timed out)",
                Trazas.resumen(afuera));
    }

    @Test
    void elResumenEsUnaSolaLinea() {
        String resumen = Trazas.resumen(new IllegalStateException("línea 1\n\tat com.rabbit.Algo(Algo.java:1)\r\nlínea 3"));

        assertFalse(resumen.contains("\n"));
        assertFalse(resumen.contains("\r"));
        assertEquals("IllegalStateException: línea 1 at com.rabbit.Algo(Algo.java:1) línea 3", resumen);
    }

    @Test
    void sinExcepcionNoRompe() {
        assertEquals("sin detalle", Trazas.resumen(null));
    }

    @Test
    void unaCausaQueSeApuntaASiMismaNoCuelga() {
        Exception rara = new IllegalStateException("rara") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertEquals(": rara", Trazas.resumen(rara).substring(Trazas.resumen(rara).indexOf(':')));
    }
}
