package com.rabbit.inventario.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF)
 *
 * Pantalla de reserva de stock: es la que hace visible el estado
 * conversacional del componente @Stateful.
 *
 * POR QUE @SessionScoped Y NO @ViewScoped
 *
 * El resto de los Beans de la app son @ViewScoped: su estado dura lo que
 * el usuario se queda en la pagina. Acá no alcanza. La reserva es una
 * conversacion que se extiende en el tiempo — el usuario reserva, se va a
 * mirar el stock a otra pantalla, vuelve y confirma. Si el Bean fuera
 * @ViewScoped, cada recarga descartaria la instancia y con ella el EJB
 * @Stateful inyectado, perdiendo la reserva en curso.
 *
 * Al ser @SessionScoped, el mismo InventarioService acompaña al usuario
 * durante toda su sesion: es el contenedor quien mantiene esa instancia
 * dedicada. Justamente lo que hay que mostrar en la defensa.
 *
 * Este Bean no tiene reglas de negocio: delega todo en IReservaStock y
 * publica los mensajes que devuelve.
 */

import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.ItemInventarioDTO;
import com.rabbit.inventario.dto.ReservaStockDTO;
import com.rabbit.inventario.negocio.IConsultaStock;
import com.rabbit.inventario.negocio.IReservaStock;
import com.rabbit.inventario.negocio.ValidacionException;
import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@SessionScoped
public class ReservaBean implements Serializable {

    // El componente stateful: esta instancia acompaña a este usuario
    // durante toda su sesion y es la que recuerda la reserva en curso.
    @Inject
    private IReservaStock reservas;

    // Mismo componente, contrato de consulta: para llenar los desplegables.
    @Inject
    private IConsultaStock stock;

    // Otro componente, por su interfaz de solo lectura.
    @Inject
    private IConsultaComercios comercios;

    private List<ComercioDTO> listaComercios;
    private List<DepositoDTO> listaDepositos;
    private List<ItemInventarioDTO> listaItems;

    private Long idComercio;
    private Long idDeposito;
    private Long idItem;
    private int cantidad = 1;

    @PostConstruct
    public void cargar() {
        listaComercios = comercios.listarTodos();
        listaDepositos = stock.listarDepositos();
        refrescarItems();
    }

    /**
     * Recarga los items al cambiar el comercio o el deposito elegido.
     *
     * Filtra por AMBOS: acá se opera EN NOMBRE DE un comercio, así que
     * mostrar stock ajeno sería ofrecer algo que reservarStock va a
     * rechazar después. La validación de fondo igual está en el negocio
     * (InventarioService.reservarStock); esto es la capa de presentación
     * evitando que el error sea siquiera posible desde la pantalla.
     */
    public void refrescarItems() {
        listaItems = (idComercio != null && idDeposito != null)
                ? stock.listarItemsPorComercioYDeposito(idComercio, idDeposito)
                : List.of();
        // El producto elegido puede haber quedado fuera de la lista nueva.
        idItem = null;
    }

    public void reservar() {
        try {
            ReservaStockDTO reserva = reservas.reservarStock(idComercio, idItem, cantidad);
            mensaje(FacesMessage.SEVERITY_INFO, "Reservaste " + reserva.getCantidad()
                    + " x " + reserva.getProducto() + ". Vence a las " + reserva.getFechaExpiracion() + ".");
            refrescarItems();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    public void confirmar() {
        try {
            reservas.confirmarReserva();
            mensaje(FacesMessage.SEVERITY_INFO, "Reserva confirmada: el stock se descontó del depósito.");
            refrescarItems();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    public void liberar() {
        try {
            reservas.liberarReserva();
            mensaje(FacesMessage.SEVERITY_INFO, "Reserva liberada: el stock volvió a estar libre.");
            refrescarItems();
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    public void extender() {
        try {
            reservas.extenderReserva();
            ReservaStockDTO r = reservas.obtenerReservaActual();
            mensaje(FacesMessage.SEVERITY_INFO, "Reserva extendida. Nuevo vencimiento: "
                    + (r != null ? r.getFechaExpiracion() : "-"));
        } catch (ValidacionException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
    }

    /** Se consulta al EJB stateful: es EL estado conversacional en pantalla. */
    public ReservaStockDTO getReservaActual() {
        return reservas.obtenerReservaActual();
    }

    public boolean isHayReserva() {
        return reservas.obtenerReservaActual() != null;
    }

    private void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }

    public List<ComercioDTO> getListaComercios() { return listaComercios; }
    public List<DepositoDTO> getListaDepositos() { return listaDepositos; }
    public List<ItemInventarioDTO> getListaItems() { return listaItems; }
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public Long getIdDeposito() { return idDeposito; }
    public void setIdDeposito(Long idDeposito) { this.idDeposito = idDeposito; }
    public Long getIdItem() { return idItem; }
    public void setIdItem(Long idItem) { this.idItem = idItem; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
