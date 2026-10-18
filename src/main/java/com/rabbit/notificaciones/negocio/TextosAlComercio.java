package com.rabbit.notificaciones.negocio;

/**
 * El aviso al comercio cuando un pedido no se pudo entregar (ADR-041,
 * ADR-045): qué pasó (motivo y comentario del repartidor), en qué visita
 * va, qué pasa ahora con el pedido y qué conviene que haga el comercio.
 * Va al portal y por mail (NotificacionService, AvisosPorMail).
 *
 * A diferencia del aviso al cliente, acá sí va el comentario del
 * repartidor: el comercio es quien tiene que resolver (corregir la
 * dirección, reponer un paquete dañado, hablar con su cliente).
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;

final class TextosAlComercio {

    private TextosAlComercio() {
    }

    /**
     * Todos con la misma forma (revisados el 18/10/2026): qué pasó (con el
     * motivo), qué sigue con el pedido y qué conviene que haga el comercio,
     * y al final dónde ver el detalle. Frases cortas, sin mayúsculas ni
     * "visita(s)".
     *
     * @param estado PENDIENTE (se reintenta) o DEVUELTO_AL_COMERCIO
     * @param motivo el de la última visita (no null)
     * @param detalle motivo y comentario como lo guardó Pedidos ("Cliente ausente: tocó timbre..."), o null
     * @param contoComoVisita si esta falla le contó una visita al cliente
     * @param visitas visitas fallidas hasta ahora
     * @param maximo visitas antes de devolverlo
     * @param conStock si la mercadería salió del stock consignado en Rabbit
     *        (si salió de un punto de picking, no hay stock que devolver)
     */
    static String entregaFallida(Long idPedido, String estado, MotivoEntregaFallida motivo, String detalle,
                                 boolean contoComoVisita, int visitas, int maximo, boolean conStock) {
        String pedido = "Tu pedido #" + idPedido;
        String que = " (" + (detalle != null ? detalle : motivo.getDescripcion()) + ")";
        String verDetalle = " Detalle en Mis pedidos → Ver visitas.";

        if ("DEVUELTO_AL_COMERCIO".equals(estado)) {
            if (motivo == MotivoEntregaFallida.PAQUETE_DANADO) {
                return pedido + " no se entregó porque el paquete está dañado" + que + " y vuelve a tu comercio."
                        + " Se anula el cobro y la mercadería no vuelve a tu stock."
                        + " Coordiná con Rabbit qué hacer con ella y el reemplazo." + verDetalle;
            }
            String porque = motivo == MotivoEntregaFallida.RECHAZADO ? " porque el cliente lo rechazó"
                    : " después de " + visitas + (visitas == 1 ? " visita" : " visitas");
            String sigue = conStock ? " Se anula el cobro y se repone tu stock."
                    : " Se anula el cobro.";
            String hacer = motivo == MotivoEntregaFallida.RECHAZADO
                    ? " Consultá con tu cliente qué pasó."
                    : " Coordiná otra entrega con tu cliente.";
            return pedido + " no se entregó" + porque + que + " y vuelve a tu comercio." + sigue + hacer + verDetalle;
        }

        if (!contoComoVisita) {
            return pedido + " se demora por un problema del reparto" + que + "."
                    + " No cuenta como visita y Rabbit se lo asigna a otro repartidor. No tenés que hacer nada.";
        }
        String sigue = visitas + 1 >= maximo
                ? " Queda una visita más: si falla, vuelve a tu comercio."
                : " Rabbit lo vuelve a intentar.";
        String hacer = switch (motivo) {
            case CLIENTE_AUSENTE -> " Avisale a tu cliente que alguien lo tiene que recibir.";
            case DIRECCION_INCORRECTA -> " Confirmá la dirección con tu cliente.";
            case NADIE_HABILITADO -> " Lo tiene que recibir un mayor de edad con DNI.";
            case DOMICILIO_CERRADO -> " Si tiene horario de recepción, avisale a Rabbit.";
            case ZONA_PELIGROSA -> " Si hay otra dirección u horario, avisale a Rabbit.";
            default -> "";
        };
        return pedido + " no se entregó en la visita " + visitas + " de " + maximo + que + "." + sigue + hacer
                + verDetalle;
    }
}
