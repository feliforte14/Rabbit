package com.rabbit.infraestructura;

import jakarta.faces.FacesException;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import java.io.IOException;
import java.util.Iterator;

/**
 * Una vista vencida (la sesión expiró, o se cerró en otra pestaña, y el
 * usuario aprieta un botón de la página que tenía abierta) vuelve a cargar
 * la misma pantalla por GET en lugar de mostrar la página de error.
 *
 * Sin esto, Mojarra envuelve la ViewExpiredException en una ServletException,
 * la regla de web.xml para ViewExpiredException no se aplica y el usuario veía
 * "Algo salió mal" (500). Con el GET, si la sesión ya no existe la pantalla
 * lo manda al login como cualquier pedido sin sesión; si la sesión sigue (la
 * vista se descartó por otra razón), ve la pantalla de nuevo. En un pedido
 * ajax, redirect() responde con un partial-response de redirección.
 *
 * Las demás excepciones siguen su camino (página de error genérica).
 * Registrado en WEB-INF/faces-config.xml.
 */
public class ManejadorDeVistaVencida extends ExceptionHandlerWrapper {

    public ManejadorDeVistaVencida(ExceptionHandler original) {
        super(original);
    }

    @Override
    public void handle() {
        Iterator<ExceptionQueuedEvent> eventos = getUnhandledExceptionQueuedEvents().iterator();
        while (eventos.hasNext()) {
            if (esVistaVencida(eventos.next().getContext().getException())) {
                eventos.remove();
                recargar(FacesContext.getCurrentInstance());
            }
        }
        getWrapped().handle();
    }

    static boolean esVistaVencida(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ViewExpiredException) {
                return true;
            }
        }
        return false;
    }

    private static void recargar(FacesContext contexto) {
        if (contexto.getResponseComplete()) {
            return;
        }
        ExternalContext externo = contexto.getExternalContext();
        try {
            externo.redirect(externo.getRequestContextPath() + externo.getRequestServletPath());
            contexto.responseComplete();
        } catch (IOException e) {
            throw new FacesException(e);
        }
    }

    /** Fábrica que registra el manejador (faces-config.xml). */
    public static class Fabrica extends ExceptionHandlerFactory {

        public Fabrica(ExceptionHandlerFactory original) {
            super(original);
        }

        @Override
        public ExceptionHandler getExceptionHandler() {
            return new ManejadorDeVistaVencida(getWrapped().getExceptionHandler());
        }
    }
}
