package com.rabbit.ruteo.presentacion;

/**
 * CAPA DE PRESENTACIÓN — escaneo.xhtml: adónde lleva el QR de una etiqueta (ADR-056).
 * Cualquiera lo puede escanear, así que no muestra nada: un repartidor con sesión va a su
 * pantalla de escaneo y el resto, al seguimiento público. El código solo se pasa de una
 * página a otra, codificado.
 */

import com.rabbit.seguridad.presentacion.SesionBean;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Named
@RequestScoped
public class EscaneoRedirectBean {

    @Inject
    private SesionBean sesion;

    private String codigo;

    public void derivar() throws IOException {
        FacesContext contexto = FacesContext.getCurrentInstance();
        ExternalContext externo = contexto.getExternalContext();
        String limpio = codigo == null ? "" : codigo.trim();
        String parametro = limpio.isEmpty() ? "" : "?codigo=" + URLEncoder.encode(limpio, StandardCharsets.UTF_8);
        boolean repartidor = sesion.isAutenticado() && sesion.isRepartidor();
        externo.redirect(externo.getRequestContextPath()
                + (repartidor ? "/repartidor/escaneo.xhtml" : "/seguimiento.xhtml") + parametro);
        contexto.responseComplete();
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
}
