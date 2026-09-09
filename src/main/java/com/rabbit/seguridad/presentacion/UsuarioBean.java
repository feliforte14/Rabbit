package com.rabbit.seguridad.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 *
 * NOTA DE ALCANCE: el alta de usuarios queda abierta (cualquiera puede
 * entrar a usuarios.xhtml y crearse una cuenta, incluso ADMINISTRADOR)
 * porque el TP necesita alguna forma de bootstrapear el primer
 * administrador sin tocar la base a mano. En un sistema real esta
 * pantalla estaría, como mínimo, detrás de @RolesAllowed("ADMINISTRADOR")
 * — igual que eliminarComercio.
 */

import com.rabbit.seguridad.dto.DatosUsuarioDTO;
import com.rabbit.seguridad.dto.UsuarioDTO;
import com.rabbit.seguridad.negocio.IConsultaUsuarios;
import com.rabbit.seguridad.negocio.IRegistroUsuarios;
import com.rabbit.seguridad.negocio.ValidacionException;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class UsuarioBean implements Serializable {

    @Inject
    private IRegistroUsuarios registro;

    @Inject
    private IConsultaUsuarios consulta;

    private List<UsuarioDTO> usuarios;
    private DatosUsuarioDTO nuevoUsuario = new DatosUsuarioDTO();

    // @PostConstruct: corre una sola vez al crear el Bean, así la tabla ya
    // llega llena en el primer render de usuarios.xhtml.
    @PostConstruct
    public void cargar() {
        usuarios = consulta.listarTodos();
    }

    // Alta de un usuario nuevo: UsuarioService lo persiste (con el
    // password ya hasheado, ver PasswordUtil) y lo sincroniza contra el
    // ApplicationRealm de WildFly (ver ApplicationRealmSync) para que
    // pueda loguearse.
    public void registrar() {
        try {
            registro.registrarUsuario(nuevoUsuario);
            mensaje(FacesMessage.SEVERITY_INFO, "Usuario registrado correctamente");
            nuevoUsuario = new DatosUsuarioDTO();
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    // Baja lógica: el usuario deja de poder autenticarse (ver Usuario.activo).
    public void darDeBaja(Long id) {
        try {
            registro.darDeBaja(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Usuario dado de baja");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    // Helper para publicar un FacesMessage global (sin componente asociado)
    // — lo consume <h:messages> en usuarios.xhtml.
    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public List<UsuarioDTO> getUsuarios() { return usuarios; }
    public DatosUsuarioDTO getNuevoUsuario() { return nuevoUsuario; }
    public void setNuevoUsuario(DatosUsuarioDTO nuevoUsuario) { this.nuevoUsuario = nuevoUsuario; }

    // Los roles posibles, para el desplegable del formulario de alta.
    public com.rabbit.seguridad.datos.model.Rol[] getRoles() { return com.rabbit.seguridad.datos.model.Rol.values(); }
}
