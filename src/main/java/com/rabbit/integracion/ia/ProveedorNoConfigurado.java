package com.rabbit.integracion.ia;

/**
 * Sin clave de API no hay IA, y el sistema funciona igual: el gateway cae en su fallback. Es el estado normal de
 * un entorno de desarrollo, de los tests y del CI.
 */
public class ProveedorNoConfigurado implements ProveedorIa {

    @Override
    public String completar(String consigna, String texto) throws IaNoDisponibleException {
        throw new IaNoDisponibleException("No hay proveedor de IA configurado");
    }
}
