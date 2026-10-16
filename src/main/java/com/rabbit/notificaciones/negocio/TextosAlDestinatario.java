package com.rabbit.notificaciones.negocio;

/**
 * Arma el asunto y el texto del aviso a quien recibe el pedido (ADR-040),
 * según el estado nuevo. Le habla al cliente final, no al comercio: nada de
 * importes, cobros ni números internos de Rabbit, y siempre con el link al
 * seguimiento público.
 *
 * Un PENDIENTE con motivo de devolución no es "recibimos tu pedido": es una
 * visita que falló y se va a volver a intentar (ADR-041).
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.infraestructura.Formatos;
import java.time.LocalDate;

final class TextosAlDestinatario {

    private TextosAlDestinatario() {
    }

    /** Lo que hace falta para escribir el aviso. nombre, fechaPrometida y motivo pueden ser null. */
    record Datos(String estado, String nombre, String comercio, String codigo, String enlace,
                 LocalDate fechaPrometida, LocalDate hoy, String motivo) {
    }

    static String asunto(Datos d) {
        String de = " de " + d.comercio();
        return switch (d.estado()) {
            case "PENDIENTE" -> d.motivo() != null ? "No pudimos entregar tu pedido" + de : "Recibimos tu pedido" + de;
            case "CONFIRMADO" -> "Tu pedido" + de + " ya tiene quién lo lleve";
            case "EN_CAMINO" -> "¡Tu pedido" + de + " llega hoy!";
            case "ENTREGADO" -> "Entregamos tu pedido" + de;
            case "CANCELADO" -> "Tu pedido" + de + " fue cancelado";
            case "DEVUELTO_AL_COMERCIO" -> "Tu pedido" + de + " volvió a la tienda";
            default -> "Novedades de tu pedido" + de;
        };
    }

    static String cuerpo(Datos d) {
        String saludo = d.nombre() != null ? "Hola, " + d.nombre() + ". " : "Hola. ";
        String texto = switch (d.estado()) {
            case "PENDIENTE" -> d.motivo() != null
                    ? "Pasamos a entregar tu pedido y no pudimos (" + d.motivo() + "). Lo vamos a intentar de nuevo"
                            + " y te avisamos cuando vuelva a salir."
                    : d.comercio() + " nos pasó tu pedido y Rabbit lo va a llevar." + cuando(d.fechaPrometida(), d.hoy());
            case "CONFIRMADO" -> "Ya le asignamos quién lo lleva. Te avisamos cuando salga a reparto.";
            case "EN_CAMINO" -> "Tu pedido salió a reparto y llega hoy. En el seguimiento ves por dónde va.";
            case "ENTREGADO" -> "Tu pedido fue entregado. ¡Gracias!";
            case "CANCELADO" -> "El pedido se canceló. Si no lo esperabas, consultá con " + d.comercio() + ".";
            case "DEVUELTO_AL_COMERCIO" -> "No pudimos entregarlo" + (d.motivo() != null ? " (" + d.motivo() + ")" : "")
                    + " y se lo devolvimos a " + d.comercio() + ". Comunicate con la tienda para coordinar otra entrega.";
            default -> "Tu pedido cambió de estado.";
        };
        return saludo + texto + "\n\nSeguilo con el código " + d.codigo() + ": " + d.enlace();
    }

    /** " Llega hoy.", " Llega mañana.", " Llega el viernes 17/10." o nada si no hay fecha. */
    static String cuando(LocalDate prometida, LocalDate hoy) {
        if (prometida == null || prometida.isBefore(hoy)) {
            return "";
        }
        return " Llega " + Formatos.diaRelativo(prometida, hoy) + ".";
    }
}
