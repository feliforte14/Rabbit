package com.rabbit.inventario.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver PuntoPickingBean para el
 * mismo patrón: administra los ítems de UN depósito puntual, identificado
 * por idDeposito (llega como parámetro de la URL vía <f:viewParam>).
 *
 * ES LA VISTA DE OPERADOR DE RABBIT: muestra TODO el stock del depósito,
 * de todos los comercios, porque quien administra el galpón necesita ver
 * qué hay adentro sin importar de quién sea. El filtro por comercio es
 * opcional, para poder aislar la consignación de uno solo.
 *
 * Es la contracara de ReservaBean y PedidoBean, donde se opera EN NOMBRE
 * DE un comercio y ahí el filtrado sí es obligatorio.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.dto.DatosItemInventarioDTO;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.inventario.negocio.ValidacionException;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
@ViewScoped
public class ItemInventarioBean implements Serializable {

    @Inject
    private IConsultaStock service;

    // Otro componente, por su interfaz de solo lectura: se necesita para
    // ofrecer los comercios y para mostrar el nombre del dueño de cada
    // ítem (el ítem solo guarda el ID, ver ItemInventario.idComercio).
    @Inject
    private IConsultaComercios comercios;

    private Long idDeposito;
    private DepositoDTO deposito;
    private List<ItemInventarioDTO> items;
    private List<ComercioDTO> listaComercios;
    private Map<Long, String> nombresComercio = new HashMap<>();

    /** null = ver el stock de todos los comercios (vista de operador). */
    private Long idComercioFiltro;

    private DatosItemInventarioDTO nuevoItem = new DatosItemInventarioDTO();

    // Se invoca vía <f:viewAction> apenas idDeposito queda seteado por el
    // <f:viewParam> de items.xhtml (no hay @PostConstruct porque en ese
    // momento del ciclo de vida idDeposito todavía no llegó).
    public void cargar() {
        deposito = service.obtenerDeposito(idDeposito);
        listaComercios = comercios.listarTodos();

        nombresComercio = new HashMap<>();
        for (ComercioDTO c : listaComercios) {
            nombresComercio.put(c.getId(), c.getNombre());
        }

        items = (idComercioFiltro != null)
                ? service.listarItemsPorComercioYDeposito(idComercioFiltro, idDeposito)
                : service.listarItemsPorDeposito(idDeposito);
    }

    /** Se dispara por ajax al cambiar el filtro de comercio. */
    public void filtrar() {
        cargar();
    }

    /**
     * Nombre del comercio dueño de un ítem, para no mostrar un ID pelado.
     * Los ítems anteriores a esta regla no tienen dueño asignado.
     */
    public String nombreComercio(Long idComercio) {
        if (idComercio == null) {
            return "— sin asignar —";
        }
        return nombresComercio.getOrDefault(idComercio, "Comercio " + idComercio);
    }

    // Carga stock nuevo en idDeposito, a nombre del comercio elegido en
    // nuevoItem (registra la consignación, ver DatosItemInventarioDTO).
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

    // Helper para publicar un FacesMessage global (sin componente asociado)
    // — lo consume <h:messages> en items.xhtml.
    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF),
    // incluido idDeposito, que <f:viewParam> escribe vía su setter.
    public Long getIdDeposito() { return idDeposito; }
    public void setIdDeposito(Long idDeposito) { this.idDeposito = idDeposito; }
    public DepositoDTO getDeposito() { return deposito; }
    public List<ItemInventarioDTO> getItems() { return items; }
    public List<ComercioDTO> getListaComercios() { return listaComercios; }
    public Long getIdComercioFiltro() { return idComercioFiltro; }
    public void setIdComercioFiltro(Long idComercioFiltro) { this.idComercioFiltro = idComercioFiltro; }
    public DatosItemInventarioDTO getNuevoItem() { return nuevoItem; }
    public void setNuevoItem(DatosItemInventarioDTO nuevoItem) { this.nuevoItem = nuevoItem; }
}
