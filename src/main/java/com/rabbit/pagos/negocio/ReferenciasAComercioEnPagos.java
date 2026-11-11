package com.rabbit.pagos.negocio;

/**
 * Cuando se elimina un comercio, borra sus cobros (anulados, de pedidos cancelados) (ver ComercioEliminado). No
 * impide la eliminación: solo existen para pedidos, y Pedidos ya impide
 * eliminar un comercio con pedidos que no estén cancelados.
 */

import com.rabbit.comercios.negocio.ComercioEliminado;
import com.rabbit.pagos.datos.CobroRepository;
import com.rabbit.pagos.datos.LiquidacionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class ReferenciasAComercioEnPagos {

    private static final Logger LOG = Logger.getLogger(ReferenciasAComercioEnPagos.class.getName());

    @Inject
    private CobroRepository repository;

    @Inject
    private LiquidacionRepository liquidaciones;

    public void alConfirmarEliminacion(@Observes ComercioEliminado evento) {
        int borrados = repository.eliminarCobrosDeComercio(evento.getIdComercio());
        int liquidacionesBorradas = liquidaciones.eliminarDeComercio(evento.getIdComercio());
        LOG.info("[Pagos] Comercio " + evento.getIdComercio() + " eliminado: se borraron " + borrados
                + " cobro(s) y " + liquidacionesBorradas + " liquidación(es)");
    }
}
