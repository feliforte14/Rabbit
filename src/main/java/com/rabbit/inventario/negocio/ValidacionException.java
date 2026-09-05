package com.rabbit.inventario.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Excepción de una regla de negocio violada (depósito inexistente,
 * producto duplicado, etc.). Se anota @ApplicationException para que el
 * contenedor EJB la propague tal cual al llamador, en vez de envolverla
 * en una EJBException — así InventarioBean puede atraparla directamente.
 *
 * Es una clase propia de este componente (no se reutiliza la de Comercios)
 * porque cada ServicioDeX es independiente: no debería depender de las
 * clases internas de otro componente.
 */
@ApplicationException(rollback = true)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
