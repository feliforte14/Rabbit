package com.rabbit.pedidos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 *
 * El combo comercio→depósito→ítem para "simular pedido nuevo" sigue el
 * mismo patrón en cascada que ReservaBean: getListaItems() se recalcula
 * en vivo a partir del comercio y el depósito elegidos en cada render, en
 * vez de depender de que el listener del f:ajax se haya disparado — así
 * el combo queda correcto incluso si ese postback puntual no llegó a
 * invocar el listener.
 *
 * Solo ofrece los ítems del comercio elegido: un pedido del ERP de Kiosco
 * El Sol nunca referenciaría stock de otro comercio, y si igual llegara
 * una combinación incoherente, InventarioService.reservarStock la
 * rechaza al sincronizar.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.ValidacionException;

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
public class PedidoBean implements Serializable {

    @Inject
    private IGestionPedidos gestion;

    @Inject
    private ISeguimientoPedido seguimiento;

    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IConsultaStock stock;

    private List<PedidoDTO> pedidos;
    private List<PedidoExternoDTO> pedidosExternos;
    private List<ComercioDTO> listaComercios;
    private List<DepositoDTO> listaDepositos;

    private Long idDepositoSeleccionado;
    private DatosPedidoExternoDTO nuevoPedido = new DatosPedidoExternoDTO();

    // @PostConstruct: corre una sola vez al crear el Bean, así las tres
    // tablas de pedidos.xhtml (reales, mock del ERP, combos de alta) ya
    // llegan llenas en el primer render.
    @PostConstruct
    public void cargar() {
        pedidos = seguimiento.listarTodos();
        pedidosExternos = seguimiento.listarPedidosExternos();
        listaComercios = comercios.listarTodos();
        listaDepositos = stock.listarDepositos();
    }

    /**
     * Ítems del comercio elegido — recalculado en cada render.
     *
     * El comercio es obligatorio (solo se puede pedir stock propio); el
     * depósito es un filtro opcional, igual que en ReservaBean. Sin
     * elegirlo se ve todo el stock del comercio en la red.
     */
    public List<ItemInventarioDTO> getListaItems() {
        Long idComercio = nuevoPedido.getIdComercio();
        if (idComercio == null) {
            return List.of();
        }
        return (idDepositoSeleccionado == null)
                ? stock.listarItemsPorComercio(idComercio)
                : stock.listarItemsPorComercioYDeposito(idComercio, idDepositoSeleccionado);
    }

    /** Nombre del depósito de un ítem, para distinguirlos en el desplegable. */
    public String nombreDeposito(Long id) {
        if (id == null || listaDepositos == null) {
            return "—";
        }
        return listaDepositos.stream()
                .filter(d -> d.getId().equals(id))
                .map(DepositoDTO::getNombre)
                .findFirst()
                .orElse("Depósito " + id);
    }

    /**
     * Se llama por ajax al cambiar el comercio o el depósito elegido:
     * limpia el ítem ya seleccionado, que puede haber quedado fuera de la
     * lista nueva.
     */
    public void onContextoCambiado() {
        nuevoPedido.setIdItem(null);
    }

    // Crea la fila mock "recién llegada del ERP" (PedidoExterno), NO un
    // pedido real: el pedido real lo genera SincronizadorDePedidos cuando
    // la levanta en su próxima pasada.
    public void registrarPedidoExterno() {
        try {
            gestion.registrarPedidoExterno(nuevoPedido);
            mensaje(FacesMessage.SEVERITY_INFO,
                    "Pedido simulado como recién llegado del ERP — el sincronizador lo va a tomar en su próxima pasada (máx. 1 min).");
            nuevoPedido = new DatosPedidoExternoDTO();
            idDepositoSeleccionado = null;
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    // Avanza el pedido de PENDIENTE a CONFIRMADO (ver EstadoPedido).
    public void confirmar(Long idPedido) {
        try {
            gestion.confirmarPedido(idPedido);
            mensaje(FacesMessage.SEVERITY_INFO, "Pedido confirmado");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    // Cancela el pedido y devuelve el stock que tenía comprometido (ver
    // Pedido.idReservaStock e IReservaStock.registrarDevolucion).
    public void cancelar(Long idPedido) {
        try {
            gestion.cancelarPedido(idPedido);
            mensaje(FacesMessage.SEVERITY_INFO, "Pedido cancelado — el stock volvió al disponible");
            cargar();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    // Helper para publicar un FacesMessage global (sin componente asociado)
    // — lo consume <h:messages> en pedidos.xhtml.
    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF).
    public List<PedidoDTO> getPedidos() { return pedidos; }
    public List<PedidoExternoDTO> getPedidosExternos() { return pedidosExternos; }
    public List<ComercioDTO> getListaComercios() { return listaComercios; }
    public List<DepositoDTO> getListaDepositos() { return listaDepositos; }
    public Long getIdDepositoSeleccionado() { return idDepositoSeleccionado; }
    public void setIdDepositoSeleccionado(Long idDepositoSeleccionado) { this.idDepositoSeleccionado = idDepositoSeleccionado; }
    public DatosPedidoExternoDTO getNuevoPedido() { return nuevoPedido; }
    public void setNuevoPedido(DatosPedidoExternoDTO nuevoPedido) { this.nuevoPedido = nuevoPedido; }
}
