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
     * AsignacionPorCapacidad), prefiriendo los de la zona indicada, y lo
     * deja OCUPADO.
     *
     * @param idPedido pedido a repartir
     * @param idZonaPreferida zona del pedido; null = cualquiera
     * @param pesoKg kilos del pedido; null si no se conocen
     * @param bultos bultos del pedido
     * @param idRepartidorExcluido el que devolvió el pedido al depósito; null = ninguno
     * @return el ID del repartidor asignado
     * @throws SinLugarException (rollback) si no le entra a nadie o ya pasó
     *         la hora de corte
     */
    Long asignarRepartidor(Long idPedido, Long idZonaPreferida, java.math.BigDecimal pesoKg, int bultos,
                           Long idRepartidorExcluido);

    /**
     * Por qué el pedido no se puede asignar ahora (y tiene que ir a la
     * lista de espera), o null si hay a quién dárselo. Es una consulta sin
     * bloqueo: Pedidos la usa antes de cobrar, para no cobrar un pedido que
     * va a quedar esperando.
     */
    String motivoParaEsperar(java.math.BigDecimal pesoKg, int bultos, Long idRepartidorExcluido);

    /** Si a esta hora todavía se suman pedidos a la hoja del día (ver HoraDeCorte). */
    boolean antesDelCorte();

    /**
     * En qué hoja de ruta entra un pedido recibido a esa hora: la de ese
     * día si llegó antes del corte, la del siguiente si no (ADR-042). Pedidos
     * la usa para la fecha prometida.
     */
    java.time.LocalDate diaDeEntregaPara(java.time.LocalDateTime recibido);

    /**
     * Le saca el pedido al repartidor (se entregó o se canceló después de
     * confirmado). Si era el último que llevaba, vuelve a DISPONIBLE.
     *
     * @param idRepartidor repartidor que lo llevaba
     * @param idPedido pedido que deja de llevar
     */
    void liberarRepartidor(Long idRepartidor, Long idPedido);

    /**
     * La última ubicación que compartió el repartidor (ADR-035), sea de
     * cuando sea, o null si nunca compartió. Pedidos la usa como respaldo
     * de la ubicación de una visita fallida (ADR-045) y decide si es
     * reciente.
     */
    com.rabbit.repartidores.dto.UbicacionDTO ultimaUbicacion(Long idRepartidor);
}
