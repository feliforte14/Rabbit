package com.rabbit.pedidos.datos.model;

/**
 * Por qué el repartidor no pudo entregar un pedido (ADR-041, ADR-045). Son
 * fijos, no texto libre, para poder contarlos en los indicadores (ADR-044)
 * y para decidir qué pasa con el pedido.
 *
 * cuentaComoVisita: si le gasta un intento al cliente. Solo los motivos
 * del lado del cliente o de la dirección; "no entra en el vehículo", un
 * problema del repartidor o un paquete dañado no son culpa de quien recibe.
 *
 * devuelveAlComercio: volver a intentarlo no tiene sentido (el cliente lo
 * rechazó, o el paquete está dañado): vuelve al comercio sin esperar a
 * agotar los intentos.
 *
 * pideComentario / pideFoto: la evidencia que exige la pantalla del
 * repartidor y PedidoService (ver EvidenciaDeVisita). La foto y el
 * comentario siempre se pueden cargar; estos solo los vuelven obligatorios.
 *
 * El orden es el de la lista desplegable: primero lo más común.
 */
public enum MotivoEntregaFallida {
    CLIENTE_AUSENTE("Cliente ausente", true, false, false, false),
    DIRECCION_INCORRECTA("Dirección incorrecta o inexistente", true, false, false, false),
    NADIE_HABILITADO("Nadie habilitado para recibir (menor, sin DNI o sin código)", true, false, false, false),
    DOMICILIO_CERRADO("Local o domicilio cerrado", true, false, false, false),
    ZONA_PELIGROSA("Zona inaccesible o insegura", true, false, false, false),
    RECHAZADO("El cliente rechazó el pedido", true, true, true, false),
    PAQUETE_DANADO("Paquete dañado", false, true, false, true),
    NO_ENTRA_EN_VEHICULO("No entra en el vehículo", false, false, false, false),
    PROBLEMA_DEL_REPARTIDOR("Problema del repartidor (vehículo, tiempo, clima)", false, false, false, false),
    OTRO("Otro", true, false, true, false);

    private final String descripcion;
    private final boolean cuentaComoVisita;
    private final boolean devuelveAlComercio;
    private final boolean pideComentario;
    private final boolean pideFoto;

    MotivoEntregaFallida(String descripcion, boolean cuentaComoVisita, boolean devuelveAlComercio,
                         boolean pideComentario, boolean pideFoto) {
        this.descripcion = descripcion;
        this.cuentaComoVisita = cuentaComoVisita;
        this.devuelveAlComercio = devuelveAlComercio;
        this.pideComentario = pideComentario;
        this.pideFoto = pideFoto;
    }

    public String getDescripcion() { return descripcion; }
    public boolean isCuentaComoVisita() { return cuentaComoVisita; }
    public boolean isDevuelveAlComercio() { return devuelveAlComercio; }
    public boolean isPideComentario() { return pideComentario; }
    public boolean isPideFoto() { return pideFoto; }
}
