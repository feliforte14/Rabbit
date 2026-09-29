package com.rabbit.transportistas.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — transportistas.xhtml: alta y
 * baja de las empresas de envío y el seguimiento de los envíos derivados.
 * Solo personal de Rabbit.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.transportistas.datos.model.TipoIntegracion;
import com.rabbit.transportistas.dto.DatosTransportistaDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import com.rabbit.transportistas.negocio.IEnvios;
import com.rabbit.transportistas.negocio.IGestionTransportistas;
import com.rabbit.transportistas.negocio.ValidacionException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class TransportistaBean implements Serializable {

    @Inject
    private IGestionTransportistas gestion;

    @Inject
    private IEnvios envios;

    private List<TransportistaDTO> transportistas;
    private List<EnvioDTO> listaEnvios;
    private DatosTransportistaDTO nuevoTransportista = new DatosTransportistaDTO();

    @PostConstruct
    public void cargar() {
        transportistas = gestion.listarTodos();
        listaEnvios = envios.listarEnvios();
    }

    public void registrar() {
        try {
            gestion.registrarTransportista(nuevoTransportista);
            Mensajes.info("Transportista registrado");
            nuevoTransportista = new DatosTransportistaDTO();
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public void darDeBaja(Long id) {
        try {
            gestion.darDeBajaTransportista(id);
            Mensajes.info("Transportista dado de baja: no recibe envíos nuevos (los que tiene siguen su curso)");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public void reactivar(Long id) {
        try {
            gestion.reactivarTransportista(id);
            Mensajes.info("Transportista reactivado");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public long enviosActivos(Long idTransportista) {
        return listaEnvios.stream()
                .filter(e -> e.getIdTransportista().equals(idTransportista))
                .filter(e -> "SOLICITADO".equals(e.getEstado()) || "EN_TRANSITO".equals(e.getEstado()))
                .count();
    }

    public TipoIntegracion[] getTiposIntegracion() { return TipoIntegracion.values(); }
    public List<TransportistaDTO> getTransportistas() { return transportistas; }
    public List<EnvioDTO> getListaEnvios() { return listaEnvios; }
    public DatosTransportistaDTO getNuevoTransportista() { return nuevoTransportista; }
    public void setNuevoTransportista(DatosTransportistaDTO nuevoTransportista) { this.nuevoTransportista = nuevoTransportista; }
}
