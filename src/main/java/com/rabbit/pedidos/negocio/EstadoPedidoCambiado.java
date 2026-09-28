package com.rabbit.pedidos.negocio;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import java.time.LocalDateTime;

/**
 * Evento CDI que dispara PedidoService en cada cambio de estado de un
 * pedido (ver EstadoPedido). Es el formato acordado para el tópico
 * "topico.pedidos.estado": su publicador (a implementar) lo va a observar
 * con TransactionPhase.AFTER_SUCCESS, igual que PublicadorPedidosExternos
 * con la cola, y lo va a mandar como JSON con estos mismos campos.
 *
 * fechaCambio sirve para ordenar: un MDB no garantiza el orden de
 * llegada, así que cada suscriptor guarda la última fechaCambio que
 * procesó por pedido e ignora los eventos más viejos.
 */
public record EstadoPedidoCambiado(Long idPedido, Long idComercio, EstadoPedido estado, LocalDateTime fechaCambio) {
}
