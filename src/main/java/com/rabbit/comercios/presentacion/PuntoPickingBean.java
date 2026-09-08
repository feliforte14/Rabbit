package com.rabbit.comercios.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - Jakarta Faces / JSF)
 *
 * Administra los puntos de picking de UN comercio puntual (identificado
 * por idComercio, que llega como parámetro de la URL vía <f:viewParam>).
 * No tiene lógica de negocio: delega todo al componente ServicioDeComercios,
 * a través de sus interfaces (IRegistroComercios para escritura,
 * IConsultaComercios para lectura) y no de la clase que las implementa.
 *
 * Renombrado desde SucursalBean (ver Sección 1.2 del documento técnico).
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.DatosPuntoPickingDTO;
import com.rabbit.comercios.dto.PuntoPickingDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.comercios.negocio.IRegistroComercios;
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
public class PuntoPickingBean implements Serializable {

    // Contrato de escritura: alta, baja y reactivación de puntos de picking.
    @Inject
    private IRegistroComercios registro;

    // Contrato de lectura: datos del comercio y su listado de puntos de picking.
    @Inject
    private IConsultaComercios consulta;

    private Long idComercio;
    private ComercioDTO comercio;
    private List<PuntoPickingDTO> puntosPicking;

    private DatosPuntoPickingDTO nuevoPuntoPicking = new DatosPuntoPickingDTO();

    public void cargar() {
        comercio = consulta.obtenerComercio(idComercio);
        puntosPicking = consulta.listarPuntosPickingDeComercio(idComercio);
    }

    public void registrar() {
        try {
            registro.registrarPuntoPicking(idComercio, nuevoPuntoPicking);
            mensaje(FacesMessage.SEVERITY_INFO, "Punto de picking registrado correctamente");
            nuevoPuntoPicking = new DatosPuntoPickingDTO();
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    public void darDeBaja(Long id) {
        try {
            registro.darDeBajaPuntoPicking(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Punto de picking dado de baja");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    public void reactivar(Long id) {
        try {
            registro.reactivarPuntoPicking(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Punto de picking reactivado");
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
    public List<PuntoPickingDTO> getPuntosPicking() { return puntosPicking; }
    public DatosPuntoPickingDTO getNuevoPuntoPicking() { return nuevoPuntoPicking; }
    public void setNuevoPuntoPicking(DatosPuntoPickingDTO nuevoPuntoPicking) { this.nuevoPuntoPicking = nuevoPuntoPicking; }
}
