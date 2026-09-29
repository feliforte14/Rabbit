package com.rabbit.ruteo.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ruteo.xhtml: los pedidos
 * pendientes agrupados por zona, el despacho según la zona (de a uno o toda
 * la zona) y la gestión de las zonas. Solo personal de Rabbit.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.infraestructura.Mensajes;
import com.rabbit.ruteo.datos.model.CoberturaZona;
import com.rabbit.ruteo.dto.DatosZonaDTO;
import com.rabbit.ruteo.dto.GrupoZonaDTO;
import com.rabbit.ruteo.dto.ResultadoDespachoDTO;
import com.rabbit.ruteo.dto.ZonaDTO;
import com.rabbit.ruteo.negocio.IRuteo;
import com.rabbit.ruteo.negocio.IZonas;
import com.rabbit.ruteo.negocio.ValidacionException;
import com.rabbit.transportistas.dto.TransportistaDTO;
import com.rabbit.transportistas.negocio.IGestionTransportistas;
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
public class RuteoBean implements Serializable {

    // Cuántos pedidos se listan por grupo (el resto se cuenta).
    private static final int MAXIMO_POR_GRUPO = 20;

    @Inject
    private IRuteo ruteo;

    @Inject
    private IZonas zonas;

    @Inject
    private IGestionTransportistas transportistas;

    @Inject
    private IConsultaComercios comercios;

    private List<GrupoZonaDTO> grupos;
    private List<ZonaDTO> listaZonas;
    private List<TransportistaDTO> listaTransportistas;
    private Map<Long, String> nombresComercios;
    private Map<Long, String> nombresTransportistas;
    private List<ResultadoDespachoDTO> resultados = List.of();
    private DatosZonaDTO nuevaZona = new DatosZonaDTO();

    @PostConstruct
    public void cargar() {
        grupos = ruteo.listarPendientesPorZona();
        listaZonas = zonas.listarTodas();
        List<TransportistaDTO> todos = transportistas.listarTodos();
        listaTransportistas = todos.stream().filter(TransportistaDTO::isActivo).collect(Collectors.toList());
        nombresTransportistas = todos.stream().collect(Collectors.toMap(TransportistaDTO::getId, TransportistaDTO::getNombre));
        nombresComercios = comercios.listarTodos().stream()
                .collect(Collectors.toMap(ComercioDTO::getId, ComercioDTO::getNombre));
    }

    public void despacharPedido(Long idPedido) {
        resultados = List.of(ruteo.despacharPedido(idPedido));
        cargar();
    }

    public void despacharZona(Long idZona) {
        resultados = ruteo.despacharZona(idZona);
        if (resultados.isEmpty()) {
            Mensajes.info("La zona no tiene pedidos pendientes");
        }
        cargar();
    }

    public void registrarZona() {
        try {
            zonas.registrarZona(nuevaZona);
            Mensajes.info("Zona registrada");
            nuevaZona = new DatosZonaDTO();
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public void darDeBajaZona(Long id) {
        try {
            zonas.darDeBajaZona(id);
            Mensajes.info("Zona dada de baja: sus pedidos quedan sin zona");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public void reactivarZona(Long id) {
        try {
            zonas.reactivarZona(id);
            Mensajes.info("Zona reactivada");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public List<?> primeros(GrupoZonaDTO g) {
        return g.getPedidos().subList(0, Math.min(MAXIMO_POR_GRUPO, g.getPedidos().size()));
    }

    public int restantes(GrupoZonaDTO g) {
        return Math.max(0, g.getPedidos().size() - MAXIMO_POR_GRUPO);
    }

    public String nombreComercio(Long id) {
        return nombresComercios.getOrDefault(id, "Comercio " + id);
    }

    public String nombreTransportista(Long id) {
        return id == null ? "—" : nombresTransportistas.getOrDefault(id, "Transportista " + id);
    }

    public CoberturaZona[] getCoberturas() { return CoberturaZona.values(); }
    public List<GrupoZonaDTO> getGrupos() { return grupos; }
    public List<ZonaDTO> getListaZonas() { return listaZonas; }
    public List<TransportistaDTO> getListaTransportistas() { return listaTransportistas; }
    public List<ResultadoDespachoDTO> getResultados() { return resultados; }
    public DatosZonaDTO getNuevaZona() { return nuevaZona; }
    public void setNuevaZona(DatosZonaDTO nuevaZona) { this.nuevaZona = nuevaZona; }
}
