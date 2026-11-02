package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.faces.FacesException;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Sesión vencida + botón de la página vieja: se recarga la pantalla, no la página de error. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManejadorDeVistaVencidaTest {

    /** FacesContext.setCurrentInstance es protegido: se llega desde una subclase. */
    private abstract static class ContextoDePrueba extends FacesContext {
        static void usar(FacesContext contexto) {
            setCurrentInstance(contexto);
        }
    }

    @Mock private FacesContext faces;
    @Mock private ExternalContext externo;
    @Mock private ExceptionHandler original;

    private final List<ExceptionQueuedEvent> pendientes = new ArrayList<>();
    private ManejadorDeVistaVencida manejador;

    @BeforeEach
    void preparar() {
        ContextoDePrueba.usar(faces);
        when(faces.getExternalContext()).thenReturn(externo);
        when(externo.getRequestContextPath()).thenReturn("/Rabbit");
        when(externo.getRequestServletPath()).thenReturn("/personal/pedidos.xhtml");
        when(original.getUnhandledExceptionQueuedEvents()).thenReturn(pendientes);
        manejador = new ManejadorDeVistaVencida(original);
    }

    @AfterEach
    void limpiar() {
        ContextoDePrueba.usar(null);
    }

    private static ExceptionQueuedEvent evento(Throwable error) {
        ExceptionQueuedEventContext contexto = mock(ExceptionQueuedEventContext.class);
        when(contexto.getException()).thenReturn(error);
        ExceptionQueuedEvent evento = mock(ExceptionQueuedEvent.class);
        when(evento.getContext()).thenReturn(contexto);
        return evento;
    }

    @Test
    void unaVistaVencidaRecargaLaMismaPantallaPorGet() throws Exception {
        pendientes.add(evento(new ViewExpiredException("vencida", "/personal/pedidos.xhtml")));

        manejador.handle();

        verify(externo).redirect("/Rabbit/personal/pedidos.xhtml");
        verify(faces).responseComplete();
        assertTrue(pendientes.isEmpty(), "la excepción queda atendida");
        verify(original).handle();
    }

    @Test
    void tambienCuandoLlegaEnvueltaEnOtraExcepcion() throws Exception {
        pendientes.add(evento(new FacesException(new ServletException(new ViewExpiredException("vencida")))));

        manejador.handle();

        verify(externo).redirect("/Rabbit/personal/pedidos.xhtml");
        assertTrue(pendientes.isEmpty());
    }

    @Test
    void lasDemasExcepcionesSiguenHaciaLaPaginaDeError() throws Exception {
        ExceptionQueuedEvent otro = evento(new IllegalStateException("otra cosa"));
        pendientes.add(otro);

        manejador.handle();

        verify(externo, never()).redirect(anyString());
        verify(faces, never()).responseComplete();
        assertEquals(List.of(otro), pendientes, "la deja para el manejador original");
        verify(original).handle();
    }

    @Test
    void siLaRespuestaYaSeCompletoNoRedirigeDeNuevo() throws Exception {
        when(faces.getResponseComplete()).thenReturn(true);
        pendientes.add(evento(new ViewExpiredException("vencida")));
        pendientes.add(evento(new ViewExpiredException("otra vez")));

        manejador.handle();

        verify(externo, never()).redirect(anyString());
        assertTrue(pendientes.isEmpty());
    }

    @Test
    void unErrorAlRedirigirSeInformaComoFacesException() throws Exception {
        doThrow(new IOException("se cortó")).when(externo).redirect(anyString());
        pendientes.add(evento(new ViewExpiredException("vencida")));

        FacesException error = assertThrows(FacesException.class, manejador::handle);
        assertInstanceOf(IOException.class, error.getCause());
    }

    @Test
    void reconoceLaVistaVencidaSoloEnLaCadenaDeCausas() {
        assertTrue(ManejadorDeVistaVencida.esVistaVencida(new ViewExpiredException()));
        assertTrue(ManejadorDeVistaVencida.esVistaVencida(new RuntimeException(new ViewExpiredException())));
        assertFalse(ManejadorDeVistaVencida.esVistaVencida(new RuntimeException("x")));
        assertFalse(ManejadorDeVistaVencida.esVistaVencida(null));
    }

    @Test
    void laFabricaEnvuelveAlManejadorOriginal() {
        ExceptionHandlerFactory anterior = mock(ExceptionHandlerFactory.class);
        when(anterior.getExceptionHandler()).thenReturn(original);

        ExceptionHandler creado = new ManejadorDeVistaVencida.Fabrica(anterior).getExceptionHandler();

        ManejadorDeVistaVencida nuevo = assertInstanceOf(ManejadorDeVistaVencida.class, creado);
        assertSame(original, nuevo.getWrapped());
    }
}
