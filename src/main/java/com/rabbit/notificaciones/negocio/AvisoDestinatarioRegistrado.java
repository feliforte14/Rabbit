package com.rabbit.notificaciones.negocio;

/**
 * Evento CDI: se guardó un aviso para quien recibe un pedido (ADR-040). Lo
 * observa AvisosPorMail recién cuando la transacción se confirma
 * (AFTER_SUCCESS), con todo ya resuelto: después del commit no se puede
 * consultar la base (ver AvisoRegistrado).
 */
public record AvisoDestinatarioRegistrado(Long idPedido, String email, String asunto, String texto) {
}
