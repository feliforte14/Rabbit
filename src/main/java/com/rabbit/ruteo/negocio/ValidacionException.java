package com.rabbit.ruteo.negocio;

import jakarta.ejb.ApplicationException;

/** Error de negocio del componente Ruteo (zonas inválidas o superpuestas). */
@ApplicationException(rollback = true)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
