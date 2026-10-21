package com.rabbit.notificaciones.presentacion;

import com.rabbit.comercios.dto.ComercioDTO;
import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.notificaciones.dto.AvisoDestinatarioDTO;
import com.rabbit.notificaciones.dto.NotificacionDTO;
import com.rabbit.notificaciones.negocio.IAvisosAlDestinatario;
import com.rabbit.notificaciones.negocio.INotificaciones;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.SecurityContext;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * CAPA DE PRESENTACIÓN — la campana de notificaciones que está en la cabecera
 * de todas las pantallas (ver template.xhtml): al tocarla se abre un panel con
 * los últimos avisos. Reemplaza a las tablas "Avisos" que antes estaban al final
 * de Mis pedidos (comercio) y de Pedidos (personal).
 *
 * Qué ve cada uno (lo decide el EJB con @RolesAllowed; acá solo se elige qué
 * pedir según el rol):
 *  - COMERCIO: los avisos que Rabbit le mandó por cada cambio de estado de sus pedidos.
 *  - Personal (administrador y operador): los avisos a comercios y los avisos a
 *    quien recibe cada pedido (ADR-040).
 *  - Repartidor y ERP: no tienen campana.
 *
 * @RequestScoped: el panel se arma en cada pantalla que se dibuja; los datos se
 * piden recién la primera vez que se usan (una página que no muestra la campana
 * no consulta nada).
 */
@Named
@RequestScoped
public class CampanaBean {

    private static final Logger LOG = Logger.getLogger(CampanaBean.class.getName());
    private static final int CANTIDAD = 15;

    @Inject
    private SecurityContext seguridad;

    @Inject
    private INotificaciones notificaciones;

    @Inject
    private IAvisosAlDestinatario avisosAlDestinatario;

    @Inject
    private IConsultaComercios comercios;

    private List<NotificacionDTO> avisos;
    private List<AvisoDestinatarioDTO> avisosADestinatarios;
    private Map<Long, String> nombresDeComercios;

    /** La campana se muestra solo a quien tiene avisos: comercios y personal. */
    public boolean isVisible() {
        return isPersonal() || isComercio();
    }

    public boolean isPersonal() {
        return seguridad.isCallerInRole("ADMINISTRADOR") || seguridad.isCallerInRole("OPERADOR");
    }

    public boolean isComercio() {
        return seguridad.isCallerInRole("COMERCIO");
    }

    /** Avisos por cambios de estado de pedidos: los del comercio, o (personal) los de todos. */
    public List<NotificacionDTO> getAvisos() {
        if (avisos == null) {
            avisos = cargarAvisos();
        }
        return avisos;
    }

    /** Avisos a quien recibe cada pedido. Solo para el personal. */
    public List<AvisoDestinatarioDTO> getAvisosADestinatarios() {
        if (avisosADestinatarios == null) {
            avisosADestinatarios = cargarAvisosADestinatarios();
        }
        return avisosADestinatarios;
    }

    /** Nombre del comercio de un aviso (para el personal, que ve los de todos). */
    public String nombreComercio(Long idComercio) {
        if (nombresDeComercios == null) {
            nombresDeComercios = cargarNombres();
        }
        return nombresDeComercios.getOrDefault(idComercio, "Comercio " + idComercio);
    }

    /** Para el título del panel y el lector de pantalla: cuántos avisos hay en total. */
    public int getTotal() {
        return getAvisos().size() + (isPersonal() ? getAvisosADestinatarios().size() : 0);
    }

    // --- privados ---

    // Un fallo al leer los avisos (cuenta sin comercio asociado, base caída) no
    // puede romper la pantalla que se está dibujando: la campana queda vacía.
    private List<NotificacionDTO> cargarAvisos() {
        try {
            if (isPersonal()) {
                return notificaciones.listarRecientes(CANTIDAD);
            }
            if (isComercio()) {
                return notificaciones.listarDelComercioActual(CANTIDAD);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[Notificaciones] No se pudieron leer los avisos de la campana: " + e.getMessage());
        }
        return List.of();
    }

    private List<AvisoDestinatarioDTO> cargarAvisosADestinatarios() {
        if (!isPersonal()) {
            return List.of();
        }
        try {
            return avisosAlDestinatario.listarRecientes(CANTIDAD);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[Notificaciones] No se pudieron leer los avisos a destinatarios: " + e.getMessage());
            return List.of();
        }
    }

    private Map<Long, String> cargarNombres() {
        if (!isPersonal()) {
            return Map.of();
        }
        try {
            return comercios.listarTodos().stream()
                    .collect(Collectors.toMap(ComercioDTO::getId, ComercioDTO::getNombre, (a, b) -> a));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[Notificaciones] No se pudieron leer los comercios de la campana: " + e.getMessage());
            return Map.of();
        }
    }
}
