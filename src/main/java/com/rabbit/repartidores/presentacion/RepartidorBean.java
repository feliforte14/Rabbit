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
import com.rabbit.ruteo.dto.ZonaDTO;
import com.rabbit.ruteo.negocio.IZonas;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named
@ViewScoped
public class RepartidorBean implements Serializable {

    @Inject
    private IGestionRepartidores service;

    // Las zonas son del componente Ruteo: se ofrecen para elegir dónde reparte.
    @Inject
    private IZonas zonas;

    private List<RepartidorDTO> repartidores;
    private List<ZonaDTO> listaZonas;
    private Map<Long, String> nombresZonas;
    private DatosRepartidorDTO nuevoRepartidor = new DatosRepartidorDTO();
    private Long idRepartidorACambiar;
    private Long idZonaNueva;

    @PostConstruct
    public void cargar() {
        repartidores = service.listarTodos();
        List<ZonaDTO> todas = zonas.listarTodas();
        listaZonas = todas.stream().filter(ZonaDTO::isActiva).collect(Collectors.toList());
        nombresZonas = todas.stream().collect(Collectors.toMap(ZonaDTO::getId, ZonaDTO::getNombre));
    }

    public String nombreZona(Long idZona) {
        if (idZona == null) {
            return "Sin zona fija";
        }
        return nombresZonas.getOrDefault(idZona, "Zona " + idZona);
    }

    public void cambiarZona() {
        try {
            service.asignarZona(idRepartidorACambiar, idZonaNueva);
            Mensajes.info("Zona actualizada");
            idRepartidorACambiar = null;
            idZonaNueva = null;
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
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
    public List<ZonaDTO> getListaZonas() { return listaZonas; }
    public Long getIdRepartidorACambiar() { return idRepartidorACambiar; }
    public void setIdRepartidorACambiar(Long idRepartidorACambiar) { this.idRepartidorACambiar = idRepartidorACambiar; }
    public Long getIdZonaNueva() { return idZonaNueva; }
    public void setIdZonaNueva(Long idZonaNueva) { this.idZonaNueva = idZonaNueva; }
    public DatosRepartidorDTO getNuevoRepartidor() { return nuevoRepartidor; }
    public void setNuevoRepartidor(DatosRepartidorDTO nuevoRepartidor) { this.nuevoRepartidor = nuevoRepartidor; }
}
