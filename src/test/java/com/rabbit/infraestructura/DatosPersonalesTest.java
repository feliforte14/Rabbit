package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** SEC-09: ningún log lleva el email completo. */
class DatosPersonalesTest {

    @Test
    void enmascaraElEmailDejandoLaInicialYElDominio() {
        assertEquals("c******@correo.test", DatosPersonales.emailEnmascarado("cliente@correo.test"));
    }

    @Test
    void elLargoDelNombreNoSeFiltra() {
        // Un nombre corto y uno largo dan el mismo resultado: no se puede deducir el largo.
        assertEquals("a******@x.com", DatosPersonales.emailEnmascarado("ab@x.com"));
        assertEquals("a******@x.com", DatosPersonales.emailEnmascarado("abcdefghijklmnop@x.com"));
    }

    @Test
    void nuncaQuedaElEmailCompletoNiLaParteLocal() {
        String m = DatosPersonales.emailEnmascarado("maria.gomez@empresa.com.ar");
        assertFalse(m.contains("maria"));
        assertFalse(m.contains("gomez"));
        assertTrue(m.endsWith("@empresa.com.ar"));
    }

    @Test
    void variosEmailsSeEnmascaranUnoPorUno() {
        assertEquals("a******@x.com, b******@y.com", DatosPersonales.emailEnmascarado("ana@x.com, beto@y.com"));
        assertEquals("a******@x.com, b******@y.com", DatosPersonales.emailEnmascarado("ana@x.com;beto@y.com"));
    }

    @Test
    void loQueNoEsUnEmailNoDejaNingunPedazo() {
        assertEquals("******", DatosPersonales.emailEnmascarado("no-es-un-email"));
        assertEquals("******", DatosPersonales.emailEnmascarado("@sinlocal.com"));
        assertEquals("******", DatosPersonales.emailEnmascarado("sinDominio@"));
    }

    @Test
    void nuloOVacioNoRompen() {
        assertEquals("(sin email)", DatosPersonales.emailEnmascarado(null));
        assertEquals("(sin email)", DatosPersonales.emailEnmascarado("   "));
    }

    @Test
    void sinEmailsLimpiaLosMensajesDeError() {
        String texto = "Invalid Addresses: cliente@correo.test (550 no existe)";
        String limpio = DatosPersonales.sinEmails(texto);
        assertFalse(limpio.contains("cliente@"));
        assertEquals("Invalid Addresses: c******@correo.test (550 no existe)", limpio);
    }

    @Test
    void sinEmailsDejaIgualUnTextoSinEmails() {
        assertEquals("Connection refused", DatosPersonales.sinEmails("Connection refused"));
        assertEquals("", DatosPersonales.sinEmails(null));
    }
}
