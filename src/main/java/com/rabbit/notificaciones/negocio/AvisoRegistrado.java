package com.rabbit.notificaciones.negocio;

/**
 * Evento CDI: se guardó un aviso para un comercio. Lo observa AvisosPorMail
 * recién cuando la transacción se confirma (AFTER_SUCCESS), así un aviso
 * que se deshace no llega a mandarse por mail.
 *
 * Lleva el email ya resuelto (null si el comercio no cargó uno): después
 * del commit no se puede consultar la base (el contenedor ya no da una
 * conexión en esa fase), así que se busca antes, dentro de la transacción.
 */
public record AvisoRegistrado(Long idComercio, String email, Long idPedido, String texto) {
}
