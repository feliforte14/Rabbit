package com.rabbit.inventario.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF)
 *
 * Historial de reservas: qué pasó con cada hold que abrió el componente
 * stateful. Es la contracara de ReservaBean — aquella muestra la reserva
 * EN CURSO (el estado conversacional vivo), esta muestra todas las que ya
 * terminaron y cómo.
 *
 * POR QUÉ @ViewScoped Y NO @SessionScoped
 * Al revés que ReservaBean: acá no hay ninguna conversación que sostener.
 * Es una consulta de solo lectura, su estado (los filtros elegidos) dura
 * lo que el usuario se queda en la pantalla y no tiene por qué sobrevivir
 * a irse a otra. Es el scope por defecto del resto de los Beans de la app.
 *
 * Este Bean no tiene reglas de negocio: junta los filtros, se los pasa a
 * IConsultaStock y muestra lo que devuelve.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.inventario.datos.model.EstadoReserva;
import com.rabbit.inventario.dto.DepositoDTO;
import com.rabbit.inventario.dto.FiltroHistorialDTO;
import com.rabbit.inventario.dto.ReservaStockDTO;
import com.rabbit.inventario.negocio.IConsultaStock;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
@ViewScoped
public class HistorialReservasBean implements Serializable {

    @Inject
    private IConsultaStock stock;

    @Inject
    private IConsultaComercios comercios;

    private FiltroHistorialDTO filtro = new FiltroHistorialDTO();
    private List<ReservaStockDTO> reservas;

    private List<ComercioDTO> listaComercios;
    private List<DepositoDTO> listaDepositos;
    private Map<Long, String> nombresComercio = new HashMap<>();
    private Map<Long, String> nombresDeposito = new HashMap<>();

    @PostConstruct
    public void cargar() {
        listaComercios = comercios.listarTodos();
        listaDepositos = stock.listarDepositos();

        nombresComercio = new HashMap<>();
        for (ComercioDTO c : listaComercios) {
            nombresComercio.put(c.getId(), c.getNombre());
        }
        nombresDeposito = new HashMap<>();
        for (DepositoDTO d : listaDepositos) {
            nombresDeposito.put(d.getId(), d.getNombre());
        }

        buscar();
    }

    /** Aplica los filtros actuales. Sin ninguno cargado, trae todo. */
    public void buscar() {
        reservas = stock.listarHistorialReservas(filtro);
    }

    public void limpiar() {
        filtro = new FiltroHistorialDTO();
        buscar();
    }

    /** Los estados posibles, para el desplegable. */
    public EstadoReserva[] getEstados() {
        return EstadoReserva.values();
    }

    public String nombreComercio(Long id) {
        if (id == null) {
            return "—";
        }
        return nombresComercio.getOrDefault(id, "Comercio " + id);
    }

    public String nombreDeposito(Long id) {
        if (id == null) {
            return "—";
        }
        return nombresDeposito.getOrDefault(id, "Depósito " + id);
    }

    /**
     * Clase CSS por estado, para que el desenlace se lea de un vistazo en
     * la tabla en vez de tener que leer palabra por palabra.
     */
    public String claseEstado(String estado) {
        if (estado == null) {
            return "";
        }
        switch (estado) {
            case "VIGENTE":    return "estado-vigente";
            case "CONFIRMADA": return "estado-confirmada";
            case "LIBERADA":   return "estado-liberada";
            case "EXPIRADA":   return "estado-expirada";
            case "DEVUELTA":   return "estado-devuelta";
            default:           return "";
        }
    }

    public boolean isHayResultados() {
        return reservas != null && !reservas.isEmpty();
    }

    public int getCantidadResultados() {
        return reservas != null ? reservas.size() : 0;
    }

    /** Total de unidades involucradas en lo que se está mostrando. */
    public int getTotalUnidades() {
        if (reservas == null) {
            return 0;
        }
        return reservas.stream().mapToInt(ReservaStockDTO::getCantidad).sum();
    }

    public FiltroHistorialDTO getFiltro() { return filtro; }
    public void setFiltro(FiltroHistorialDTO filtro) { this.filtro = filtro; }
    public List<ReservaStockDTO> getReservas() { return reservas; }
    public List<ComercioDTO> getListaComercios() { return listaComercios; }
    public List<DepositoDTO> getListaDepositos() { return listaDepositos; }
}
