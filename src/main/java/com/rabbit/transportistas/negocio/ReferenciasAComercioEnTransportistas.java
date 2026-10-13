package com.rabbit.transportistas.negocio;

/**
 * Cuando se elimina un comercio, borra sus envíos (cancelados, de pedidos cancelados) (ver ComercioEliminado). No
 * impide la eliminación: solo existen para pedidos, y Pedidos ya impide
 * eliminar un comercio con pedidos que no estén cancelados.
 */

import com.rabbit.comercios.negocio.ComercioEliminado;
import com.rabbit.transportistas.datos.TransportistaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class ReferenciasAComercioEnTransportistas {

    private static final Logger LOG = Logger.getLogger(ReferenciasAComercioEnTransportistas.class.getName());

    @Inject
    private TransportistaRepository repository;

    public void alConfirmarEliminacion(@Observes ComercioEliminado evento) {
        int borrados = repository.eliminarEnviosDeComercio(evento.getIdComercio());
        LOG.info("[Transportistas] Comercio " + evento.getIdComercio() + " eliminado: se borraron " + borrados + " envío(s)");
    }
}
