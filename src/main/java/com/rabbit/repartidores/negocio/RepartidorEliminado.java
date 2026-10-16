package com.rabbit.repartidores.negocio;

/**
 * Evento CDI sincrónico: "este repartidor se elimina ahora". Lo dispara
 * RepartidorService.eliminarRepartidor cuando nadie impidió la eliminación
 * (ver EliminacionDeRepartidor), dentro de la misma transacción: cada
 * componente suelta lo suyo que apuntaba al repartidor. Si algo falla, la
 * transacción se deshace entera y el repartidor sigue existiendo.
 */
public class RepartidorEliminado {

    private final Long idRepartidor;

    public RepartidorEliminado(Long idRepartidor) {
        this.idRepartidor = idRepartidor;
    }

    public Long getIdRepartidor() { return idRepartidor; }
}
