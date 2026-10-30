package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** SEC-05 y eje D: CSP, nosniff, anti-clickjacking y Permissions-Policy. */
class FiltroCabecerasDeSeguridadTest {

    private static Map<String, String> directivas() {
        return Arrays.stream(FiltroCabecerasDeSeguridad.CSP.split(";\\s*"))
                .map(d -> d.split(" ", 2))
                .collect(Collectors.toMap(d -> d[0], d -> d.length > 1 ? d[1] : ""));
    }

    @Test
    void mandaLasCincoCabeceras() {
        assertEquals(Map.of(
                "Content-Security-Policy", FiltroCabecerasDeSeguridad.CSP,
                "X-Content-Type-Options", "nosniff",
                "X-Frame-Options", "DENY",
                "Permissions-Policy", FiltroCabecerasDeSeguridad.PERMISOS,
                "Referrer-Policy", "strict-origin-when-cross-origin"), FiltroCabecerasDeSeguridad.CABECERAS);
    }

    @Test
    void laCspNoDejaMeterLaPaginaEnUnIframeNiPlugins() {
        Map<String, String> csp = directivas();
        assertEquals("'none'", csp.get("frame-ancestors"));
        assertEquals("'none'", csp.get("object-src"));
        assertEquals("'self'", csp.get("default-src"));
        assertEquals("'self'", csp.get("base-uri"));
        assertEquals("'self'", csp.get("form-action"));
    }

    @Test
    void laCspNoAdmiteComodinesNiEval() {
        String csp = FiltroCabecerasDeSeguridad.CSP;
        assertFalse(csp.contains("*"), csp);
        assertFalse(csp.contains("unsafe-eval"), csp);
        assertFalse(csp.contains("http:"), "solo orígenes HTTPS: " + csp);
    }

    @Test
    void lasConexionesSoloVanARabbit() {
        assertEquals("'self'", directivas().get("connect-src"));
    }

    @Test
    void soloLaUbicacionYLaCamaraYSoloParaRabbit() {
        String permisos = FiltroCabecerasDeSeguridad.PERMISOS;
        assertTrue(permisos.contains("geolocation=(self)"));
        assertTrue(permisos.contains("camera=(self)"));
        assertTrue(permisos.contains("microphone=()"));
        assertTrue(permisos.contains("payment=()"));
    }
}
