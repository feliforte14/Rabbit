package com.rabbit.repartidores.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Regla de negocio de Repartidores incumplida (por ejemplo, no hay ningún
 * repartidor disponible). rollback = true: si se lanza dentro de
 * PedidoService.confirmarPedido, deshace toda la confirmación.
 */
@ApplicationException(rollback = true)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
