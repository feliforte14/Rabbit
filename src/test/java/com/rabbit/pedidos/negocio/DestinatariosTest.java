package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Los datos de quien recibe (ADR-039): opcionales, pero si vienen tienen que servir. */
class DestinatariosTest {

    @Test
    void vacioEsQueNoVino() {
        assertNull(Destinatarios.nombre(null));
        assertNull(Destinatarios.nombre("   "));
        assertNull(Destinatarios.telefono(""));
        assertNull(Destinatarios.email(" "));
    }

    @Test
    void seNormalizanLosEspacios() {
        assertEquals("Ana Pérez", Destinatarios.nombre("  Ana   Pérez "));
        assertEquals("+54 11 4567-8901", Destinatarios.telefono(" +54 11  4567-8901 "));
    }

    @Test
    void elTelefonoSeAceptaComoLoEscribeLaGente() {
        assertEquals("(011) 4567-8901", Destinatarios.telefono("(011) 4567-8901"));
        assertEquals("1145678901", Destinatarios.telefono("1145678901"));
    }

    @Test
    void unTelefonoQueNoSirveSeRechaza() {
        assertThrows(ValidacionException.class, () -> Destinatarios.telefono("llamar al portero"));
        assertThrows(ValidacionException.class, () -> Destinatarios.telefono("123"));
    }

    @Test
    void elEmailSeGuardaEnMinusculasYSeValida() {
        assertEquals("ana@correo.com", Destinatarios.email(" Ana@Correo.com "));
        assertThrows(ValidacionException.class, () -> Destinatarios.email("ana.correo.com"));
        assertThrows(ValidacionException.class, () -> Destinatarios.email("ana@correo"));
    }

    @Test
    void elEmailEsObligatorioParaUnPedidoNuevo() {
        ValidacionException e = assertThrows(ValidacionException.class, () -> Destinatarios.emailObligatorio(null));
        assertTrue(e.getMessage().startsWith("Falta el email de quien recibe"));
        assertThrows(ValidacionException.class, () -> Destinatarios.emailObligatorio("   "));
        assertEquals("ana@correo.com", Destinatarios.emailObligatorio(" Ana@Correo.com "));
        // Los pedidos viejos sin email se siguen pudiendo leer.
        assertNull(Destinatarios.email(null));
    }

    @Test
    void unNombreDemasiadoLargoSeRechaza() {
        assertThrows(ValidacionException.class, () -> Destinatarios.nombre("x".repeat(101)));
    }
}
