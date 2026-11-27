package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** SEC-05 y eje D: CSP, nosniff, anti-clickjacking y Permissions-Policy. */
class FiltroCabecerasDeSeguridadTest {

    private static Map<String, String> directivas() {
        return Arrays.stream(FiltroCabecerasDeSeguridad.CSP.split(";\\s*"))
                .map(d -> d.split(" ", 2))
                .collect(Collectors.toMap(d -> d[0], d -> d.length > 1 ? d[1] : ""));
    }

    @Test
    void mandaLasSieteCabeceras() {
        assertEquals(Map.of(
                "Content-Security-Policy", FiltroCabecerasDeSeguridad.CSP,
                "X-Content-Type-Options", "nosniff",
                "X-Frame-Options", "DENY",
                "Permissions-Policy", FiltroCabecerasDeSeguridad.PERMISOS,
                "Referrer-Policy", "strict-origin-when-cross-origin",
                "Cross-Origin-Opener-Policy", "same-origin",
                "Cross-Origin-Resource-Policy", "same-origin"), FiltroCabecerasDeSeguridad.CABECERAS);
    }

    @Test
    void noMandaEmbedderPolicyPorqueRompeElMapa() {
        assertFalse(FiltroCabecerasDeSeguridad.CABECERAS.containsKey("Cross-Origin-Embedder-Policy"));
    }

    @Test
    void laPaginaDeSeguimientoNoSeGuardaEnCache() throws Exception {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/Rabbit/seguimiento.xhtml");
        HttpServletResponse response = mock(HttpServletResponse.class);

        new FiltroCabecerasDeSeguridad().doFilter(request, response, mock(FilterChain.class));

        verify(response).setHeader("Cache-Control", "no-store");
    }

    @Test
    void lasDemasPaginasNoCambianElCache() throws Exception {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/Rabbit/login.xhtml");
        HttpServletResponse response = mock(HttpServletResponse.class);

        new FiltroCabecerasDeSeguridad().doFilter(request, response, mock(FilterChain.class));

        org.mockito.Mockito.verify(response, org.mockito.Mockito.never()).setHeader(
                org.mockito.ArgumentMatchers.eq("Cache-Control"), org.mockito.ArgumentMatchers.anyString());
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

    @Test
    void poneLasCabecerasAntesDeSeguirConLaCadena() throws Exception {
        ServletRequest request = mock(ServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new FiltroCabecerasDeSeguridad().doFilter(request, response, chain);

        InOrder orden = inOrder(response, chain);
        orden.verify(response).setHeader("Content-Security-Policy", FiltroCabecerasDeSeguridad.CSP);
        orden.verify(chain).doFilter(request, response);
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(response).setHeader("X-Frame-Options", "DENY");
        verify(response).setHeader("Permissions-Policy", FiltroCabecerasDeSeguridad.PERMISOS);
        verify(response).setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    }
}
