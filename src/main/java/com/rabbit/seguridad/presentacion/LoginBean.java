package com.rabbit.seguridad.presentacion;

/**
 * CAPA DE PRESENTACIÓN — dispara la autenticación declarativa del
 * contenedor. Junta usuario/contraseña del formulario y llama a
 * HttpServletRequest.login(), la API estándar de Servlet: el propio
 * WildFly valida la credencial contra el dominio de seguridad de la
 * aplicación (ApplicationDomain) y, si es válida,
 * asocia el caller y sus roles a la sesión — esos roles son los que
 * después evalúan las anotaciones @RolesAllowed en cualquier EJB (ver
 * ComercioService.eliminarComercio).
 *
 * DE DÓNDE SALEN LAS CUENTAS (ADR-031): el dominio de seguridad de la
 * aplicación (ApplicationDomain) busca primero en la tabla "usuarios" de
 * la base compartida, con un realm JDBC que valida el hash bcrypt, y solo
 * si la cuenta no está ahí, en los archivos de add-user.sh de esta máquina
 * (el administrador inicial y las cuentas viejas). Así, una cuenta creada
 * desde cualquier WildFly del equipo entra en todos. La configuración la
 * deja scripts/configurar-realm-compartido.cli.
 */

import com.rabbit.infraestructura.Mensajes;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
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

    @Inject
    private SesionBean sesion;

    @Inject
    private LimiteDeIntentos limite;

    // Autentica contra el ApplicationRealm vía la API estándar de Servlet
    // (ver el porqué en el comentario de clase) y, si funciona, redirige
    // al primer listado protegido de la app.
    public void login() throws IOException {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        HttpServletRequest request = (HttpServletRequest) facesContext.getExternalContext().getRequest();

        // Fuerza bruta: con demasiados intentos fallidos, ni se consulta al realm.
        java.time.Duration espera = limite.esperaPara(username);
        if (!espera.isZero()) {
            long minutos = Math.max(1, (espera.getSeconds() + 59) / 60);
            // 429 Too Many Requests: la pantalla igual se muestra con el aviso.
            facesContext.getExternalContext().setResponseStatus(429);
            facesContext.getExternalContext().setResponseHeader("Retry-After", String.valueOf(espera.getSeconds() + 1));
            Mensajes.error("Demasiados intentos fallidos. Probá de nuevo en " + minutos
                    + (minutos == 1 ? " minuto." : " minutos."));
            return;
        }

        try {
            request.login(username, password);
            limite.registrarExito(username);
            // Session fixation: la sesión autenticada no conserva el ID
            // que tenía antes del login (que un tercero pudo haber fijado).
            request.changeSessionId();
            // Cada tipo de usuario entra a su propia pantalla (ver
            // SesionBean.getPaginaInicio). Un usuario sin acceso web (el
            // del ERP, que solo usa la API) no queda logueado.
            String inicio = sesion.getPaginaInicio();
            if (inicio == null) {
                request.logout();
                request.getSession().invalidate();
                Mensajes.error("Este usuario no tiene acceso a la aplicación web");
                return;
            }
            facesContext.getExternalContext().redirect(request.getContextPath() + inicio);
        } catch (ServletException e) {
            limite.registrarFallo(username);
            Mensajes.error("Usuario o contraseña incorrectos");
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

    // Pedido de cuenta: no hay alta abierta (ver UsuarioService), así que
    // quien no tiene cuenta escribe a Rabbit y un administrador la crea a
    // mano. La dirección se configura con la system property
    // rabbit.contacto.mail, como las de AvisosPorMail.
    public String getContactoMail() {
        return System.getProperty("rabbit.contacto.mail", "altas_rabbit@gmail.com");
    }

    // mailto: con asunto y cuerpo ya escritos, para que el primer mail
    // traiga lo que el administrador necesita para dar el alta.
    public String getContactoMailto() {
        String asunto = "Solicitud de cuenta en Rabbit";
        String cuerpo = "Hola, quiero una cuenta en Rabbit.\n\n"
                + "Soy (comercio / repartidor):\n"
                + "Nombre o razón social:\n"
                + "CUIT / DNI:\n"
                + "Teléfono:\n";
        return "mailto:" + getContactoMail() + "?subject=" + codificar(asunto) + "&body=" + codificar(cuerpo);
    }

    // URLEncoder codifica para formularios (espacio = "+"); en un mailto:
    // el espacio va como %20.
    private static String codificar(String texto) {
        return java.net.URLEncoder.encode(texto, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
