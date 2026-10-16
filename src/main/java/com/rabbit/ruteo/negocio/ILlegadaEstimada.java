package com.rabbit.ruteo.negocio;

import com.rabbit.ruteo.dto.LlegadaEstimadaDTO;
import jakarta.ejb.Local;

/**
 * CONTRATO del horario estimado de un pedido en camino (ADR-043). Lo usa el
 * seguimiento público, sin usuario. Implementada por LlegadaEstimadaService.
 */
@Local
public interface ILlegadaEstimada {

    /**
     * En qué parada del repartidor está el pedido y en qué franja llega.
     * Nunca lanza: si no se puede estimar (sin dirección, el pedido ya no
     * es del repartidor, falla una consulta), devuelve null y el
     * seguimiento se muestra sin horario.
     *
     * @param idRepartidor el que lleva el pedido
     * @param idPedido pedido EN_CAMINO
     */
    LlegadaEstimadaDTO estimar(Long idRepartidor, Long idPedido);
}
