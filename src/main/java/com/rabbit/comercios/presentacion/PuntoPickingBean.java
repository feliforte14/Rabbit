package com.rabbit.comercios.presentacion;

/**
 * CAPA DE PRESENTACIÓN (Managed Bean - Jakarta Faces / JSF)
 *
 * Administra los puntos de picking de UN comercio puntual (identificado
 * por idComercio, que llega como parámetro de la URL vía <f:viewParam>).
 * No tiene lógica de negocio: delega todo al componente ServicioDeComercios,
 * a través de sus interfaces (IRegistroComercios para escritura,
 * IConsultaComercios para lectura) y no de la clase que las implementa.
 *
 * Renombrado desde SucursalBean (ver Sección 1.2 del documento técnico).
 *
 * La usan el personal de Rabbit (cualquier comercio, por parámetro) y un
 * usuario COMERCIO (solo el suyo: el parámetro se ignora y el comercio sale
 * de su identidad; ComercioService además lo vuelve a controlar).
 */

import com.rabbit.infraestructura.Mensajes;
import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.dto.DatosPuntoPickingDTO;
import com.rabbit.comercios.dto.PuntoPickingDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.comercios.negocio.IRegistroComercios;
import com.rabbit.comercios.negocio.ValidacionException;

import com.rabbit.seguridad.negocio.IContextoUsuario;
import com.rabbit.seguridad.presentacion.SesionBean;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class PuntoPickingBean implements Serializable {

    // Contrato de escritura: alta, baja y reactivación de puntos de picking.
    @Inject
    private IRegistroComercios registro;

    // Contrato de lectura: datos del comercio y su listado de puntos de picking.
    @Inject
    private IConsultaComercios consulta;

    @Inject
    private SesionBean sesion;

    @Inject
    private IContextoUsuario contextoUsuario;

    private Long idComercio;
    private ComercioDTO comercio;
    private List<PuntoPickingDTO> puntosPicking;

    private DatosPuntoPickingDTO nuevoPuntoPicking = new DatosPuntoPickingDTO();

    // Se invoca vía <f:viewAction> apenas idComercio queda seteado por el
    // <f:viewParam> de puntos-picking.xhtml — no hay @PostConstruct porque
    // en ese momento del ciclo de vida idComercio todavía no llegó.
    public void cargar() {
        comercio = null;
        puntosPicking = List.of();
        try {
            if (sesion.isComercio()) {
                idComercio = contextoUsuario.idComercioActual();
            }
            if (idComercio == null) {
                Mensajes.error("Elegí un comercio desde el listado de comercios");
                return;
            }
            comercio = consulta.obtenerComercio(idComercio);
            puntosPicking = consulta.listarPuntosPickingDeComercio(idComercio);
        } catch (ValidacionException | com.rabbit.seguridad.negocio.ValidacionException e) {
            // Si es la propia cuenta del comercio la que no se pudo resolver,
            // el mensaje interno del servicio ("Comercio no encontrado: 1")
            // confunde: para el usuario no hay ningún comercio en pantalla,
            // solo una cuenta mal asociada. Al personal, que sí eligió el
            // comercio de un listado real, se le muestra el mensaje tal cual.
            if (sesion.isComercio()) {
                Mensajes.error("Tu cuenta no está asociada a un comercio activo. Contactá a un administrador.");
            } else {
                Mensajes.error(e.getMessage());
            }
        }
    }

    // Alta de un punto de picking nuevo para idComercio, a partir de
    // nuevoPuntoPicking (bindeado al formulario de la página).
    public void registrar() {
        try {
            registro.registrarPuntoPicking(idComercio, nuevoPuntoPicking);
            Mensajes.info("Punto de picking registrado correctamente");
            nuevoPuntoPicking = new DatosPuntoPickingDTO();
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    // Baja lógica del punto de picking (ver PuntoPicking.activa).
    public void darDeBaja(Long id) {
        try {
            registro.darDeBajaPuntoPicking(id);
            Mensajes.info("Punto de picking dado de baja");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    // Reactiva un punto de picking dado de baja. ComercioService rechaza
    // esto si el comercio dueño sigue inactivo (ver PuntoPicking.activa).
    public void reactivar(Long id) {
        try {
            registro.reactivarPuntoPicking(id);
            Mensajes.info("Punto de picking reactivado");
            cargar();
        } catch (ValidacionException e) {
            Mensajes.error(e.getMessage());
        }
    }

    // Getters/setters JavaBean: los requiere Expression Language (JSF),
    // incluido idComercio, que <f:viewParam> escribe vía su setter.
    public Long getIdComercio() { return idComercio; }
    public void setIdComercio(Long idComercio) { this.idComercio = idComercio; }
    public ComercioDTO getComercio() { return comercio; }
    public List<PuntoPickingDTO> getPuntosPicking() { return puntosPicking; }
    public DatosPuntoPickingDTO getNuevoPuntoPicking() { return nuevoPuntoPicking; }
    public void setNuevoPuntoPicking(DatosPuntoPickingDTO nuevoPuntoPicking) { this.nuevoPuntoPicking = nuevoPuntoPicking; }
}
