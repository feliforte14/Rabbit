package com.rabbit.pedidos.negocio;

import com.rabbit.pedidos.dto.IndicadoresDTO;
import jakarta.ejb.Local;

/**
 * CONTRATO del tablero de indicadores de entrega (ADR-044). Implementada
 * por IndicadoresService.
 */
@Local
public interface IIndicadores {

    /** Días que se pueden consultar (los que ofrece la pantalla). */
    java.util.List<Integer> PERIODOS = java.util.List.of(7, 30, 90);

    /**
     * Indicadores de los pedidos recibidos en los últimos {@code dias}
     * días (hoy incluido). Solo el personal.
     *
     * @param idComercio un comercio puntual, o null para toda la red
     * @throws ValidacionException si el período no es uno de PERIODOS
     */
    IndicadoresDTO calcular(int dias, Long idComercio);

    /** Lo mismo, solo de los pedidos del comercio que llama. Solo COMERCIO. */
    IndicadoresDTO calcularDelComercioActual(int dias);
}
