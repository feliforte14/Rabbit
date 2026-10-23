package com.rabbit.pedidos.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - JSF) — personal/etiquetas.xhtml: las etiquetas imprimibles
 * de un pedido, una por bulto, con el QR del código de seguimiento, la dirección y "bulto 2 de 3"
 * (ADR-056). Por defecto sale una etiqueta por cada bulto del pedido; el personal puede pedir otra
 * cantidad (por ejemplo, reimprimir una) con ?bultos=N, sin cambiar el pedido.
 *
 * El QR apunta a escaneo.xhtml: un repartidor con sesión llega a la pantalla donde marca el retiro;
 * cualquier otra persona, al seguimiento público. Se arma con rabbit.url-publica si está definida
 * (el link que se imprime tiene que servir desde la calle) y, si no, con la URL de este pedido.
 */

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.negocio.Bultos;
import com.rabbit.pedidos.negocio.ISeguimientoPedido;
import com.rabbit.pedidos.negocio.ValidacionException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Named
@RequestScoped
public class EtiquetasPedidoBean {

    @Inject
    private ISeguimientoPedido seguimiento;

    @Inject
    private IConsultaComercios comercios;

    // Los dos llegan por la URL (f:viewParam).
    private Long pedido;
    private Integer bultos;

    private PedidoDTO datos;
    private String comercio;
    private String error;

    // f:viewAction: carga cuando ya se leyeron los parámetros de la URL.
    public void cargar() {
        if (pedido == null) {
            error = "Falta el pedido";
            return;
        }
        try {
            datos = seguimiento.consultarEstadoPedido(pedido);
        } catch (RuntimeException e) {
            error = "No existe el pedido " + pedido;
            return;
        }
        if ("CANCELADO".equals(datos.estado)) {
            error = "El pedido " + pedido + " está cancelado: no se imprimen etiquetas";
            datos = null;
            return;
        }
        try {
            ComercioDTO c = comercios.obtenerComercio(datos.idComercio);
            comercio = c != null ? c.getNombre() : null;
        } catch (RuntimeException e) {
            comercio = null;
        }
        try {
            bultos = bultos == null ? datos.cantidadDeBultos : Bultos.validar(bultos);
        } catch (ValidacionException e) {
            bultos = datos.cantidadDeBultos;
        }
    }

    /** 1, 2, ... N: un elemento por etiqueta. */
    public List<Integer> getNumeros() {
        List<Integer> numeros = new ArrayList<>();
        for (int i = 1; bultos != null && i <= bultos; i++) {
            numeros.add(i);
        }
        return numeros;
    }

    /** Adónde apunta el QR. */
    public String getUrlEscaneo() {
        if (datos == null) {
            return "";
        }
        return base() + "/escaneo.xhtml?codigo=" + URLEncoder.encode(datos.codigoSeguimiento, StandardCharsets.UTF_8);
    }

    private static String base() {
        String propiedad = System.getProperty("rabbit.url-publica");
        if (propiedad != null && !propiedad.isBlank()) {
            String url = propiedad.trim();
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance()
                .getExternalContext().getRequest();
        String puerto = (request.getScheme().equals("https") && request.getServerPort() == 443)
                || (request.getScheme().equals("http") && request.getServerPort() == 80) ? "" : ":" + request.getServerPort();
        return request.getScheme() + "://" + request.getServerName() + puerto + request.getContextPath();
    }

    public Long getPedido() { return pedido; }
    public void setPedido(Long pedido) { this.pedido = pedido; }
    public Integer getBultos() { return bultos; }
    public void setBultos(Integer bultos) { this.bultos = bultos; }
    public PedidoDTO getDatos() { return datos; }
    public String getComercio() { return comercio; }
    public String getError() { return error; }
}
