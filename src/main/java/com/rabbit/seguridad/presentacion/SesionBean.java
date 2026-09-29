package com.rabbit.seguridad.presentacion;

/**
 * Envuelve el SecurityContext estándar para que las vistas puedan
 * preguntar "quién está logueado" y "es admin" con Expression Language
 * simple, sin acoplar los .xhtml a la API de Jakarta Security.
 */

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.repartidores.dto.RepartidorDTO;
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
import java.util.logging.Logger;

@Named
@RequestScoped
public class SesionBean {

    private static final Logger LOG = Logger.getLogger(SesionBean.class.getName());

    private static final String PAGINA_LOGIN = "/login.xhtml";
    private static final String SIN_ASOCIAR = "Cuenta sin asociar";

    @Inject
    private SecurityContext securityContext;

    // Cache del render en curso (el bean es @RequestScoped).
    private String representado;

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
            facesContext.getExternalContext().redirect(request.getContextPath() + PAGINA_LOGIN);
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
            String inicio = getPaginaInicio();
            if (inicio == null) {
                // Autenticado pero sin ningún rol con acceso web (por
                // ejemplo, el usuario del ERP). No hay página a la cual
                // mandarlo: se cierra la sesión y vuelve al login.
                LOG.warning("[Seguridad] " + getUsuarioActual()
                        + " no tiene un rol con acceso a la aplicación web: se cierra la sesión");
                cerrarSesion();
                inicio = PAGINA_LOGIN;
            }
            redirigir(inicio);
        }
    }

    private void cerrarSesion() {
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance()
                .getExternalContext().getRequest();
        try {
            request.logout();
        } catch (jakarta.servlet.ServletException e) {
            // No había sesión autenticada que cerrar del lado del contenedor.
        }
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
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
        // El menú lo pide más de una vez por render: se resuelve una sola.
        if (representado == null) {
            representado = resolverRepresentado();
        }
        return representado.isEmpty() ? null : representado;
    }

    // Nunca propaga una excepción: el menú se muestra en todas las páginas,
    // y una cuenta mal asociada (el comercio o el repartidor ya no existe)
    // no puede romper el render de cada una.
    private String resolverRepresentado() {
        try {
            if (isComercio()) {
                return comercios.obtenerComercio(contextoUsuario.idComercioActual()).nombre;
            }
            if (isRepartidor()) {
                RepartidorDTO repartidor = repartidores.obtenerRepartidor(contextoUsuario.idRepartidorActual());
                return repartidor != null ? repartidor.getNombre() : SIN_ASOCIAR;
            }
        } catch (ValidacionException | com.rabbit.comercios.negocio.ValidacionException e) {
            return SIN_ASOCIAR;
        }
        return "";
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
