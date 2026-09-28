package com.rabbit.integracion.legado;

import jakarta.ejb.Local;

/**
 * Puerto hacia el sistema legado, del lado de Rabbit. Es la interfaz que
 * {@link com.rabbit.comercios.negocio.ComercioService} conoce — nunca
 * {@link PadronFiscalClient} ni los tipos JAX-WS directamente, mismo
 * criterio de Facade/DIP que el resto de los componentes.
 */
@Local
public interface IPadronFiscalClient {

    /**
     * Nunca lanza: los tres desenlaces (habilitado, no encontrado, servicio
     * no disponible) viajan en el resultado — ver {@link ResultadoConsultaCuit}.
     */
    ResultadoConsultaCuit consultar(String cuit);
}
