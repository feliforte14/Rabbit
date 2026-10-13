package com.rabbit.notificaciones.negocio;

/**
 * Cuando se elimina un comercio, borra los avisos que recibió (ver ComercioEliminado). No
 * impide la eliminación: solo existen para pedidos, y Pedidos ya impide
 * eliminar un comercio con pedidos que no estén cancelados.
 */

import com.rabbit.comercios.negocio.ComercioEliminado;
import com.rabbit.notificaciones.datos.NotificacionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class ReferenciasAComercioEnNotificaciones {

    private static final Logger LOG = Logger.getLogger(ReferenciasAComercioEnNotificaciones.class.getName());

    @Inject
    private NotificacionRepository repository;

    public void alConfirmarEliminacion(@Observes ComercioEliminado evento) {
        int borrados = repository.eliminarNotificacionesDeComercio(evento.getIdComercio());
        LOG.info("[Notificaciones] Comercio " + evento.getIdComercio() + " eliminado: se borraron " + borrados + " aviso(s)");
    }
}
