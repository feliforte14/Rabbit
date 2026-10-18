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
     * @param estado PENDIENTE (se reintenta) o DEVUELTO_AL_COMERCIO
     * @param motivo el de la última visita (no null)
     * @param detalle motivo y comentario como lo guardó Pedidos ("Cliente ausente: tocó timbre..."), o null
     * @param contoComoVisita si esta falla le contó una visita al cliente
     * @param visitas visitas fallidas hasta ahora
     * @param maximo visitas antes de devolverlo
     */
    static String entregaFallida(Long idPedido, String estado, MotivoEntregaFallida motivo, String detalle,
                                 boolean contoComoVisita, int visitas, int maximo) {
        String pedido = "Tu pedido #" + idPedido;
        String que = " (" + (detalle != null ? detalle : motivo.getDescripcion()) + ")";
        String fotos = ". Fotos y ubicación en Mis pedidos → Ver visitas";

        if ("DEVUELTO_AL_COMERCIO".equals(estado)) {
            if (motivo == MotivoEntregaFallida.PAQUETE_DANADO) {
                return "INCIDENCIA: paquete dañado en tu pedido #" + idPedido + (detalle != null ? " (" + detalle + ")" : "")
                        + ". No se entregó, el cobro se anula y la mercadería NO vuelve a tu stock:"
                        + " coordiná con Rabbit qué hacer con ella y el reemplazo" + fotos;
            }
            String porque = motivo == MotivoEntregaFallida.RECHAZADO ? " porque el cliente lo rechazó"
                    : " después de " + visitas + " visita(s)";
            return pedido + " no se pudo entregar" + porque + que
                    + " y vuelve a tu comercio: el stock consignado vuelve a tu stock y el cobro se anula" + fotos;
        }

        if (!contoComoVisita) {
            return pedido + " no se pudo entregar por un problema del reparto" + que
                    + ": no le cuenta una visita a tu cliente y Rabbit se lo asigna a otro repartidor";
        }
        String accion = switch (motivo) {
            case DIRECCION_INCORRECTA -> ". Revisá la dirección con tu cliente y avisale a Rabbit si está mal";
            case DOMICILIO_CERRADO -> ". Si tu cliente tiene horario de recepción, avisale a Rabbit";
            case NADIE_HABILITADO -> ". Que lo reciba un mayor de edad con DNI";
            default -> "";
        };
        String ultima = visitas + 1 >= maximo
                ? ". La próxima es la última: si falla, vuelve a tu comercio"
                : ". Rabbit se lo asigna a otro repartidor";
        return pedido + ": visita " + visitas + " de " + maximo + " fallida" + que + ultima
                + accion + fotos;
    }
}
