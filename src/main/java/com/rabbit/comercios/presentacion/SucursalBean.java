package com.rabbit.comercios.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - Jakarta Faces / JSF)
 *
 * Administra las sucursales de UN comercio puntual (identificado por
 * idComercio, que llega como parámetro de la URL vía <f:viewParam>).
 * No tiene lógica de negocio: delega todo a ComercioService.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.DatosSucursalDTO;
import com.rabbit.comercios.dto.SucursalDTO;
import com.rabbit.comercios.negocio.ComercioService;
import com.rabbit.comercios.negocio.ValidacionException;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class SucursalBean implements Serializable {

    @Inject
    private ComercioService service;

    private Long idComercio;
    private ComercioDTO comercio;
    private List<SucursalDTO> sucursales;

    private DatosSucursalDTO nuevaSucursal = new DatosSucursalDTO();

    public void cargar() {
        comercio = service.obtenerComercio(idComercio);
        sucursales = service.listarSucursalesDeComercio(idComercio);
    }

    public void registrar() {
        try {
            service.registrarSucursal(idComercio, nuevaSucursal);
            mensaje(FacesMessage.SEVERITY_INFO, "Sucursal registrada correctamente");
            nuevaSucursal = new DatosSucursalDTO();
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    public void darDeBaja(Long id) {
        try {
            service.darDeBajaSucursal(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Sucursal dada de baja");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    public void reactivar(Long id) {
        try {
            service.reactivarSucursal(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Sucursal reactivada");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public ComercioDTO getComercio() { return comercio; }
    public List<SucursalDTO> getSucursales() { return sucursales; }
    public DatosSucursalDTO getNuevaSucursal() { return nuevaSucursal; }
    public void setNuevaSucursal(DatosSucursalDTO nuevaSucursal) { this.nuevaSucursal = nuevaSucursal; }
}
