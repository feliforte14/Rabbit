package com.rabbit.pagos.negocio;

/**
 * CAPA DE NEGOCIO — componente ServicioDePagosYCobranzas (EJB @Stateless).
 *
 * Cuándo se cobra, según el medio de pago del pedido:
 *   - PREPAGO: al confirmar el pedido (PedidoService.confirmarPedido llama
 *     a registrarCobro). Se autoriza por SOAP contra el banco legado
 *     (IBancoClient) y queda ACREDITADO con el código del banco.
 *   - CONTRA_ENTREGA: al confirmar queda PENDIENTE; lo efectiviza el
 *     suscriptor de Pagos al tópico de estados cuando el pedido pasa a
 *     ENTREGADO (ver SuscriptorPagosEstadoPedido).
 *
 * TRANSACCIONES: todo con REQUIRED, así registrarCobro se suma a la
 * transacción de confirmarPedido. Un pago rechazado (o un banco que no
 * responde) lanza ValidacionException (@ApplicationException(rollback =
 * true)) y deshace la confirmación entera.
 *
 * EL BANCO NO SE DESHACE CON UN ROLLBACK: si el banco autorizó y después la
 * confirmación falla, el rollback no le devuelve la plata al cliente. Por
 * eso cada autorización dispara PagoAutorizado, y ReversasBancarias le pide
 * al banco la reversa si la transacción termina deshaciéndose.
 *
 * SEGURIDAD: mismo esquema que ComercioService — @PermitAll de clase y
 * @RolesAllowed("ADMINISTRADOR") en anularCobro, lo más sensible del
 * componente (devolver plata). El suscriptor del tópico no tiene usuario,
 * por eso registrarCobroContraEntrega no puede llevar restricción de rol.
 * Las lecturas son del personal de Rabbit; un COMERCIO lee solo sus cobros
 * (el cobro guarda el comercio dueño y el comercio sale de la identidad
 * autenticada, ver IContextoUsuario).
 */

import com.rabbit.integracion.banco.IBancoClient;
import com.rabbit.integracion.banco.ResultadoAutorizacion;
import com.rabbit.pagos.datos.CobroRepository;
import com.rabbit.pagos.datos.model.Cobro;
import com.rabbit.pagos.datos.model.EstadoCobro;
import com.rabbit.pagos.dto.CobroDTO;
import com.rabbit.pagos.dto.MedioPago;
import com.rabbit.seguridad.negocio.IContextoUsuario;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR", "COMERCIO"})
@PermitAll
public class PagoService implements IRegistroCobros, IConsultaCobros {

    private static final Logger LOG = Logger.getLogger(PagoService.class.getName());

    @Inject
    private CobroRepository repository;

    @Inject
    private IBancoClient banco;

    @Inject
    private Event<PagoAutorizado> pagoAutorizado;

    @Inject
    private Event<CobroAnulado> cobroAnulado;

    @Inject
    private IContextoUsuario contextoUsuario;

    // ===============================================================
    // IRegistroCobros
    // ===============================================================

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Long registrarCobro(Long idPedido, Long idComercio, BigDecimal importe, MedioPago medio) {
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
        cobro.setIdComercio(idComercio);
        cobro.setImporte(importe);
        cobro.setMedioPago(medio);
        cobro.setFechaCreacion(LocalDateTime.now());
        if (medio == MedioPago.PREPAGO) {
            String codigo = autorizarEnBanco(idPedido, importe);
            cobro.setCodigoAutorizacion(codigo);
            cobro.setEstado(EstadoCobro.ACREDITADO);
            cobro.setFechaAcreditacion(LocalDateTime.now());
            // Desde acá el banco ya cobró: si esta transacción se deshace,
            // ReversasBancarias le pide la reversa (AFTER_FAILURE).
            pagoAutorizado.fire(new PagoAutorizado(idPedido, codigo));
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
        // Un PREPAGO ya se cobró en el banco: la devolución se pide recién
        // cuando la cancelación quede confirmada (AFTER_SUCCESS).
        if (cobro.getCodigoAutorizacion() != null) {
            cobroAnulado.fire(new CobroAnulado(idPedido, cobro.getCodigoAutorizacion()));
        }
    }

    // Llamada sincrónica: sin la respuesta del banco no se puede confirmar.
    private String autorizarEnBanco(Long idPedido, BigDecimal importe) {
        ResultadoAutorizacion resultado = banco.autorizar(idPedido, importe);
        switch (resultado.getEstado()) {
            case APROBADO:
                return resultado.getCodigoAutorizacion();
            case RECHAZADO:
                throw new ValidacionException("Pago rechazado por el banco: " + resultado.getMotivo());
            default:
                throw new ValidacionException("El banco no respondió: el pedido " + idPedido
                        + " no se confirmó. Intentá de nuevo en unos minutos.");
        }
    }

    // ===============================================================
    // IConsultaCobros
    // ===============================================================

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public CobroDTO obtenerCobroDePedido(Long idPedido) {
        Cobro cobro = repository.buscarPorPedido(idPedido);
        return cobro != null ? CobroDTO.desde(cobro) : null;
    }

    @Override
    @RolesAllowed({"ADMINISTRADOR", "OPERADOR"})
    public List<CobroDTO> listarTodos() {
        return repository.listarTodos().stream().map(CobroDTO::desde).collect(Collectors.toList());
    }

    @Override
    @RolesAllowed("COMERCIO")
    public List<CobroDTO> listarCobrosDelComercioActual() {
        return repository.listarDeComercio(contextoUsuario.idComercioActual()).stream()
                .map(CobroDTO::desde)
                .collect(Collectors.toList());
    }
}
