package com.rabbit.repartidores.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 */

import com.rabbit.repartidores.dto.DatosRepartidorDTO;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.repartidores.negocio.ValidacionException;
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
public class RepartidorBean implements Serializable {

    @Inject
    private IGestionRepartidores service;

    private List<RepartidorDTO> repartidores;
    private DatosRepartidorDTO nuevoRepartidor = new DatosRepartidorDTO();

    @PostConstruct
    public void cargar() {
        repartidores = service.listarTodos();
    }

    public void registrar() {
        try {
            service.registrarRepartidor(nuevoRepartidor);
            mensaje(FacesMessage.SEVERITY_INFO, "Repartidor registrado correctamente");
            nuevoRepartidor = new DatosRepartidorDTO();
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    public List<RepartidorDTO> getRepartidores() { return repartidores; }
    public DatosRepartidorDTO getNuevoRepartidor() { return nuevoRepartidor; }
    public void setNuevoRepartidor(DatosRepartidorDTO nuevoRepartidor) { this.nuevoRepartidor = nuevoRepartidor; }
}
