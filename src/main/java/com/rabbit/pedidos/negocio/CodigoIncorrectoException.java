package com.rabbit.pedidos.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Un código de entrega equivocado. A diferencia del resto de las ValidacionException NO deshace
 * la transacción: hace falta que el intento fallido quede contado (ADR-055), si no se podrían
 * probar los 10.000 códigos sin límite. Se captura igual que cualquier ValidacionException.
 */
@ApplicationException(rollback = false)
public class CodigoIncorrectoException extends ValidacionException {
    public CodigoIncorrectoException(String mensaje) {
        super(mensaje);
    }
}
