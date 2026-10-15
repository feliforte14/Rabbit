package com.rabbit.repartidores.negocio;

/**
 * CONTRATO que ServicioDeRepartidores le ofrece a Pedidos para el flujo
 * de confirmación (cobrar → asignar repartidor → confirmar). La
 * implementa RepartidorService.
 *
 * Corre con el default de un EJB (REQUIRED): la asignación se suma a la
 * transacción de PedidoService.confirmarPedido, así que si algo falla, el
 * repartidor vuelve a quedar libre por rollback, sin que nadie tenga que
 * "desasignarlo" a mano.
 */

import jakarta.ejb.Local;

@Local
public interface IAsignacionRepartidores {

    /**
     * Le asigna el pedido al primer repartidor al que le entra (ver
     * CapacidadRepartidor), prefiriendo los de la zona indicada, y lo deja
     * OCUPADO.
     *
     * @param idPedido pedido a repartir
     * @param idZonaPreferida zona del pedido; null = cualquiera
     * @param pesoKg kilos del pedido; null si no se conocen
     * @param bultos bultos del pedido
     * @return el ID del repartidor asignado
     * @throws RuntimeException de aplicación (rollback) si no le entra a nadie
     */
    Long asignarRepartidor(Long idPedido, Long idZonaPreferida, java.math.BigDecimal pesoKg, int bultos);

    /**
     * Le saca el pedido al repartidor (se entregó o se canceló después de
     * confirmado). Si era el último que llevaba, vuelve a DISPONIBLE.
     *
     * @param idRepartidor repartidor que lo llevaba
     * @param idPedido pedido que deja de llevar
     */
    void liberarRepartidor(Long idRepartidor, Long idPedido);
}
