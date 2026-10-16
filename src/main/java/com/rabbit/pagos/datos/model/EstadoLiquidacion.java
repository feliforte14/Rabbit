package com.rabbit.pagos.datos.model;

/**
 * Estado del cobro de una liquidación mensual (ADR-034).
 *
 *   PENDIENTE --- banco aprueba ---> COBRADA
 *       |
 *       +------ banco rechaza ----> RECHAZADA (se reintenta a mano)
 *
 * Si el banco no responde, queda PENDIENTE y el timer diario la reintenta.
 */
public enum EstadoLiquidacion {
    PENDIENTE,
    COBRADA,
    RECHAZADA
}
