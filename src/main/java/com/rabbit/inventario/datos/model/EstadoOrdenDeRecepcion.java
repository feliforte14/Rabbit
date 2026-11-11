package com.rabbit.inventario.datos.model;

/**
 * Estados de una orden de recepción (ADR-058): el comercio anuncia la mercadería que va a mandar y el
 * depósito registra lo que llegó de verdad.
 *
 *   ANUNCIADA ── recepción con todo lo anunciado (o más) ──> RECIBIDA
 *       │
 *       ├──── recepción con menos de lo anunciado en alguna línea ──> PARCIAL
 *       └──── el comercio (o el personal) la cancela ──> CANCELADA
 *
 * RECIBIDA, PARCIAL y CANCELADA son finales: una recepción se registra una sola vez (lo que no llegó,
 * no llegó; si después llega, se anuncia de nuevo).
 */
public enum EstadoOrdenDeRecepcion {
    ANUNCIADA, RECIBIDA, PARCIAL, CANCELADA
}
