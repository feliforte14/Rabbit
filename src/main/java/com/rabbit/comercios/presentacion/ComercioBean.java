package com.rabbit.comercios.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - Jakarta Faces / JSF)
 *
 * Un Managed Bean es el equivalente JSF a un "Controller": conecta la vista
 * (los .xhtml en webapp/) con la capa de Negocio. Los componentes de la vista
 * (h:dataTable, h:inputText, h:commandButton) leen y escriben directamente
 * sobre los atributos públicos de este bean vía Expression Language (#{...}).
 *
 * @Named lo expone a las vistas como "comercioBean".
 * @ViewScoped guarda el estado del bean solo mientras el usuario se queda
 * en la misma página (por ejemplo, mientras completa el formulario), y lo
 * descarta apenas navega a otra. Ni se recrea en cada clic, ni queda guardado
 * para siempre como pasaría con @SessionScoped.
 *
 * Esta capa NO tiene lógica de negocio: valida formato mínimo de la UI
 * y delega toda decisión real al componente ServicioDeComercios.
 *
 * Depende de las INTERFACES del componente (IRegistroComercios para las
 * operaciones de escritura, IConsultaComercios para las de lectura), no de
 * la clase que las implementa. La vista no sabe —ni necesita saber— que
 * del otro lado hay un EJB llamado ComercioService.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.DatosComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.comercios.negocio.IRegistroComercios;
import com.rabbit.comercios.negocio.ValidacionException;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJBAccessException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class ComercioBean implements Serializable {

    // Contrato de escritura: alta, baja, reactivación y eliminación.
    @Inject
    private IRegistroComercios registro;

    // Contrato de lectura: el listado que se muestra en la tabla.
    @Inject
    private IConsultaComercios consulta;

    private List<ComercioDTO> comercios;

    // Campos que se bindean con el formulario de alta (comercios.xhtml)
    private DatosComercioDTO nuevoComercio = new DatosComercioDTO();

    // @PostConstruct: corre una sola vez, apenas el contenedor termina de
    // inyectar registro/consulta — así la tabla ya llega llena en el primer
    // render de la página, sin esperar una acción del usuario.
    @PostConstruct
    public void cargar() {
        comercios = consulta.listarTodos();
    }

    // Alta de un comercio nuevo a partir de nuevoComercio (bindeado al
    // formulario). Si ComercioService rechaza los datos (CUIT inválido,
    // duplicado, etc.) el mensaje de negocio se muestra tal cual llega en
    // la excepción, sin volcar el formulario.
    public void registrar() {
        try {
            registro.registrarComercio(nuevoComercio);
            mensaje(FacesMessage.SEVERITY_INFO, "Comercio registrado correctamente");
            nuevoComercio = new DatosComercioDTO();
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    // Baja lógica: el comercio sigue en la BD pero deja de operar (ver
    // Comercio.activo). Recarga el listado para reflejar el nuevo estado.
    public void darDeBaja(Long id) {
        try {
            registro.darDeBajaComercio(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Comercio dado de baja");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    // Reactiva un comercio dado de baja.
    public void reactivar(Long id) {
        try {
            registro.reactivarComercio(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Comercio reactivado");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    // Eliminación FÍSICA (no baja lógica): borra el comercio y, en cascada,
    // sus puntos de picking. Es la única operación del componente protegida
    // con @RolesAllowed("ADMINISTRADOR") a nivel de EJB — por eso, además
    // del error de negocio, hay que atrapar el rechazo de permisos.
    public void eliminar(Long id) {
        try {
            registro.eliminarComercio(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Comercio eliminado");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        } catch (EJBAccessException e) {
            // Lanzada por el contenedor cuando @RolesAllowed("ADMINISTRADOR") rechaza
            // al caller — ni siquiera llegó a ejecutarse el método.
            mensaje(FacesMessage.SEVERITY_ERROR,
                    "No tenés permisos para eliminar comercios. Iniciá sesión como administrador.");
        }
    }

    // Helper para publicar un FacesMessage global (sin componente asociado,
    // por eso el primer argumento a addMessage es null) — lo consume
    // <h:messages globalOnly="true"> en comercios.xhtml.
    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF) para
    // leer y escribir estos campos desde comercios.xhtml.
    public List<ComercioDTO> getComercios() {
        return comercios;
    }

    public DatosComercioDTO getNuevoComercio() {
        return nuevoComercio;
    }

    public void setNuevoComercio(DatosComercioDTO nuevoComercio) {
        this.nuevoComercio = nuevoComercio;
    }
}
