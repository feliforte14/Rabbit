package com.logiRed.comercios.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - Jakarta Faces / JSF)
 *
 * Un Managed Bean es el equivalente JSF a un "Controller": conecta la vista
 * (los .xhtml en webapp/) con la capa de Negocio. Los componentes de la vista
 * (h:dataTable, h:inputText, h:commandButton) leen y escriben directamente
 * sobre los atributos públicos de este bean vía Expression Language (#{...}).
 *
 * @Named lo expone a las vistas como "comercioBean".
 * @ViewScoped mantiene el estado del bean mientras el usuario interactúa
 * con la misma vista (por ejemplo, mientras completa el formulario de alta),
 * y lo descarta al navegar a otra página — evita recargar todo en cada request
 * sin mantener el estado indefinidamente como haría @SessionScoped.
 *
 * Esta capa NO tiene lógica de negocio: valida formato mínimo de la UI
 * y delega toda decisión real a ComercioService.
 */

import com.logiRed.comercios.dto.ComercioDTO;
import com.logiRed.comercios.dto.DatosComercioDTO;
import com.logiRed.comercios.negocio.ComercioService;

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
public class ComercioBean implements Serializable {

    @Inject
    private ComercioService service;

    private List<ComercioDTO> comercios;

    // Campos que se bindean con el formulario de alta (comercio-form.xhtml)
    private DatosComercioDTO nuevoComercio = new DatosComercioDTO();

    @PostConstruct
    public void cargar() {
        comercios = service.listarTodos();
    }

    public void registrar() {
        service.registrarComercio(nuevoComercio);
        mensaje(FacesMessage.SEVERITY_INFO, "Comercio registrado correctamente");
        nuevoComercio = new DatosComercioDTO();
        cargar();
    }

    public void darDeBaja(Long id) {
        service.darDeBajaComercio(id);
        mensaje(FacesMessage.SEVERITY_INFO, "Comercio dado de baja");
        cargar();
    }

    public void eliminar(Long id) {
        service.eliminarComercio(id);
        mensaje(FacesMessage.SEVERITY_INFO, "Comercio eliminado");
        cargar();
    }

    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

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
