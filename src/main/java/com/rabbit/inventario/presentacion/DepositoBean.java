package com.rabbit.inventario.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 */

import com.rabbit.inventario.dto.DatosDepositoDTO;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.negocio.InventarioService;
import com.rabbit.inventario.negocio.ValidacionException;

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
public class DepositoBean implements Serializable {

    @Inject
    private InventarioService service;

    private List<DepositoDTO> depositos;
    private DatosDepositoDTO nuevoDeposito = new DatosDepositoDTO();

    @PostConstruct
    public void cargar() {
        depositos = service.listarDepositos();
    }

    public void registrar() {
        try {
            service.registrarDeposito(nuevoDeposito);
            mensaje(FacesMessage.SEVERITY_INFO, "Depósito registrado correctamente");
            nuevoDeposito = new DatosDepositoDTO();
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    public List<DepositoDTO> getDepositos() { return depositos; }
    public DatosDepositoDTO getNuevoDeposito() { return nuevoDeposito; }
    public void setNuevoDeposito(DatosDepositoDTO nuevoDeposito) { this.nuevoDeposito = nuevoDeposito; }
}
