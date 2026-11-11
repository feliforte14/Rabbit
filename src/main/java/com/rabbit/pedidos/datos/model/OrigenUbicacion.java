package com.rabbit.pedidos.datos.model;

/**
 * De dónde salió la ubicación registrada en una visita fallida (ADR-045).
 *
 * DISPOSITIVO: la tomó el celular del repartidor al abrir la ventana de
 * "No se pudo entregar", así que es la del momento.
 *
 * ULTIMA_COMPARTIDA: el celular no la dio (sin permiso o sin señal) y se
 * usó la última que el repartidor compartió mientras repartía (ADR-035),
 * si era de los últimos minutos. Es aproximada: puede ser de unas cuadras
 * antes.
 */
public enum OrigenUbicacion {
    DISPOSITIVO,
    ULTIMA_COMPARTIDA
}
