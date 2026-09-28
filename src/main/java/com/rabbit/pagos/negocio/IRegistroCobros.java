package com.rabbit.pagos.negocio;

/**
 * CONTRATO DE ESCRITURA del componente ServicioDePagosYCobranzas.
 *
 * Por ahora solo la interfaz: fija las firmas que va a usar
 * PedidoService.confirmarPedido (flujo transaccional asignar repartidor →
 * cobrar → confirmar) para que Pedidos y Pagos puedan avanzar en paralelo.
 *
 * Todas las operaciones de escritura corren con el default de un EJB
 * (REQUIRED): se suman a la transacción del llamador. Así, si falla un
 * paso posterior de confirmarPedido, el cobro registrado acá se deshace
 * junto con todo lo demás. Las fallas de negocio (pago rechazado, importe
 * inválido) se lanzan como excepción @ApplicationException(rollback = true)
 * propia de Pagos.
 */

import com.rabbit.pagos.dto.MedioPago;
import jakarta.ejb.Local;
import java.math.BigDecimal;

@Local
public interface IRegistroCobros {

    /**
     * Registra el cobro de un pedido al confirmarlo. Con PREPAGO lo
     * autoriza contra la pasarela (simulada) y queda ACREDITADO; con
     * CONTRA_ENTREGA queda PENDIENTE hasta la entrega.
     *
     * @param idPedido pedido al que corresponde el cobro
     * @param importe  importe a cobrar, mayor a cero
     * @param medio    medio de pago elegido en el ERP del comercio
     * @return el ID del cobro registrado
     * @throws RuntimeException de aplicación (rollback) si el pago se
     *         rechaza o los datos son inválidos
     */
    Long registrarCobro(Long idPedido, BigDecimal importe, MedioPago medio);

    /**
     * Efectiviza el cobro CONTRA_ENTREGA de un pedido que ya se entregó.
     * La dispara el suscriptor de Pagos al tópico de estados del pedido.
     * Idempotente: si el cobro ya está ACREDITADO, no hace nada (el mismo
     * evento puede llegar más de una vez).
     *
     * @param idPedido pedido entregado
     */
    void registrarCobroContraEntrega(Long idPedido);

    /**
     * Anula el cobro de un pedido cancelado. Operación sensible: la
     * implementación va con @RolesAllowed("ADMINISTRADOR").
     *
     * @param idPedido pedido cuyo cobro se anula
     */
    void anularCobro(Long idPedido);
}
