package com.rabbit.seguridad.negocio;

/**
 * CONTRATO DE ESCRITURA del componente ServicioDeUsuariosYSeguridad.
 * Implementada por {@link UsuarioService}.
 */

import com.rabbit.seguridad.dto.DatosUsuarioDTO;
import jakarta.ejb.Local;

@Local
public interface IRegistroUsuarios {

    /**
     * Da de alta un usuario nuevo, activo.
     *
     * @param datos username, password en claro y rol
     * @return el ID asignado por la base
     * @throws ValidacionException si el username ya existe o los datos son inválidos
     */
    Long registrarUsuario(DatosUsuarioDTO datos);

    /**
     * Baja lógica: el usuario deja de poder autenticarse (RabbitIdentityStore
     * lo excluye), pero el registro se conserva.
     *
     * @param id ID del usuario a dar de baja
     * @throws ValidacionException si el usuario no existe
     */
    void darDeBaja(Long id);
}
