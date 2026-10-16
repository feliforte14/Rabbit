package com.rabbit.pedidos.datos.model;

/**
 * Por qué el repartidor no pudo entregar un pedido (ADR-041). Son fijos,
 * no texto libre, para poder contarlos en los indicadores (ADR-044) y para
 * decidir si la visita cuenta como intento.
 *
 * cuentaComoVisita: si el repartidor llegó a ir a la dirección. "No entra
 * en el vehículo" se da antes de salir, así que no gasta un intento.
 *
 * devuelveAlComercio: el cliente rechazó el pedido, así que volver a
 * intentarlo no tiene sentido: vuelve al comercio sin esperar a agotar
 * los intentos (es lo que hacen los operadores con un rechazo).
 */
public enum MotivoEntregaFallida {
    CLIENTE_AUSENTE("Cliente ausente", true, false),
    DIRECCION_INCORRECTA("Dirección incorrecta o incompleta", true, false),
    RECHAZADO("El cliente rechazó el pedido", true, true),
    ZONA_PELIGROSA("Zona peligrosa o inaccesible", true, false),
    NO_ENTRA_EN_VEHICULO("No entra en el vehículo", false, false),
    OTRO("Otro", true, false);

    private final String descripcion;
    private final boolean cuentaComoVisita;
    private final boolean devuelveAlComercio;

    MotivoEntregaFallida(String descripcion, boolean cuentaComoVisita, boolean devuelveAlComercio) {
        this.descripcion = descripcion;
        this.cuentaComoVisita = cuentaComoVisita;
        this.devuelveAlComercio = devuelveAlComercio;
    }

    public String getDescripcion() { return descripcion; }
    public boolean isCuentaComoVisita() { return cuentaComoVisita; }
    public boolean isDevuelveAlComercio() { return devuelveAlComercio; }
}
