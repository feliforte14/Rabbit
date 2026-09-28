package com.rabbit.pagos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Regla de negocio de Pagos incumplida: pago rechazado por la pasarela,
 * importe inválido, cobro inexistente. rollback = true: dentro de
 * PedidoService.confirmarPedido deshace también la asignación del
 * repartidor y el cambio de estado.
 */
@ApplicationException(rollback = true)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
