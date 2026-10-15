package com.rabbit.repartidores.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — ver ComercioBean para la
 * explicación completa de @Named/@ViewScoped, se aplica igual acá.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.repartidores.dto.DatosRepartidorDTO;
import com.rabbit.repartidores.dto.RepartidorDTO;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.repartidores.negocio.ValidacionException;
import com.rabbit.ruteo.dto.ZonaDTO;
import com.rabbit.ruteo.negocio.IZonas;
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
public class RepartidorBean implements Serializable {

    @Inject
    private IGestionRepartidores service;

    // Las zonas son del componente Ruteo: se ofrecen para elegir dónde reparte.
    @Inject
    private IZonas zonas;

    private List<RepartidorDTO> repartidores;
    private List<ZonaDTO> listaZonas;
    private Map<Long, String> nombresZonas;
    private DatosRepartidorDTO nuevoRepartidor = new DatosRepartidorDTO();
    private Long idRepartidorACambiar;
    private Long idZonaNueva;

    // Formulario "Capacidad del vehículo" (ADR-032).
    private Long idRepartidorCapacidad;
    private java.math.BigDecimal capacidadKgNueva;
    private Integer capacidadBultosNueva;

    @PostConstruct
    public void cargar() {
        repartidores = service.listarTodos();
        List<ZonaDTO> todas = zonas.listarTodas();
        listaZonas = todas.stream().filter(ZonaDTO::isActiva).collect(Collectors.toList());
        nombresZonas = todas.stream().collect(Collectors.toMap(ZonaDTO::getId, ZonaDTO::getNombre));
    }

    public String nombreZona(Long idZona) {
        if (idZona == null) {
            return "Sin zona fija";
        }
        return nombresZonas.getOrDefault(idZona, "Zona " + idZona);
    }

    public void cambiarZona() {
        try {
            service.asignarZona(idRepartidorACambiar, idZonaNueva);
            Mensajes.info("Zona actualizada");
            idRepartidorACambiar = null;
            idZonaNueva = null;
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public void cambiarCapacidad() {
        try {
            service.actualizarCapacidad(idRepartidorCapacidad, capacidadKgNueva, capacidadBultosNueva);
            Mensajes.info(capacidadKgNueva == null && capacidadBultosNueva == null
                    ? "Sin capacidad cargada: va a llevar un pedido a la vez"
                    : "Capacidad actualizada");
            idRepartidorCapacidad = null;
            capacidadKgNueva = null;
            capacidadBultosNueva = null;
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    // "120 kg · 40 bultos", o "Un pedido a la vez" si no tiene capacidad cargada.
    public String capacidad(RepartidorDTO r) {
        if (!r.isConCapacidad()) {
            return "Un pedido a la vez";
        }
        StringBuilder texto = new StringBuilder();
        if (r.getCapacidadKg() != null) {
            texto.append(r.getCapacidadKg().stripTrailingZeros().toPlainString()).append(" kg");
        }
        if (r.getCapacidadBultos() != null) {
            texto.append(texto.length() > 0 ? " · " : "").append(r.getCapacidadBultos()).append(" bultos");
        }
        return texto.toString();
    }

    // Lo que lleva ahora: "2 pedidos (35 kg, 12 bultos)".
    public String carga(RepartidorDTO r) {
        int pedidos = r.getPedidosEnCurso();
        if (pedidos == 0) {
            return "—";
        }
        return pedidos + (pedidos == 1 ? " pedido" : " pedidos") + " ("
                + (r.getCargaKg().signum() > 0 ? r.getCargaKg().stripTrailingZeros().toPlainString() + " kg, " : "")
                + r.getCargaBultos() + " bultos): "
                + r.getPedidosEnCursoTexto();
    }

    public void registrar() {
        try {
            service.registrarRepartidor(nuevoRepartidor);
            Mensajes.info("Repartidor registrado correctamente");
            nuevoRepartidor = new DatosRepartidorDTO();
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public List<RepartidorDTO> getRepartidores() { return repartidores; }
    public List<ZonaDTO> getListaZonas() { return listaZonas; }
    public Long getIdRepartidorACambiar() { return idRepartidorACambiar; }
    public void setIdRepartidorACambiar(Long idRepartidorACambiar) { this.idRepartidorACambiar = idRepartidorACambiar; }
    public Long getIdZonaNueva() { return idZonaNueva; }
    public void setIdZonaNueva(Long idZonaNueva) { this.idZonaNueva = idZonaNueva; }
    public Long getIdRepartidorCapacidad() { return idRepartidorCapacidad; }
    public void setIdRepartidorCapacidad(Long idRepartidorCapacidad) { this.idRepartidorCapacidad = idRepartidorCapacidad; }
    public java.math.BigDecimal getCapacidadKgNueva() { return capacidadKgNueva; }
    public void setCapacidadKgNueva(java.math.BigDecimal capacidadKgNueva) { this.capacidadKgNueva = capacidadKgNueva; }
    public Integer getCapacidadBultosNueva() { return capacidadBultosNueva; }
    public void setCapacidadBultosNueva(Integer capacidadBultosNueva) { this.capacidadBultosNueva = capacidadBultosNueva; }
    public DatosRepartidorDTO getNuevoRepartidor() { return nuevoRepartidor; }
    public void setNuevoRepartidor(DatosRepartidorDTO nuevoRepartidor) { this.nuevoRepartidor = nuevoRepartidor; }
}
