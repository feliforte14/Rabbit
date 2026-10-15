package com.rabbit.repartidores.negocio;

/**
 * Cuando se elimina una zona, los repartidores que tenían esa zona quedan
 * "sin zona fija": la asignación los sigue considerando para cualquier
 * pedido (ver ZonaEliminada).
 */

import com.rabbit.repartidores.datos.RepartidorRepository;
import com.rabbit.ruteo.negocio.ZonaEliminada;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class ZonaDeRepartidores {

    private static final Logger LOG = Logger.getLogger(ZonaDeRepartidores.class.getName());

    @Inject
    private RepartidorRepository repository;

    public void alEliminarZona(@Observes ZonaEliminada evento) {
        int repartidores = repository.quitarZona(evento.getIdZona());
        LOG.info("[Repartidores] Zona " + evento.getIdZona() + " eliminada: "
                + repartidores + " repartidor(es) quedaron sin zona fija");
    }
}
