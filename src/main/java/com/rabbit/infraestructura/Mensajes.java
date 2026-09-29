package com.rabbit.infraestructura;

/**
 * CAPA DE PRESENTACIÓN (compartida) — publica un mensaje global de JSF
 * (sin componente asociado), el que muestra <h:messages globalOnly="true">
 * de cada vista. Antes cada Managed Bean tenía su propia copia de este
 * helper.
 */

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;

public final class Mensajes {

    private Mensajes() {}

    /** Confirmación de una operación que salió bien. */
    public static void info(String texto) {
        agregar(FacesMessage.SEVERITY_INFO, texto);
    }

    /** Error de negocio o técnico que el usuario tiene que ver. */
    public static void error(String texto) {
        agregar(FacesMessage.SEVERITY_ERROR, texto);
    }

    private static void agregar(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }
}
