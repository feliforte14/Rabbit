package com.rabbit.repartidores.negocio;

import java.util.ArrayList;
import java.util.List;

/**
 * Evento CDI sincrónico: "se va a eliminar este repartidor". Lo dispara
 * RepartidorService.eliminarRepartidor antes de borrar; cada componente que
 * guarda datos del repartidor (Pedidos, Seguridad) lo observa y anota un
 * impedimento si todavía tiene algo que lo necesita. Mismo patrón que
 * EliminacionDeComercio.
 */
public class EliminacionDeRepartidor {

    private final Long idRepartidor;
    private final List<String> impedimentos = new ArrayList<>();

    public EliminacionDeRepartidor(Long idRepartidor) {
        this.idRepartidor = idRepartidor;
    }

    public Long getIdRepartidor() { return idRepartidor; }

    /** Lo llama un observador cuando algo suyo todavía necesita al repartidor. */
    public void impedir(String motivo) {
        impedimentos.add(motivo);
    }

    public List<String> getImpedimentos() { return impedimentos; }
}
