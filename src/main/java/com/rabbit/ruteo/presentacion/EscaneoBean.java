package com.rabbit.ruteo.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — repartidor/escaneo.xhtml: lo que ve el repartidor
 * al escanear con la cámara el QR de una etiqueta (ADR-056).
 *
 * El QR lleva el código de seguimiento del pedido. Acá se busca ENTRE LAS ENTREGAS DEL PROPIO
 * REPARTIDOR (nunca en todos los pedidos): si el pedido no es suyo, no se muestra nada de él.
 * Si está por retirar, un botón marca el retiro (lo mismo que "Retirar" en Mis entregas).
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.pedidos.negocio.IGestionPedidos;
import com.rabbit.pedidos.negocio.ValidacionException;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.negocio.IRuteo;
import jakarta.ejb.EJBException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class EscaneoBean implements Serializable {

    @Inject
    private IRuteo ruteo;

    @Inject
    private IGestionPedidos gestion;

    private String codigo;
    private HojaDeRutaDTO pedido;
    private boolean buscado;

    // f:viewAction: busca cuando ya se leyó el parámetro de la URL.
    public void buscar() {
        buscado = true;
        pedido = null;
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        codigo = codigo.trim().toUpperCase();
        List<HojaDeRutaDTO> mias;
        try {
            mias = ruteo.entregasEnCursoDelRepartidor();
        } catch (RuntimeException e) {
            // Cuenta REPARTIDOR sin repartidor asociado, o sin acceso: no es suyo nada.
            return;
        }
        for (HojaDeRutaDTO h : mias) {
            if (codigo.equalsIgnoreCase(h.codigoSeguimiento)) {
                pedido = h;
                return;
            }
        }
    }

    /** CONFIRMADO → EN_CAMINO: el repartidor retiró el pedido (escaneó la etiqueta). */
    public void retirar() {
        if (pedido == null) {
            return;
        }
        try {
            gestion.despacharPedido(pedido.idPedido);
            Mensajes.info("Pedido " + pedido.idPedido + " retirado: ya está en camino");
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        } catch (EJBException e) {
            Mensajes.error("No se pudo registrar el retiro. Intentá de nuevo.");
        }
        buscar();
    }

    public boolean isPorRetirar() {
        return pedido != null && "CONFIRMADO".equals(pedido.estado);
    }

    public boolean isEnCamino() {
        return pedido != null && "EN_CAMINO".equals(pedido.estado);
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public HojaDeRutaDTO getPedido() { return pedido; }
    public boolean isBuscado() { return buscado; }
}
