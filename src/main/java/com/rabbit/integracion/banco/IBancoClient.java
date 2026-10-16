package com.rabbit.integracion.banco;

import jakarta.ejb.Local;
import java.math.BigDecimal;

/**
 * Puerto hacia el banco, del lado de Rabbit. Es lo único que conoce
 * PagoService: nunca {@link BancoClient} ni los tipos JAX-WS (patrón
 * Adapter). Si el banco pasara a REST, solo cambia BancoClient.
 */
@Local
public interface IBancoClient {

    /**
     * Pide al banco que cobre el pedido. Nunca lanza: los tres desenlaces
     * viajan en el resultado (ver {@link ResultadoAutorizacion}).
     */
    ResultadoAutorizacion autorizar(Long idPedido, BigDecimal importe);

    /**
     * Como autorizar, con la referencia que va a ver el banco (por ejemplo
     * "LIQ-12" para la liquidación mensual de un comercio, ADR-034).
     */
    ResultadoAutorizacion autorizarReferencia(String referencia, BigDecimal importe);

    /**
     * Pide al banco que devuelva la plata de una autorización.
     *
     * @return true si el banco confirmó la reversa; false si no respondió
     */
    boolean reversar(String codigoAutorizacion);
}
