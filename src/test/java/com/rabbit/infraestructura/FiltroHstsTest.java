package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** SEC-05: HSTS solo por HTTPS, con max-age configurable y apagable. */
class FiltroHstsTest {

    @Test
    void porHttpsMandaLaCabeceraConElMaxAgeConfigurado() {
        assertEquals("max-age=31536000", FiltroHsts.valorDeLaCabecera(true, FiltroHsts.UN_ANIO));
        assertEquals("max-age=300", FiltroHsts.valorDeLaCabecera(true, 300));
    }

    @Test
    void porHttpNoLaMandaPorqueElNavegadorLaIgnora() {
        assertNull(FiltroHsts.valorDeLaCabecera(false, FiltroHsts.UN_ANIO));
    }

    @Test
    void conMaxAgeCeroONegativoQuedaApagada() {
        assertNull(FiltroHsts.valorDeLaCabecera(true, 0));
        assertNull(FiltroHsts.valorDeLaCabecera(true, -1));
    }

    @Test
    void noLlevaIncludeSubDomainsNiPreload() {
        String valor = FiltroHsts.valorDeLaCabecera(true, FiltroHsts.UN_ANIO);
        assertFalse(valor.contains("includeSubDomains"));
        assertFalse(valor.contains("preload"));
    }

    @Test
    void elNombreDeLaCabeceraEsElEstandar() {
        assertEquals("Strict-Transport-Security", FiltroHsts.CABECERA);
    }
}
