package com.rabbit.repartidores.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.repartidores.dto.DatosRepartidorDTO;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.repartidores.negocio.ValidacionException;
import jakarta.annotation.PostConstruct;
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
            Mensajes.info("Repartidor registrado correctamente");
            nuevoRepartidor = new DatosRepartidorDTO();
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public List<RepartidorDTO> getRepartidores() { return repartidores; }
    public DatosRepartidorDTO getNuevoRepartidor() { return nuevoRepartidor; }
    public void setNuevoRepartidor(DatosRepartidorDTO nuevoRepartidor) { this.nuevoRepartidor = nuevoRepartidor; }
}
