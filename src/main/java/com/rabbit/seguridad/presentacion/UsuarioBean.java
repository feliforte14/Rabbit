package com.rabbit.seguridad.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 *
 * ALCANCE DE LA PANTALLA: usuarios.xhtml es solo para ADMINISTRADOR
 * (SesionBean.exigirAdministrador): padrón, alta con elección de rol y
 * baja. No hay alta pública; el primer administrador se crea con
 * add-user.sh. La restricción real la impone UsuarioService con
 * @RolesAllowed; el guardián de la página solo evita mostrarla.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.seguridad.dto.DatosUsuarioDTO;
import com.rabbit.seguridad.dto.UsuarioDTO;
import com.rabbit.seguridad.negocio.IConsultaUsuarios;
import com.rabbit.seguridad.negocio.IRegistroUsuarios;
import com.rabbit.seguridad.negocio.ValidacionException;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJBAccessException;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Named
@ViewScoped
public class UsuarioBean implements Serializable {

    @Inject
    private IRegistroUsuarios registro;

    @Inject
    private IConsultaUsuarios consulta;

    // Para asociar una cuenta COMERCIO o REPARTIDOR a quien representa.
    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IGestionRepartidores repartidores;

    private List<ComercioDTO> listaComercios;
    private List<RepartidorDTO> listaRepartidores;

    private List<UsuarioDTO> usuarios;
    private DatosUsuarioDTO nuevoUsuario = nuevoUsuarioVacio();

    // @PostConstruct: corre una sola vez al crear el Bean, así la tabla ya
    // llega llena en el primer render de usuarios.xhtml.
    @PostConstruct
    public void cargar() {
        usuarios = isAdmin() ? consulta.listarTodos() : Collections.emptyList();
        listaComercios = comercios.listarTodos();
        listaRepartidores = repartidores.listarTodos();
    }

    // Alta de un usuario nuevo: UsuarioService lo persiste (con el
    // password ya hasheado, ver PasswordUtil) y lo sincroniza contra el
    // ApplicationRealm de WildFly (ver ApplicationRealmSync) para que
    // pueda loguearse.
    public void registrar() {
        try {
            registro.registrarUsuario(nuevoUsuario);
            Mensajes.info("Usuario registrado correctamente");
            nuevoUsuario = nuevoUsuarioVacio();
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBAccessException e) {
            // @RolesAllowed("ADMINISTRADOR") en UsuarioService.registrarUsuario.
            Mensajes.error("Solo un administrador puede registrar usuarios.");
        }
    }

    // Baja lógica: el usuario deja de poder autenticarse (ver Usuario.activo).
    public void darDeBaja(Long id) {
        try {
            registro.darDeBaja(id);
            Mensajes.info("Usuario dado de baja");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBAccessException e) {
            // @RolesAllowed("ADMINISTRADOR") en UsuarioService.darDeBaja.
            Mensajes.error("No tenés permisos para dar de baja usuarios. Iniciá sesión como administrador.");
        }
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public List<UsuarioDTO> getUsuarios() { return usuarios; }
    public DatosUsuarioDTO getNuevoUsuario() { return nuevoUsuario; }
    public void setNuevoUsuario(DatosUsuarioDTO nuevoUsuario) { this.nuevoUsuario = nuevoUsuario; }

    // Quien mira es ADMINISTRADOR: ve el padrón y puede dar de baja.
    public boolean isAdmin() {
        return FacesContext.getCurrentInstance().getExternalContext().isUserInRole("ADMINISTRADOR");
    }

    // El formulario arranca con Operador elegido, el tipo más común.
    private static DatosUsuarioDTO nuevoUsuarioVacio() {
        DatosUsuarioDTO datos = new DatosUsuarioDTO();
        datos.rol = com.rabbit.seguridad.datos.model.Rol.OPERADOR;
        return datos;
    }

    // A quién representa cada cuenta, para la columna del padrón.
    public String representado(UsuarioDTO u) {
        if (u.getIdComercio() != null) {
            return listaComercios.stream().filter(c -> c.getId().equals(u.getIdComercio()))
                    .map(ComercioDTO::getNombre).findFirst().orElse("Comercio " + u.getIdComercio());
        }
        if (u.getIdRepartidor() != null) {
            return listaRepartidores.stream().filter(r -> r.getId().equals(u.getIdRepartidor()))
                    .map(RepartidorDTO::getNombre).findFirst().orElse("Repartidor " + u.getIdRepartidor());
        }
        return "Rabbit";
    }

    public List<ComercioDTO> getListaComercios() { return listaComercios; }
    public List<RepartidorDTO> getListaRepartidores() { return listaRepartidores; }

    // Los roles posibles, para el desplegable del formulario de alta.
    public com.rabbit.seguridad.datos.model.Rol[] getRoles() { return com.rabbit.seguridad.datos.model.Rol.values(); }
}
