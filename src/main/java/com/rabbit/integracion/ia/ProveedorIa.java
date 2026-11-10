package com.rabbit.integracion.ia;

/**
 * El modelo de lenguaje detrás del gateway. Es la costura que desacopla a Rabbit del proveedor: para cambiar de
 * proveedor se escribe otra implementación (ver {@link ProveedorClaude}) y no se toca el resto.
 */
public interface ProveedorIa {

    /**
     * Manda la consigna y el texto del usuario y devuelve lo que contestó el modelo, tal cual.
     *
     * @throws IaNoDisponibleException si el proveedor no está configurado, no responde a tiempo o responde mal
     */
    String completar(String consigna, String texto) throws IaNoDisponibleException;

    /** El proveedor que corresponde a la configuración (system properties o variables de entorno). */
    static ProveedorIa vigente() {
        String clave = ProveedorClaude.claveConfigurada();
        return clave == null ? new ProveedorNoConfigurado() : new ProveedorClaude(clave);
    }
}
