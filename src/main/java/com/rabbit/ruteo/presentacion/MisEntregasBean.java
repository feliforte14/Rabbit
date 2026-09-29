package com.rabbit.ruteo.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — la pantalla del repartidor
 * (mis-entregas.xhtml): su entrega actual con la hoja de ruta, los botones
 * para marcar el retiro y la entrega, y su historial.
 *
 * No recibe el ID del repartidor: IRuteo e IGestionPedidos lo sacan de la
 * identidad autenticada, así que un repartidor no puede ver ni mover los
 * pedidos de otro.
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ValidacionException;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.negocio.IRuteo;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJBException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class MisEntregasBean implements Serializable {

    @Inject
    private IRuteo ruteo;

    @Inject
    private IGestionPedidos gestion;

    private HojaDeRutaDTO entregaActual;
    private List<HojaDeRutaDTO> historial;

    @PostConstruct
    public void cargar() {
        try {
            entregaActual = ruteo.entregaActualDelRepartidor();
            historial = ruteo.historialDelRepartidor();
        } catch (com.rabbit.seguridad.negocio.ValidacionException e) {
            // Cuenta REPARTIDOR sin repartidor asociado.
            entregaActual = null;
            historial = List.of();
            Mensajes.error(e.getMessage());
        }
    }

    // Retiró el pedido: CONFIRMADO → EN_CAMINO.
    public void retirar() {
        ejecutar(() -> gestion.despacharPedido(entregaActual.getIdPedido()), "Pedido retirado: ya está en camino");
    }

    // Lo entregó: EN_CAMINO → ENTREGADO (y el repartidor queda libre).
    public void entregar() {
        ejecutar(() -> gestion.registrarEntrega(entregaActual.getIdPedido()), "Entrega registrada");
    }

    private void ejecutar(Runnable accion, String exito) {
        if (entregaActual == null) {
            return;
        }
        try {
            accion.run();
            Mensajes.info(exito);
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo registrar el cambio. Intentá de nuevo.");
        }
    }

    public HojaDeRutaDTO getEntregaActual() { return entregaActual; }
    public List<HojaDeRutaDTO> getHistorial() { return historial; }
}
