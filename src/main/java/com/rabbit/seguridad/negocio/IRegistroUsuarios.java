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
     * Da de alta un usuario nuevo, activo. Solo ADMINISTRADOR.
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

    /**
     * Vuelve a habilitar una cuenta dada de baja, con una contraseña nueva:
     * la baja la sacó del realm de WildFly, que guarda su propio hash de la
     * contraseña, y ese hash no se puede reconstruir desde la tabla. Solo
     * ADMINISTRADOR.
     *
     * @param id ID del usuario a reactivar
     * @param passwordNueva contraseña en claro con la que va a entrar
     * @throws ValidacionException si no existe, ya está activo, la
     *         contraseña es inválida o ya no existe (o está dado de baja) el
     *         comercio o repartidor que representa
     */
    void reactivarUsuario(Long id, String passwordNueva);
}
