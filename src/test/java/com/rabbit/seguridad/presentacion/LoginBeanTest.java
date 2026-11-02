package com.rabbit.seguridad.presentacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Login web: fuerza bruta (429 + Retry-After), session fixation y usuarios sin acceso web. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginBeanTest {

    /** FacesContext.setCurrentInstance es protegido: se llega desde una subclase. */
    private abstract static class ContextoDePrueba extends FacesContext {
        static void usar(FacesContext contexto) {
            setCurrentInstance(contexto);
        }
    }

    @Mock private FacesContext faces;
    @Mock private ExternalContext externo;
    @Mock private HttpServletRequest request;
    @Mock private HttpSession sesionHttp;
    @Mock private SesionBean sesion;

    private final LimiteDeIntentos limite = new LimiteDeIntentos();
    private final LoginBean bean = new LoginBean();

    @BeforeEach
    void preparar() throws Exception {
        ContextoDePrueba.usar(faces);
        when(faces.getExternalContext()).thenReturn(externo);
        when(externo.getRequest()).thenReturn(request);
        when(request.getContextPath()).thenReturn("/Rabbit");
        when(request.getSession()).thenReturn(sesionHttp);
        inyectar("sesion", sesion);
        inyectar("limite", limite);
        bean.setUsername("ana");
        bean.setPassword("secreta-123");
    }

    @AfterEach
    void limpiar() {
        ContextoDePrueba.usar(null);
    }

    private void inyectar(String campo, Object valor) throws Exception {
        var f = LoginBean.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(bean, valor);
    }

    private String ultimoMensaje() {
        ArgumentCaptor<FacesMessage> mensaje = ArgumentCaptor.forClass(FacesMessage.class);
        verify(faces).addMessage(isNull(), mensaje.capture());
        return mensaje.getValue().getSummary();
    }

    @Test
    void conCredencialesCorrectasCambiaElIdDeSesionYVaASuPantalla() throws Exception {
        when(sesion.getPaginaInicio()).thenReturn("/personal/inicio.xhtml");

        bean.login();

        InOrder orden = inOrder(request, externo);
        orden.verify(request).login("ana", "secreta-123");
        orden.verify(request).changeSessionId();
        orden.verify(externo).redirect("/Rabbit/personal/inicio.xhtml");
    }

    @Test
    void conContrasenaIncorrectaAvisaSinDecirSiElUsuarioExiste() throws Exception {
        doThrow(new ServletException("mal")).when(request).login(anyString(), anyString());

        bean.login();

        assertEquals("Usuario o contraseña incorrectos", ultimoMensaje());
        verify(externo, never()).redirect(anyString());
    }

    @Test
    void despuesDelMaximoDeFallosResponde429ConRetryAfterYNiConsultaAlRealm() throws Exception {
        for (int i = 0; i < LimiteDeIntentos.MAXIMO; i++) {
            limite.registrarFallo("ana");
        }

        bean.login();

        verify(externo).setResponseStatus(429);
        ArgumentCaptor<String> retryAfter = ArgumentCaptor.forClass(String.class);
        verify(externo).setResponseHeader(org.mockito.ArgumentMatchers.eq("Retry-After"), retryAfter.capture());
        long segundos = Long.parseLong(retryAfter.getValue());
        assertTrue(segundos > 0 && segundos <= LimiteDeIntentos.BLOQUEO.getSeconds() + 1, "Retry-After " + segundos);
        assertTrue(ultimoMensaje().startsWith("Demasiados intentos fallidos"));
        verify(request, never()).login(any(), any());
    }

    @Test
    void unUsuarioSinAccesoWebNoQuedaLogueado() throws Exception {
        when(sesion.getPaginaInicio()).thenReturn(null);

        bean.login();

        verify(request).logout();
        verify(sesionHttp).invalidate();
        assertEquals("Este usuario no tiene acceso a la aplicación web", ultimoMensaje());
        verify(externo, never()).redirect(anyString());
    }

    @Test
    void elLogoutCierraLaSesionAunqueNoHubieraUnaAutenticada() throws Exception {
        doThrow(new ServletException("no había")).when(request).logout();

        bean.logout();

        verify(sesionHttp).invalidate();
        verify(externo).redirect("/Rabbit/login.xhtml");
    }

    @Test
    void elMailDePedidoDeCuentaVieneArmado() {
        String mailto = bean.getContactoMailto();
        assertTrue(mailto.startsWith("mailto:" + bean.getContactoMail() + "?subject="));
        assertFalse(mailto.contains("+"), "en un mailto el espacio va como %20");
        assertEquals("ana", bean.getUsername());
        assertEquals("secreta-123", bean.getPassword());
    }
}
