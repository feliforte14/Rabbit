package com.rabbit.integracion.ia;

/** El proveedor de IA no pudo contestar (sin configurar, sin respuesta a tiempo o con una respuesta inválida). */
public class IaNoDisponibleException extends Exception {

    public IaNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public IaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
