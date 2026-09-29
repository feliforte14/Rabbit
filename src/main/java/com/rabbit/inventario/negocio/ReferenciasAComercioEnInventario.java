package com.rabbit.inventario.negocio;

/**
 * Impide eliminar un comercio que tiene stock consignado en los depósitos:
 * esa mercadería quedaría sin dueño (ver EliminacionDeComercio).
 */

import com.rabbit.comercios.negocio.EliminacionDeComercio;
import com.rabbit.inventario.datos.InventarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReferenciasAComercioEnInventario {

    @Inject
    private InventarioRepository repository;

    public void alEliminarComercio(@Observes EliminacionDeComercio evento) {
        long items = repository.contarItemsDeComercio(evento.getIdComercio());
        if (items > 0) {
            evento.impedir(items + " ítem(s) de stock consignado");
        }
    }
}
