package com.rabbit.seguridad.presentacion;

/**
 * CAPA DE PRESENTACIÓN — dispara la autenticación declarativa del
 * contenedor. Junta usuario/contraseña del formulario y llama a
 * HttpServletRequest.login(), la API estándar de Servlet: el propio
 * WildFly valida la credencial contra el dominio de seguridad de la
 * aplicación (ApplicationDomain / ApplicationRealm) y, si es válida,
 * asocia el caller y sus roles a la sesión — esos roles son los que
 * después evalúan las anotaciones @RolesAllowed en cualquier EJB (ver
 * ComercioService.eliminarComercio).
 *
 * NOTA DE ALCANCE: se evaluó primero un IdentityStore propio (Jakarta
 * Security) para autenticar contra la tabla "usuarios" de UsuarioService,
 * pero en este WildFly el subsistema ejb3/undertow ya mapea la app al
 * dominio "ApplicationDomain" a nivel de servidor
 * (application-security-domain "other" -> ApplicationDomain, ver
 * standalone.xml) — el mismo dominio que hace funcionar @RolesAllowed en
 * ComercioService. Pelear ese mapeo agregaba una capa de configuración de
 * Elytron fuera del alcance de "seguridad declarativa mínima". Se optó
 * por lo que YA es nativo y funciona: usuarios/roles administrados con
 * add-user.sh en ApplicationRealm. UsuarioService sigue siendo el tercer
 * componente funcional (alta/baja de usuarios de la aplicación como dato
 * de negocio), separado de las credenciales que usa el contenedor para
 * autenticar — la misma separación que ya existe en cualquier sistema real
 * entre un Identity Provider y el perfil de usuario de la aplicación.
 */

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.Serializable;

@Named
@ViewScoped
public class LoginBean implements Serializable {

    private String username;
    private String password;

    // Autentica contra el ApplicationRealm vía la API estándar de Servlet
    // (ver el porqué en el comentario de clase) y, si funciona, redirige
    // al primer listado protegido de la app.
    public void login() throws IOException {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        HttpServletRequest request = (HttpServletRequest) facesContext.getExternalContext().getRequest();

        try {
            request.login(username, password);
            // El sidebar (ver template.xhtml) reemplaza a lo que antes era
            // panel.xhtml como menú — ya no hace falta una pantalla
            // intermedia post-login, se entra directo al listado de comercios.
            facesContext.getExternalContext().redirect(request.getContextPath() + "/comercios.xhtml");
        } catch (ServletException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, "Usuario o contraseña incorrectos");
        }
    }

    // Cierra la sesión de contenedor (request.logout()) y además invalida
    // la HttpSession: no alcanza con lo primero, porque atributos propios
    // de la aplicación guardados en sesión (como el EJB @Stateful de
    // ReservaBean) sobrevivirían a un logout que solo desautentique.
    public void logout() throws IOException {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        HttpServletRequest request = (HttpServletRequest) facesContext.getExternalContext().getRequest();
        try {
            request.logout();
        } catch (ServletException e) {
            // No había sesión autenticada que cerrar del lado del contenedor.
        }
        request.getSession().invalidate();
        facesContext.getExternalContext().redirect(request.getContextPath() + "/login.xhtml");
    }

    // Helper para publicar un FacesMessage global (sin componente asociado)
    // — lo consume <h:messages> en login.xhtml.
    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
