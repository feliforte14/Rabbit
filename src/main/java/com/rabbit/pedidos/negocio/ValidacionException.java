package com.rabbit.pedidos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Excepción de una regla de negocio violada en este componente (pedido
 * inexistente, comercio no habilitado, etc.). Es una clase propia (no se
 * reutiliza la de Comercios ni la de Inventario) porque cada ServicioDeX
 * es independiente — mismo criterio documentado en las otras dos.
 */
@ApplicationException(rollback = true)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
