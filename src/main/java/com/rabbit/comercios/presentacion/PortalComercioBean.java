package com.rabbit.comercios.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — el portal del comercio
 * (mis-pedidos.xhtml y mi-stock.xhtml): sus pedidos con el estado de cada
 * uno, los avisos que le mandó Rabbit y su stock consignado.
 *
 * Nunca recibe el ID del comercio: cada servicio lo saca de la identidad
 * autenticada (ver IContextoUsuario), así que un comercio no puede ver
 * datos de otro aunque manipule la página.
 */

import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.notificaciones.dto.NotificacionDTO;
import com.rabbit.notificaciones.negocio.INotificaciones;
import com.rabbit.pagos.dto.CobroDTO;
import com.rabbit.pagos.negocio.IConsultaCobros;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.seguridad.negocio.ValidacionException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Named
@ViewScoped
public class PortalComercioBean implements Serializable {

    private static final int AVISOS = 15;

    @Inject
    private ISeguimientoPedido seguimiento;

    @Inject
    private INotificaciones notificaciones;

    @Inject
    private IConsultaStock stock;

    @Inject
    private IConsultaCobros cobros;

    @Inject
    private IGestionRepartidores repartidores;

    private List<PedidoDTO> pedidos;
    private List<NotificacionDTO> avisos;
    private List<ItemInventarioDTO> items;
    private Map<Long, String> nombresDepositos;

    // Una vista carga solo lo que muestra (ver cargarPedidos / cargarStock
    // en el f:viewAction de cada página).
    public void cargarPedidos() {
        try {
            pedidos = seguimiento.listarPedidosDelComercioActual();
            avisos = notificaciones.listarDelComercioActual(AVISOS);
        } catch (ValidacionException e) {
            pedidos = List.of();
            avisos = List.of();
            mensaje(e.getMessage());
        }
    }

    public void cargarStock() {
        try {
            items = stock.listarStockDelComercioActual();
        } catch (ValidacionException e) {
            items = List.of();
            mensaje(e.getMessage());
        }
        nombresDepositos = stock.listarDepositos().stream()
                .collect(Collectors.toMap(DepositoDTO::getId, DepositoDTO::getNombre));
    }

    public long contarPedidos(String estado) {
        return pedidos.stream().filter(p -> estado.equals(p.getEstado())).count();
    }

    public int getUnidadesLibres() {
        return items.stream().mapToInt(i -> i.cantidadLibre).sum();
    }

    public int getUnidadesReservadas() {
        return items.stream().mapToInt(i -> i.cantidadReservada).sum();
    }

    public String estadoCobro(Long idPedido) {
        CobroDTO cobro = cobros.obtenerCobroDePedido(idPedido);
        return cobro != null ? cobro.estado : null;
    }

    public String nombreRepartidor(Long idRepartidor) {
        RepartidorDTO r = repartidores.obtenerRepartidor(idRepartidor);
        return r != null ? r.getNombre() : "—";
    }

    public String nombreDeposito(Long idDeposito) {
        return nombresDepositos.getOrDefault(idDeposito, "Depósito " + idDeposito);
    }

    private void mensaje(String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, texto, null));
    }

    public List<PedidoDTO> getPedidos() { return pedidos; }
    public List<NotificacionDTO> getAvisos() { return avisos; }
    public List<ItemInventarioDTO> getItems() { return items; }
}
