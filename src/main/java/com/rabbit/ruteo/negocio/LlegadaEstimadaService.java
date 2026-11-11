package com.rabbit.ruteo.negocio;

/**
 * CAPA DE NEGOCIO — horario estimado de un pedido en camino para el
 * seguimiento público (EJB @Stateless, componente Ruteo, ADR-043).
 *
 * Para saber en qué parada está el pedido hace falta la hoja de ruta
 * recomendada del repartidor, y armarla lee las direcciones de todos sus
 * pedidos, depósitos y puntos de picking: datos que solo ve el personal.
 * El seguimiento no tiene usuario, así que en vez de abrir esas consultas
 * sin rol, este bean las hace con @RunAs("OPERADOR") (mismo recurso que
 * AsignadorDeEspera) y devuelve solo números: la posición y la franja.
 * Lo que ve el cliente nunca incluye direcciones de nadie.
 */

import com.rabbit.ruteo.dto.LlegadaEstimadaDTO;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@Stateless
@RunAs("OPERADOR")
@PermitAll
public class LlegadaEstimadaService implements ILlegadaEstimada {

    private static final Logger LOG = Logger.getLogger(LlegadaEstimadaService.class.getName());

    @Inject
    private IRuteo ruteo;

    // Solo lectura: no hace falta retener una transacción mientras se arma.
    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public LlegadaEstimadaDTO estimar(Long idRepartidor, Long idPedido) {
        if (idRepartidor == null || idPedido == null) {
            return null;
        }
        try {
            return EstimacionDeLlegada.estimar(ruteo.recorridoDeRepartidor(idRepartidor), idPedido,
                    LocalDateTime.now(), EstimacionDeLlegada.minutosPorParada());
        } catch (RuntimeException e) {
            // El seguimiento se ve igual, sin horario.
            LOG.warning("[Ruteo] No se pudo estimar la llegada del pedido " + idPedido + ": " + e.getMessage());
            return null;
        }
    }
}
