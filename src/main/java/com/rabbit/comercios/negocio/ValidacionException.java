package com.rabbit.comercios.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Excepción de una regla de negocio violada (CUIT inválido, comercio
 * inexistente, etc.). Se anota @ApplicationException para que el
 * contenedor EJB la propague tal cual al llamador, en vez de envolverla
 * en una EJBException — así ComercioBean puede atraparla directamente.
 */
@ApplicationException(rollback = true)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
