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
     * Baja lógica: el usuario deja de poder autenticarse (el realm JDBC de
     * WildFly solo acepta cuentas activas), pero el registro se conserva.
     *
     * @param id ID del usuario a dar de baja
     * @throws ValidacionException si el usuario no existe
     */
    void darDeBaja(Long id);

    /**
     * Vuelve a habilitar una cuenta dada de baja, con una contraseña nueva
     * (la anterior no se puede recuperar del hash). Solo ADMINISTRADOR.
     *
     * @param id ID del usuario a reactivar
     * @param passwordNueva contraseña en claro con la que va a entrar
     * @throws ValidacionException si no existe, ya está activo, la
     *         contraseña es inválida o ya no existe (o está dado de baja) el
     *         comercio o repartidor que representa
     */
    void reactivarUsuario(Long id, String passwordNueva);

    /**
     * Cambia la contraseña de una cuenta activa. También pasa a la base
     * las cuentas creadas antes de ADR-031. Solo ADMINISTRADOR.
     *
     * @param id ID del usuario
     * @param passwordNueva contraseña en claro con la que va a entrar
     * @throws ValidacionException si no existe, está dado de baja o la
     *         contraseña es inválida
     */
    void restablecerClave(Long id, String passwordNueva);
}
