package com.rabbit.ruteo.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — tablero de entregas del
 * personal de Rabbit (entregas.xhtml): qué pedidos tienen que salir, cuáles
 * están en viaje, con qué repartidor y de dónde a dónde.
 */

import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.negocio.IRuteo;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class EntregasBean implements Serializable {

    @Inject
    private IRuteo ruteo;

    @Inject
    private IGestionRepartidores repartidores;

    private List<HojaDeRutaDTO> entregas;
    private List<RepartidorDTO> listaRepartidores;

    @PostConstruct
    public void cargar() {
        entregas = ruteo.listarEntregasEnCurso();
        listaRepartidores = repartidores.listarTodos();
    }

    public long getPorSalir() {
        return entregas.stream().filter(e -> "CONFIRMADO".equals(e.getEstado())).count();
    }

    public long getEnCamino() {
        return entregas.stream().filter(e -> "EN_CAMINO".equals(e.getEstado())).count();
    }

    public long getRepartidoresDisponibles() {
        return listaRepartidores.stream().filter(r -> "DISPONIBLE".equals(r.getEstado())).count();
    }

    public List<HojaDeRutaDTO> getEntregas() { return entregas; }
    public List<RepartidorDTO> getListaRepartidores() { return listaRepartidores; }
}
