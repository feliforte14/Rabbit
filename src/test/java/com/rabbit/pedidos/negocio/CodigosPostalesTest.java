package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CodigosPostalesTest {

    @Test
    void aceptaCuatroDigitosYCpa() {
        assertEquals("1414", CodigosPostales.resolver("1414", null));
        assertEquals("1414", CodigosPostales.resolver(" 1414 ", null));
        assertEquals("1414", CodigosPostales.resolver("C1414ABC", null));
        assertEquals("1043", CodigosPostales.resolver("c1043aaf", null));
    }

    @Test
    void siNoVieneLoBuscaEnLaDireccion() {
        assertEquals("1414", CodigosPostales.resolver(null, "Av. Corrientes 1234 (C1414ABC), CABA"));
        assertEquals("1638", CodigosPostales.resolver("", "Av. Maipú 1500, CP 1638, Vicente López"));
        assertEquals("1900", CodigosPostales.resolver(null, "Calle 7 (1900) La Plata"));
    }

    @Test
    void unNumeroDeCalleSueltoNoEsCodigoPostal() {
        assertNull(CodigosPostales.resolver(null, "Av. Corrientes 1234, CABA"));
    }

    @Test
    void detectaCodigosInvalidos() {
        assertTrue(CodigosPostales.esInvalido("ABC"));
        assertTrue(CodigosPostales.esInvalido("12345"));
        assertFalse(CodigosPostales.esInvalido(null));
        assertFalse(CodigosPostales.esInvalido("C1414ABC"));
    }

    @Test
    void elCodigoRotuladoSigueResolviendoseConEspaciosYDosPuntos() {
        assertEquals("1414", CodigosPostales.resolver(null, "Av. Corrientes 1234, CP 1414"));
        assertEquals("1414", CodigosPostales.resolver(null, "Av. Corrientes 1234, C.P.: 1414"));
        assertEquals("1414", CodigosPostales.resolver(null, "Av. Corrientes 1234, c.p :   1414"));
    }

    // Con \s*:?\s* sin posesivos, una racha larga de espacios se repartía entre dos cuantificadores: tiempo cuadrático.
    @Test
    void unaRachaLargaDeEspaciosNoLaTrabaYNoDaCodigo() {
        String direccion = "Calle falsa, CP" + " ".repeat(100_000) + "sin numero";
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> assertNull(CodigosPostales.resolver(null, direccion)));
    }
}
