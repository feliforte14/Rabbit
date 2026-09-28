package com.rabbit.pagos.negocio;

/**
 * CAPA DE NEGOCIO — componente ServicioDePagosYCobranzas (EJB @Stateless).
 *
 * Cuándo se cobra, según el medio de pago del pedido:
 *   - PREPAGO: al confirmar el pedido (PedidoService.confirmarPedido llama
 *     a registrarCobro). Se autoriza contra la pasarela y queda ACREDITADO.
 *   - CONTRA_ENTREGA: al confirmar queda PENDIENTE; lo efectiviza el
 *     suscriptor de Pagos al tópico de estados cuando el pedido pasa a
 *     ENTREGADO (ver SuscriptorPagosEstadoPedido).
 *
 * TRANSACCIONES: todo con REQUIRED, así registrarCobro se suma a la
 * transacción de confirmarPedido. Un pago rechazado lanza
 * ValidacionException (@ApplicationException(rollback = true)) y deshace
 * la confirmación entera, incluida la asignación del repartidor.
 *
 * SEGURIDAD: mismo esquema que ComercioService — @PermitAll de clase y
 * @RolesAllowed("ADMINISTRADOR") en anularCobro, lo más sensible del
 * componente (devolver plata). El suscriptor del tópico no tiene usuario,
 * por eso registrarCobroContraEntrega no puede llevar restricción de rol.
 */

import com.rabbit.pagos.datos.CobroRepository;
import com.rabbit.pagos.datos.model.Cobro;
import com.rabbit.pagos.datos.model.EstadoCobro;
import com.rabbit.pagos.dto.CobroDTO;
import com.rabbit.pagos.dto.MedioPago;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR"})
@PermitAll
public class PagoService implements IRegistroCobros, IConsultaCobros {

    private static final Logger LOG = Logger.getLogger(PagoService.class.getName());

    /**
     * Pasarela simulada, con una regla determinística para la demo (mismo
     * criterio que el CUIT 20-00000000-0 del padrón fiscal): un PREPAGO por
     * encima de este importe se rechaza, así el camino de rechazo y su
     * rollback se pueden mostrar a pedido.
     */
    public static final BigDecimal LIMITE_PASARELA = new BigDecimal("500000");

    @Inject
    private CobroRepository repository;

    // ===============================================================
    // IRegistroCobros
    // ===============================================================

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Long registrarCobro(Long idPedido, BigDecimal importe, MedioPago medio) {
        if (importe == null || importe.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("El pedido " + idPedido + " no tiene un importe válido para cobrar");
        }
        if (medio == null) {
            throw new ValidacionException("El pedido " + idPedido + " no tiene medio de pago");
        }
        if (repository.buscarPorPedido(idPedido) != null) {
            throw new ValidacionException("El pedido " + idPedido + " ya tiene un cobro registrado");
        }

        Cobro cobro = new Cobro();
        cobro.setIdPedido(idPedido);
        cobro.setImporte(importe);
        cobro.setMedioPago(medio);
        cobro.setFechaCreacion(LocalDateTime.now());
        if (medio == MedioPago.PREPAGO) {
            autorizarEnPasarela(idPedido, importe);
            cobro.setEstado(EstadoCobro.ACREDITADO);
            cobro.setFechaAcreditacion(LocalDateTime.now());
        } else {
            cobro.setEstado(EstadoCobro.PENDIENTE);
        }
        Long id = repository.guardar(cobro).getId();
        LOG.info("[Pagos] Cobro " + id + " del pedido " + idPedido + ": " + medio + " -> " + cobro.getEstado());
        return id;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void registrarCobroContraEntrega(Long idPedido) {
        Cobro cobro = repository.buscarPorPedidoParaActualizar(idPedido);
        // Idempotente: el mismo evento ENTREGADO puede llegar más de una vez,
        // y un pedido PREPAGO (ya acreditado) o sin cobro no tiene nada que
        // efectivizar.
        if (cobro == null || cobro.getMedioPago() != MedioPago.CONTRA_ENTREGA
                || cobro.getEstado() != EstadoCobro.PENDIENTE) {
            return;
        }
        cobro.setEstado(EstadoCobro.ACREDITADO);
        cobro.setFechaAcreditacion(LocalDateTime.now());
        repository.actualizar(cobro);
        LOG.info("[Pagos] Cobro contra entrega del pedido " + idPedido + " acreditado");
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ADMINISTRADOR")
    public void anularCobro(Long idPedido) {
        Cobro cobro = repository.buscarPorPedidoParaActualizar(idPedido);
        if (cobro == null || cobro.getEstado() == EstadoCobro.ANULADO) {
            return;
        }
        cobro.setEstado(EstadoCobro.ANULADO);
        repository.actualizar(cobro);
        LOG.info("[Pagos] Cobro del pedido " + idPedido + " anulado");
    }

    private void autorizarEnPasarela(Long idPedido, BigDecimal importe) {
        if (importe.compareTo(LIMITE_PASARELA) > 0) {
            throw new ValidacionException("Pago rechazado por la pasarela: el pedido " + idPedido
                    + " supera el límite de $" + LIMITE_PASARELA.toPlainString());
        }
    }

    // ===============================================================
    // IConsultaCobros
    // ===============================================================

    @Override
    public CobroDTO obtenerCobroDePedido(Long idPedido) {
        Cobro cobro = repository.buscarPorPedido(idPedido);
        return cobro != null ? CobroDTO.desde(cobro) : null;
    }

    @Override
    public List<CobroDTO> listarTodos() {
        return repository.listarTodos().stream().map(CobroDTO::desde).collect(Collectors.toList());
    }
}
