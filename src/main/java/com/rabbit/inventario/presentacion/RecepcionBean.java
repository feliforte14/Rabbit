package com.rabbit.inventario.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — personal/recepciones.xhtml (ADR-058): el personal del
 * depósito ve las órdenes que anunciaron los comercios y registra lo que llegó de cada producto. Con
 * ?orden=N se abre el formulario de esa orden, con lo anunciado como valor inicial: lo común es que llegue
 * todo, y se corrige solo lo que difiere.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.infraestructura.Mensajes;
import com.rabbit.inventario.dto.LineaRecibidaDTO;
import com.rabbit.inventario.dto.OrdenDeRecepcionDTO;
import com.rabbit.inventario.negocio.IRecepcionDeMercaderia;
import com.rabbit.inventario.negocio.ValidacionException;
import jakarta.ejb.EJBException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named
@ViewScoped
public class RecepcionBean implements Serializable {

    /** Una línea del formulario de recepción: lo anunciado y lo que llegó (editable). */
    public static class Fila implements Serializable {
        private final Long idLinea;
        private final String producto;
        private final int esperada;
        private Integer recibida;

        Fila(Long idLinea, String producto, int esperada) {
            this.idLinea = idLinea;
            this.producto = producto;
            this.esperada = esperada;
            this.recibida = esperada;
        }

        public Long getIdLinea() { return idLinea; }
        public String getProducto() { return producto; }
        public int getEsperada() { return esperada; }
        public Integer getRecibida() { return recibida; }
        public void setRecibida(Integer recibida) { this.recibida = recibida; }

        /** Lo que llegó menos lo anunciado, mientras se escribe (null si está vacío). */
        public Integer getDiferencia() { return recibida == null ? null : recibida - esperada; }
    }

    @Inject
    private IRecepcionDeMercaderia recepciones;

    @Inject
    private IConsultaComercios comercios;

    private transient List<OrdenDeRecepcionDTO> ordenes;
    private Map<Long, String> nombresComercio;

    // f:viewParam: la orden que se está recibiendo.
    private Long orden;
    private OrdenDeRecepcionDTO abierta;
    private List<Fila> filas = new ArrayList<>();

    // f:viewAction: abre el formulario cuando ya se leyó el parámetro.
    public void abrir() {
        abierta = null;
        filas = new ArrayList<>();
        if (orden == null) {
            return;
        }
        try {
            OrdenDeRecepcionDTO o = recepciones.obtener(orden);
            if (!o.isAnunciada()) {
                Mensajes.error("La orden " + orden + " ya está " + o.getEstado() + ": no se puede volver a registrar");
                return;
            }
            abierta = o;
            filas = o.getLineas().stream()
                    .map(l -> new Fila(l.getId(), l.getProducto(), l.getCantidadEsperada()))
                    .collect(Collectors.toList());
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public String registrar() {
        List<LineaRecibidaDTO> recibidas = new ArrayList<>();
        for (Fila f : filas) {
            recibidas.add(new LineaRecibidaDTO(f.idLinea, f.recibida));
        }
        try {
            OrdenDeRecepcionDTO resultado = recepciones.registrarRecepcion(orden, recibidas);
            Mensajes.info("Orden " + resultado.getId() + " registrada: llegaron " + resultado.getTotalRecibido() + " de "
                    + resultado.getTotalEsperado() + " unidades"
                    + (resultado.isHayDiferencia() ? " (hay diferencias)" : "") + ". El stock ya se actualizó.");
            ordenes = null;
            abierta = null;
            filas = new ArrayList<>();
            orden = null;
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo registrar la recepción. Intentá de nuevo.");
        }
        return null;
    }

    public void cancelar(Long idOrden) {
        try {
            recepciones.cancelar(idOrden);
            Mensajes.info("Orden " + idOrden + " cancelada");
            ordenes = null;
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo cancelar la orden. Intentá de nuevo.");
        }
    }

    public List<OrdenDeRecepcionDTO> getOrdenes() {
        if (ordenes == null) {
            ordenes = recepciones.listarTodas();
        }
        return ordenes;
    }

    public String nombreComercio(Long idComercio) {
        if (nombresComercio == null) {
            nombresComercio = new HashMap<>();
            for (ComercioDTO c : comercios.listarTodos()) {
                nombresComercio.put(c.getId(), c.getNombre());
            }
        }
        return nombresComercio.getOrDefault(idComercio, "Comercio " + idComercio);
    }

    public long getAnunciadas() {
        return getOrdenes().stream().filter(OrdenDeRecepcionDTO::isAnunciada).count();
    }

    public Long getOrden() { return orden; }
    public void setOrden(Long orden) { this.orden = orden; }
    public OrdenDeRecepcionDTO getAbierta() { return abierta; }
    public List<Fila> getFilas() { return filas; }
}
