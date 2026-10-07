package com.rabbit.transportistas.negocio;

import com.rabbit.transportistas.datos.model.EstadoEnvio;

/**
 * Evento CDI sincrónico: el transportista informó un estado nuevo para un
 * envío. Lo observa Pedidos (ActualizacionDePedidosPorEnvio), que mueve el
 * pedido en la misma transacción. Así Transportistas no depende de Pedidos.
 */
public record EstadoEnvioCambiado(Long idPedido, EstadoEnvio anterior, EstadoEnvio nuevo) {
}
