package com.rabbit.transportistas.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Error de negocio del componente Transportistas (transportista inexistente
 * o de baja, envío rechazado, transportista sin respuesta). rollback = true:
 * dentro de PedidoService.derivarATransportista deshace toda la derivación.
 */
@ApplicationException(rollback = true)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
