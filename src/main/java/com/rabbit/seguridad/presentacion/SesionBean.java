package com.rabbit.seguridad.presentacion;

/**
 * Envuelve el SecurityContext estándar para que las vistas puedan
 * preguntar "quién está logueado" y "es admin" con Expression Language
 * simple, sin acoplar los .xhtml a la API de Jakarta Security.
 */

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.Principal;

@Named
@RequestScoped
public class SesionBean {

    @Inject
    private SecurityContext securityContext;

    /**
     * Gatekeeper de páginas que requieren sesión iniciada: cada .xhtml
     * protegido lo llama desde su propio <f:metadata><f:viewAction>.
     * Sin esto, cualquiera que tipee la URL directo entra igual — un
     * f:viewAction corre antes del renderizado, a diferencia de un simple
     * "rendered" que solo oculta contenido pero deja la página accesible.
     */
    public void exigirSesion() throws IOException {
        if (!isAutenticado()) {
            FacesContext facesContext = FacesContext.getCurrentInstance();
            HttpServletRequest request = (HttpServletRequest) facesContext.getExternalContext().getRequest();
            facesContext.getExternalContext().redirect(request.getContextPath() + "/login.xhtml");
            // Sin esto, JSF sigue el ciclo de vida normal y renderiza la
            // vista igual después del redirect() — el response ya tiene un
            // Location header pero el body real termina siendo el de la
            // página "protegida", no el de login.
            facesContext.responseComplete();
        }
    }

    // Nombre del usuario logueado, para mostrarlo en el sidebar
    // (template.xhtml); null si no hay sesión iniciada.
    public String getUsuarioActual() {
        return isAutenticado() ? securityContext.getCallerPrincipal().getName() : null;
    }

    /**
     * Un caller sin login no tiene Principal null: Elytron le asigna uno
     * real llamado "anonymous" (ver default-permission-mapper en
     * standalone.xml). Por eso acá se compara el nombre, no solo se
     * chequea != null.
     */
    public boolean isAutenticado() {
        Principal principal = securityContext.getCallerPrincipal();
        return principal != null && !"anonymous".equals(principal.getName());
    }

    // Usado en la vista para mostrar/ocultar acciones de administrador
    // (por ejemplo "Eliminar" en comercios.xhtml) — es solo una comodidad
    // de UI: la autorización real la impone @RolesAllowed en el EJB.
    public boolean isAdmin() {
        return securityContext.isCallerInRole("ADMINISTRADOR");
    }
}
