package com.rabbit.seguridad.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Excepción de una regla de negocio violada (username duplicado, password
 * vacío, etc.). @ApplicationException para que el contenedor la propague
 * tal cual, sin envolverla en EJBException.
 */
@ApplicationException(rollback = true)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
