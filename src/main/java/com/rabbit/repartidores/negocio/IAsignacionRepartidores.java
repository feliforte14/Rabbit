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
     * Toma un repartidor DISPONIBLE y lo deja OCUPADO con este pedido.
     *
     * @param idPedido pedido a repartir
     * @return el ID del repartidor asignado
     * @throws RuntimeException de aplicación (rollback) si no hay ningún
     *         repartidor disponible
     */
    Long asignarRepartidor(Long idPedido);

    /**
     * Como asignarRepartidor, pero prefiere un repartidor de la zona
     * indicada; si no hay ninguno libre ahí, toma cualquiera.
     */
    Long asignarRepartidor(Long idPedido, Long idZonaPreferida);

    /**
     * Devuelve el repartidor a DISPONIBLE: cuando el pedido se entrega o
     * se cancela después de confirmado.
     *
     * @param idRepartidor repartidor a liberar
     */
    void liberarRepartidor(Long idRepartidor);
}
