package com.rabbit.inventario.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver SucursalBean para el
 * mismo patrón: administra los ítems de UN depósito puntual, identificado
 * por idDeposito (llega como parámetro de la URL vía <f:viewParam>).
 */

import com.rabbit.inventario.dto.DatosItemInventarioDTO;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.InventarioService;
import com.rabbit.inventario.negocio.ValidacionException;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class ItemInventarioBean implements Serializable {

    @Inject
    private InventarioService service;

    private Long idDeposito;
    private DepositoDTO deposito;
    private List<ItemInventarioDTO> items;

    private DatosItemInventarioDTO nuevoItem = new DatosItemInventarioDTO();

    public void cargar() {
        deposito = service.obtenerDeposito(idDeposito);
        items = service.listarItemsPorDeposito(idDeposito);
    }

    public void registrar() {
        try {
            service.registrarItem(idDeposito, nuevoItem);
            mensaje(FacesMessage.SEVERITY_INFO, "Stock cargado correctamente");
            nuevoItem = new DatosItemInventarioDTO();
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    public Long getIdDeposito() { return idDeposito; }
    public void setIdDeposito(Long idDeposito) { this.idDeposito = idDeposito; }
    public DepositoDTO getDeposito() { return deposito; }
    public List<ItemInventarioDTO> getItems() { return items; }
    public DatosItemInventarioDTO getNuevoItem() { return nuevoItem; }
    public void setNuevoItem(DatosItemInventarioDTO nuevoItem) { this.nuevoItem = nuevoItem; }
}
