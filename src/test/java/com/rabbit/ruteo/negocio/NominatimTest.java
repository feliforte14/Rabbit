package com.rabbit.ruteo.negocio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Lo que se le pregunta a OpenStreetMap y cómo se lee la respuesta (ADR-046). */
class NominatimTest {

    @Test
    void laMismaDireccionEscritaDistintoEsLaMismaClave() {
        assertEquals(Nominatim.clave("Av. Cabildo 2100, CABA", "1428"),
                Nominatim.clave("  av. cabildo   2100, caba ", " 1428"));
        assertNotEquals(Nominatim.clave("Av. Cabildo 2100, CABA", "1428"),
                Nominatim.clave("Av. Cabildo 2100, CABA", "1429"));
    }

    @Test
    void escribeCabaEnteraYSumaElCpYElPais() {
        assertEquals("Av. Cabildo 2100, Ciudad Autónoma de Buenos Aires, 1428, Argentina",
                Nominatim.consulta("Av. Cabildo 2100, CABA", "1428"));
        assertEquals("Mitre 500, Ramos Mejía, Argentina", Nominatim.consulta("Mitre 500, Ramos Mejía", null));
        // "Cabañas" no es CABA.
        assertTrue(Nominatim.consulta("Cabañas 10, Tigre", null).startsWith("Cabañas 10"));
    }

    @Test
    void laUrlPideUnSoloResultadoEnArgentina() {
        String url = Nominatim.url(Nominatim.URL_POR_DEFECTO, "Av. Cabildo 2100, CABA", "1428");
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=ar&q="));
        assertTrue(url.contains("Av.+Cabildo+2100"));
    }

    @Test
    void leeLaPrimeraCoordenada() {
        double[] c = Nominatim.leer("[{\"place_id\":1,\"lat\":\"-34.5621\",\"lon\":\"-58.4563\",\"name\":\"x\"}]");
        assertEquals(-34.5621, c[0], 1e-9);
        assertEquals(-58.4563, c[1], 1e-9);
    }

    @Test
    void sinResultadosEsNullYUnErrorEsExcepcion() {
        assertNull(Nominatim.leer(" [ ] "));
        assertThrows(IllegalArgumentException.class, () -> Nominatim.leer("<html>Bloqueado</html>"));
        assertThrows(IllegalArgumentException.class, () -> Nominatim.leer("[{\"place_id\":1}]"));
    }
}
