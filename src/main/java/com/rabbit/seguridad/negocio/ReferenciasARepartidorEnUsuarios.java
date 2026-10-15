package com.rabbit.seguridad.negocio;

/**
 * Impide eliminar un repartidor que tiene cuenta de usuario (activa o dada
 * de baja): la cuenta quedaría representando a un repartidor inexistente
 * (ver EliminacionDeRepartidor). Hay que eliminar la cuenta primero
 * (Usuarios → Eliminar).
 */

import com.rabbit.repartidores.negocio.EliminacionDeRepartidor;
import com.rabbit.seguridad.datos.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReferenciasARepartidorEnUsuarios {

    @Inject
    private UsuarioRepository repository;

    public void alEliminarRepartidor(@Observes EliminacionDeRepartidor evento) {
        if (repository.contarCuentasDeRepartidor(evento.getIdRepartidor()) > 0) {
            evento.impedir("una cuenta de usuario (eliminala primero desde Usuarios)");
        }
    }
}
