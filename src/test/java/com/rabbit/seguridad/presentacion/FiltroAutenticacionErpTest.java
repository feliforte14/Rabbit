package com.rabbit.seguridad.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FiltroAutenticacionErpTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private final LimiteDeIntentos limite = new LimiteDeIntentos();
    private final FiltroAutenticacionErp filtro = new FiltroAutenticacionErp();
    private final StringWriter cuerpo = new StringWriter();
    private Instant ahora = Instant.parse("2026-11-10T12:00:00Z");

    @BeforeEach
    void preparar() throws Exception {
        limite.usarReloj(new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zona) { return this; }
            @Override public Instant instant() { return ahora; }
        });
        var campo = FiltroAutenticacionErp.class.getDeclaredField("limite");
        campo.setAccessible(true);
        campo.set(filtro, limite);
        when(response.getWriter()).thenReturn(new PrintWriter(cuerpo, true));
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");
        doThrow(new ServletException("incorrecta")).when(request).login(anyString(), anyString());
    }

    private static String basic(String usuarioYClave) {
        return "Basic " + Base64.getEncoder().encodeToString(usuarioYClave.getBytes(StandardCharsets.UTF_8));
    }

    private void con(String authorization) {
        when(request.getHeader("Authorization")).thenReturn(authorization);
    }

    private void claveCorrecta(String usuario, String clave) throws ServletException {
        org.mockito.Mockito.doNothing().when(request).login(usuario, clave);
    }

    @Test
    void conCredencialesCorrectasPasaYCierraLaIdentidadAlTerminar() throws Exception {
        claveCorrecta("erp-tienda", "s3cr3t:con:dos-puntos");
        con(basic("erp-tienda:s3cr3t:con:dos-puntos"));

        filtro.doFilter(request, response, chain);

        InOrder orden = inOrder(request, chain);
        orden.verify(request).login("erp-tienda", "s3cr3t:con:dos-puntos");
        orden.verify(chain).doFilter(request, response);
        orden.verify(request).logout();
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void laIdentidadSeCierraAunqueElRecursoFalle() throws Exception {
        claveCorrecta("erp", "bien");
        con(basic("erp:bien"));
        doThrow(new IllegalStateException("explotó")).when(chain).doFilter(any(), any());

        try {
            filtro.doFilter(request, response, chain);
        } catch (IllegalStateException esperado) {
            // la excepción sigue su camino
        }

        verify(request).logout();
    }

    @Test
    void unPedidoYaAutenticadoNoSeVuelveAAutenticar() throws Exception {
        when(request.getUserPrincipal()).thenReturn(() -> "admin");

        filtro.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request, never()).login(anyString(), anyString());
        verify(request, never()).logout();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Bearer abc", "Basic", "Basic !!!no-es-base64!!!", "Basic c2luLWRvcy1wdW50b3M=", "Basic OmNsYXZl"})
    void sinCredencialesUsablesEs401ConElDesafio(String authorization) throws Exception {
        con(authorization);

        filtro.doFilter(request, response, chain);

        verify(response).setStatus(401);
        verify(response).setHeader("WWW-Authenticate", FiltroAutenticacionErp.DESAFIO);
        verify(response).setContentType("application/problem+json;charset=UTF-8");
        assertTrue(cuerpo.toString().contains("\"type\":\"/problemas/no-autenticado\""), cuerpo.toString());
        verify(request, never()).login(anyString(), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void unaClaveIncorrectaEs401YCuentaUnFallo() throws Exception {
        con(basic("erp:mal"));

        filtro.doFilter(request, response, chain);

        verify(response).setStatus(401);
        assertTrue(cuerpo.toString().contains("Usuario o contraseña incorrectos"));
        verify(chain, never()).doFilter(any(), any());
        assertEquals(Duration.ZERO, limite.esperaPara("erp"));
    }

    @Test
    void alQuintoFalloElUsuarioQuedaBloqueadoYRecibe429SinConsultarAlRealm() throws Exception {
        con(basic("erp:mal"));
        for (int i = 0; i < LimiteDeIntentos.MAXIMO; i++) {
            filtro.doFilter(request, response, chain);
        }
        verify(request, times(LimiteDeIntentos.MAXIMO)).login("erp", "mal");

        claveCorrecta("erp", "bien");
        con(basic("ERP:bien"));
        ahora = ahora.plusSeconds(60);
        filtro.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(response).setHeader("Retry-After", String.valueOf(Duration.ofMinutes(14).toSeconds()));
        assertTrue(cuerpo.toString().contains("\"status\":429"), cuerpo.toString());
        assertTrue(cuerpo.toString().contains("/problemas/demasiados-intentos"));
        verify(request, never()).login("ERP", "bien");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void venceElBloqueoYUnLoginCorrectoReiniciaLaCuenta() throws Exception {
        con(basic("erp:mal"));
        for (int i = 0; i < LimiteDeIntentos.MAXIMO; i++) {
            filtro.doFilter(request, response, chain);
        }
        ahora = ahora.plus(LimiteDeIntentos.BLOQUEO).plusSeconds(1);
        claveCorrecta("erp", "bien");
        con(basic("erp:bien"));

        filtro.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        con(basic("erp:mal"));
        filtro.doFilter(request, response, chain);
        assertEquals(Duration.ZERO, limite.esperaPara("erp"));
    }

    @Test
    void elRetryAfterRedondeaParaArribaYNuncaEsCero() throws Exception {
        con(basic("erp:mal"));
        for (int i = 0; i < LimiteDeIntentos.MAXIMO; i++) {
            filtro.doFilter(request, response, chain);
        }
        ahora = ahora.plus(LimiteDeIntentos.BLOQUEO).minusMillis(300);

        filtro.doFilter(request, response, chain);

        verify(response).setHeader("Retry-After", "1");
    }

    @Test
    void leeLasCredencialesDelHeader() {
        assertEquals(new FiltroAutenticacionErp.Credenciales("ana", ""), FiltroAutenticacionErp.leer(basic("ana:")));
        assertEquals(new FiltroAutenticacionErp.Credenciales("ñandú", "clave"),
                FiltroAutenticacionErp.leer("basic " + Base64.getEncoder().encodeToString("ñandú:clave".getBytes(StandardCharsets.UTF_8))));
        assertNull(FiltroAutenticacionErp.leer("Basic " + Base64.getEncoder().encodeToString(":x".getBytes())));
    }
}
