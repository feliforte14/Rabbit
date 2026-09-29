package com.rabbit.seguridad.presentacion;

/**
 * Envuelve el SecurityContext estándar para que las vistas puedan
 * preguntar "quién está logueado" y "es admin" con Expression Language
 * simple, sin acoplar los .xhtml a la API de Jakarta Security.
 */

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import com.rabbit.seguridad.negocio.ValidacionException;
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

    @Inject
    private IContextoUsuario contextoUsuario;

    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IGestionRepartidores repartidores;

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

    /** Páginas del personal de Rabbit (ADMINISTRADOR u OPERADOR). */
    public void exigirPersonal() throws IOException {
        exigir(isPersonal());
    }

    /** Páginas solo para ADMINISTRADOR (usuarios.xhtml). */
    public void exigirAdministrador() throws IOException {
        exigir(isAdmin());
    }

    /** Páginas del portal del comercio. */
    public void exigirComercio() throws IOException {
        exigir(isComercio());
    }

    /** Páginas del repartidor. */
    public void exigirRepartidor() throws IOException {
        exigir(isRepartidor());
    }

    /** Páginas que comparten el personal y el comercio (puntos de picking). */
    public void exigirPersonalOComercio() throws IOException {
        exigir(isPersonal() || isComercio());
    }

    // Sin sesión, al login; con sesión pero sin el rol, a su página de
    // inicio. Es solo navegación: la autorización real la impone el EJB.
    private void exigir(boolean permitido) throws IOException {
        if (!isAutenticado()) {
            exigirSesion();
            return;
        }
        if (!permitido) {
            redirigir(getPaginaInicio());
        }
    }

    private void redirigir(String pagina) throws IOException {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        HttpServletRequest request = (HttpServletRequest) facesContext.getExternalContext().getRequest();
        facesContext.getExternalContext().redirect(request.getContextPath() + pagina);
        facesContext.responseComplete();
    }

    /**
     * Página de inicio según el tipo de usuario; null si el usuario no
     * tiene acceso a la aplicación web (por ejemplo, el usuario del ERP,
     * que solo usa la API REST).
     */
    public String getPaginaInicio() {
        if (isPersonal()) {
            return "/pedidos.xhtml";
        }
        if (isComercio()) {
            return "/mis-pedidos.xhtml";
        }
        if (isRepartidor()) {
            return "/mis-entregas.xhtml";
        }
        return null;
    }

    public boolean isPersonal() {
        return isAdmin() || securityContext.isCallerInRole("OPERADOR");
    }

    public boolean isComercio() {
        return securityContext.isCallerInRole("COMERCIO");
    }

    public boolean isRepartidor() {
        return securityContext.isCallerInRole("REPARTIDOR");
    }

    /** Tipo de usuario, tal como se muestra en el menú. */
    public String getRolActual() {
        if (isAdmin()) {
            return "Administrador";
        }
        if (securityContext.isCallerInRole("OPERADOR")) {
            return "Operador";
        }
        if (isComercio()) {
            return "Comercio";
        }
        if (isRepartidor()) {
            return "Repartidor";
        }
        return null;
    }

    /** A quién representa la cuenta (comercio o repartidor), para el menú. */
    public String getRepresentado() {
        try {
            if (isComercio()) {
                return comercios.obtenerComercio(contextoUsuario.idComercioActual()).nombre;
            }
            if (isRepartidor()) {
                return repartidores.obtenerRepartidor(contextoUsuario.idRepartidorActual()).getNombre();
            }
        } catch (ValidacionException | com.rabbit.comercios.negocio.ValidacionException e) {
            return "Cuenta sin asociar";
        }
        return null;
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
