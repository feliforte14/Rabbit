package com.rabbit.seguridad.negocio;

/**
 * CONTRATO DE SOLO LECTURA del componente ServicioDeUsuariosYSeguridad —
 * mismo principio de segregación de interfaces que IConsultaComercios /
 * IConsultaStock: quien solo necesita listar usuarios (la pantalla de
 * administración) no obtiene por dependencia la capacidad de dar de alta
 * ni de baja a nadie.
 *
 * Implementada por {@link UsuarioService}.
 */

import com.rabbit.seguridad.dto.UsuarioDTO;
import jakarta.ejb.Local;
import java.util.List;

@Local
public interface IConsultaUsuarios {

    /**
     * @return todos los usuarios registrados (activos e inactivos)
     */
    List<UsuarioDTO> listarTodos();

    /**
     * @param id ID del usuario
     * @return el usuario, nunca con su passwordHash expuesto
     * @throws ValidacionException si el usuario no existe
     */
    UsuarioDTO obtenerUsuario(Long id);
}
