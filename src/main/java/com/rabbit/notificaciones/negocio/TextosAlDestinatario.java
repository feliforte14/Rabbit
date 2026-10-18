package com.rabbit.notificaciones.negocio;

/**
 * Arma el asunto y el texto del aviso a quien recibe el pedido (ADR-040),
 * según el estado nuevo. Le habla al cliente final, no al comercio: nada de
 * importes, cobros ni números internos de Rabbit, y siempre con el link al
 * seguimiento público.
 *
 * Un PENDIENTE con motivo de entrega fallida no es "recibimos tu pedido":
 * es una visita que falló y se va a volver a intentar (ADR-041). Desde
 * ADR-045 el texto depende del motivo, porque lo que tiene que hacer el
 * cliente cambia: dejar a alguien habilitado, revisar la dirección con la
 * tienda, nada (si el problema fue del repartidor)... Y si la próxima es la
 * última visita, se lo dice. El comentario del repartidor NO va: es para
 * Rabbit y el comercio.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.infraestructura.Formatos;
import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import java.time.LocalDate;

final class TextosAlDestinatario {

    private TextosAlDestinatario() {
    }

    /**
     * Lo que hace falta para escribir el aviso. nombre, fechaPrometida y
     * motivo pueden ser null. contoComoVisita: si la última falla le contó
     * una visita al cliente; visitas: las fallidas hasta ahora; maximo: las
     * que se intentan antes de devolverlo (IntentosDeEntrega).
     */
    record Datos(String estado, String nombre, String comercio, String codigo, String enlace,
                 LocalDate fechaPrometida, LocalDate hoy, MotivoEntregaFallida motivo, boolean contoComoVisita,
                 int visitas, int maximo) {
    }

    static String asunto(Datos d) {
        String de = " de " + d.comercio();
        return switch (d.estado()) {
            case "PENDIENTE" -> d.motivo() == null ? "Recibimos tu pedido" + de
                    : d.contoComoVisita() ? "No pudimos entregar tu pedido" + de
                    : "Tu pedido" + de + " se demora un poco";
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
            case "PENDIENTE" -> d.motivo() != null ? visitaFallida(d)
                    : d.comercio() + " nos pasó tu pedido y Rabbit lo va a llevar." + cuando(d.fechaPrometida(), d.hoy());
            case "CONFIRMADO" -> "Ya le asignamos quién lo lleva. Te avisamos cuando salga a reparto.";
            case "EN_CAMINO" -> "Tu pedido salió a reparto y llega hoy. En el seguimiento ves por dónde va.";
            case "ENTREGADO" -> "Tu pedido fue entregado. ¡Gracias!";
            case "CANCELADO" -> "El pedido se canceló. Si no lo esperabas, consultá con " + d.comercio() + ".";
            case "DEVUELTO_AL_COMERCIO" -> devuelto(d);
            default -> "Tu pedido cambió de estado.";
        };
        return saludo + texto + "\n\nSeguilo con el código " + d.codigo() + ": " + d.enlace();
    }

    // PENDIENTE después de una visita que falló: qué pasó, qué puede hacer
    // el cliente y, si corresponde, que la próxima es la última.
    private static String visitaFallida(Datos d) {
        MotivoEntregaFallida m = d.motivo();
        if (!d.contoComoVisita()) {
            return "Tuvimos un inconveniente con el reparto y tu pedido no salió como estaba previsto. "
                    + "No tenés que hacer nada: lo lleva otro repartidor y te avisamos cuando salga.";
        }
        String queHacer = switch (m) {
            case CLIENTE_AUSENTE -> "Pasamos a entregar tu pedido y no había nadie. Si no vas a estar, dejá a alguien"
                    + " que pueda recibirlo.";
            case DIRECCION_INCORRECTA -> "Pasamos a entregar tu pedido y no pudimos encontrar la dirección. Comunicate"
                    + " con " + d.comercio() + " para confirmarla o corregirla.";
            case NADIE_HABILITADO -> "Pasamos a entregar tu pedido pero quien estaba no podía recibirlo. Tiene que"
                    + " recibirlo una persona mayor de edad con DNI (y el código de entrega, si te lo pidieron).";
            case DOMICILIO_CERRADO -> "Pasamos a entregar tu pedido y el lugar estaba cerrado. Si es un local o"
                    + " tiene horario, avisale a " + d.comercio() + " en qué horario se puede entregar.";
            case ZONA_PELIGROSA -> "Hoy no pudimos llegar a la dirección de forma segura.";
            default -> "Pasamos a entregar tu pedido y no pudimos.";
        };
        int siguiente = d.visitas() + 1;
        String despues = siguiente >= d.maximo()
                ? " Lo vamos a intentar una vez más: es la última visita. Si tampoco se puede entregar, el pedido"
                        + " vuelve a " + d.comercio() + "."
                : " Lo vamos a intentar de nuevo y te avisamos cuando vuelva a salir.";
        return queHacer + despues;
    }

    private static String devuelto(Datos d) {
        MotivoEntregaFallida m = d.motivo();
        if (m == MotivoEntregaFallida.RECHAZADO) {
            return "Registramos que no recibiste el pedido y se lo devolvimos a " + d.comercio()
                    + ". Por cualquier consulta, comunicate con la tienda.";
        }
        if (m == MotivoEntregaFallida.PAQUETE_DANADO) {
            return "Tu pedido se dañó antes de llegar y preferimos no entregártelo así. Ya le avisamos a "
                    + d.comercio() + ": comunicate con la tienda para coordinar un reemplazo.";
        }
        String veces = d.visitas() > 1 ? " después de " + d.visitas() + " visitas" : "";
        return "No pudimos entregarlo" + veces + (m != null ? " (" + m.getDescripcion().toLowerCase() + ")" : "")
                + " y se lo devolvimos a " + d.comercio() + ". Comunicate con la tienda para coordinar otra entrega.";
    }

    /** " Llega hoy.", " Llega mañana.", " Llega el viernes 17/10." o nada si no hay fecha. */
    static String cuando(LocalDate prometida, LocalDate hoy) {
        if (prometida == null || prometida.isBefore(hoy)) {
            return "";
        }
        return " Llega " + Formatos.diaRelativo(prometida, hoy) + ".";
    }
}
