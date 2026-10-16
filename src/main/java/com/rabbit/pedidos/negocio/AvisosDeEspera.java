package com.rabbit.pedidos.negocio;

/**
 * Dispara una pasada de la lista de espera (AsignadorDeEspera) apenas un
 * pedido se entrega, se cancela o se devuelve al depósito: en ese momento
 * a algún repartidor le pudo quedar lugar (ADR-037).
 *
 * Escucha EstadoPedidoCambiado después del commit (AFTER_SUCCESS) y no
 * asigna ahí mismo: programa un timer que llama a AsignadorDeEspera desde
 * su propio hilo. Así no demora la pantalla de quien hizo el cambio ni
 * corre con su usuario (un REPARTIDOR no puede confirmar pedidos).
 *
 * POR QUÉ ES OTRO BEAN: AsignadorDeEspera tiene @RunAs("OPERADOR"), y al
 * entrar a un bean así Elytron busca esa identidad en el realm (la tabla
 * usuarios, ADR-031). Durante AFTER_SUCCESS la transacción ya terminó y esa
 * consulta falla (RealmUnavailableException), así que el aviso se perdía.
 * Este bean no tiene @RunAs: solo crea el timer, y la llamada al asignador
 * sale del hilo del timer, donde no hay problema.
 */

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Singleton;
import jakarta.ejb.Timeout;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import com.rabbit.pedidos.datos.model.EstadoPedido;

@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@PermitAll
public class AvisosDeEspera {

    @Resource
    private TimerService timers;

    @Inject
    private AsignadorDeEspera asignador;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void alCambiarEstado(@Observes(during = TransactionPhase.AFTER_SUCCESS) EstadoPedidoCambiado evento) {
        EstadoPedido estado = evento.estado();
        if (estado == EstadoPedido.ENTREGADO || estado == EstadoPedido.CANCELADO || estado == EstadoPedido.PENDIENTE) {
            timers.createSingleActionTimer(500, new TimerConfig(null, false));
        }
    }

    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void alVencer() {
        asignador.procesar();
    }
}
