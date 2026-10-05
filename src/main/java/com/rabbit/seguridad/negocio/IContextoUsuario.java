package com.rabbit.seguridad.negocio;

import jakarta.ejb.Local;

/**
 * CONTRATO del componente ServicioDeUsuariosYSeguridad para saber a quién
 * representa el usuario que llama. Implementada por
 * {@link ContextoUsuarioService}.
 *
 * Lo usan los componentes que filtran por dueño (Pedidos, Inventario,
 * Comercios, Ruteo): el comercio o el repartidor sale SIEMPRE de la
 * identidad autenticada, nunca de un parámetro que manda el cliente.
 */
@Local
public interface IContextoUsuario {

    /**
     * @return el comercio de un usuario COMERCIO o ERP
     * @throws ValidacionException si quien llama no es un COMERCIO o ERP asociado a un comercio
     */
    Long idComercioActual();

    /**
     * @return el repartidor de un usuario REPARTIDOR
     * @throws ValidacionException si quien llama no es un REPARTIDOR asociado a un repartidor
     */
    Long idRepartidorActual();
}
