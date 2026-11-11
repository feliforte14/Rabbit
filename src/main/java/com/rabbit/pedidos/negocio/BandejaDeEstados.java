package com.rabbit.pedidos.negocio;

/**
 * CAPA DE NEGOCIO — bandeja de salida (outbox) de los cambios de estado.
 *
 * POR QUÉ EXISTE
 * Antes, PublicadorEstadosPedido mandaba el aviso al tópico después del
 * commit y, si el broker estaba caído, lo perdía (solo un warning en el
 * log). Con eso, un pedido podía quedar ENTREGADO con el cobro contra
 * entrega sin acreditar y sin aviso al comercio, porque Pagos y
 * Notificaciones se enteran por el tópico.
 *
 * Ahora el cambio de estado y su aviso se guardan juntos (registrar, que se
 * suma a la transacción del cambio) y el envío es aparte (EnvioDeEstadosPendientes):
 * lo que no salió queda pendiente y se reenvía.
 *
 * Cada método de lectura y de marca abre su propia transacción (REQUIRES_NEW):
 * quien envía corre sin transacción y no debe mezclarse con una ajena.
 */

import com.rabbit.pedidos.datos.EventoEstadoRepository;
import com.rabbit.pedidos.datos.model.EventoEstadoPedido;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;

@Stateless
public class BandejaDeEstados {

    /** Un evento pendiente, sin la entidad (viaja fuera de la transacción). */
    public record Pendiente(Long id, EstadoPedidoCambiado evento) {
    }

    @Inject
    private EventoEstadoRepository repository;

    /** Se suma a la transacción del cambio de estado: si ésta se deshace, el aviso también. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void registrar(EstadoPedidoCambiado evento) {
        EventoEstadoPedido fila = new EventoEstadoPedido();
        fila.setIdPedido(evento.idPedido());
        fila.setIdComercio(evento.idComercio());
        fila.setEstado(evento.estado().name());
        fila.setFechaCambio(evento.fechaCambio());
        fila.setCreadoEn(LocalDateTime.now());
        repository.guardar(fila);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<Pendiente> pendientes(int maximo) {
        return repository.listarPendientes(maximo).stream()
                .map(f -> new Pendiente(f.getId(), new EstadoPedidoCambiado(
                        f.getIdPedido(), f.getIdComercio(),
                        com.rabbit.pedidos.datos.model.EstadoPedido.valueOf(f.getEstado()),
                        f.getFechaCambio())))
                .toList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void marcarPublicado(Long id) {
        repository.marcarPublicado(id, LocalDateTime.now());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void registrarFallo(Long id) {
        repository.sumarIntentoFallido(id);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int limpiarPublicadosAnterioresA(LocalDateTime limite) {
        return repository.borrarPublicadosAnterioresA(limite);
    }
}
