package com.rabbit.seguridad.negocio;

/**
 * Impide eliminar un comercio que tiene cuentas de usuario asociadas: la
 * cuenta quedaría representando a un comercio inexistente (ver
 * EliminacionDeComercio). Hay que dar de baja la cuenta primero.
 */

import com.rabbit.comercios.negocio.EliminacionDeComercio;
import com.rabbit.seguridad.datos.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReferenciasAComercioEnUsuarios {

    @Inject
    private UsuarioRepository repository;

    public void alEliminarComercio(@Observes EliminacionDeComercio evento) {
        long cuentas = repository.contarCuentasDeComercio(evento.getIdComercio());
        if (cuentas > 0) {
            evento.impedir(cuentas + " cuenta(s) de usuario");
        }
    }
}
