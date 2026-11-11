package com.rabbit.pedidos.negocio;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.TipoEntrega;
import java.time.LocalDate;

/**
 * Qué puede cambiar quien recibe el pedido desde el seguimiento público (ADR-057), y con qué
 * datos. Separada del servicio para probarla sin base ni EJB.
 *
 *  - Reprogramar: elegir otro día para recibirlo, desde el que ya se había prometido y hasta
 *    {@value #DIAS_MAXIMOS} días desde hoy. No se puede pedir antes de lo prometido: eso no
 *    es reprogramar y Rabbit no lo puede garantizar.
 *  - Instrucciones ("dejar en portería", "tocar timbre 3B"): texto libre de un tercero, corto.
 *  - Autorizar a otra persona a recibirlo: solo un nombre. NO reemplaza el código de entrega
 *    (ADR-055): la persona autorizada tiene que conocerlo para recibir el pedido.
 *
 * Solo mientras el pedido no salió a reparto: ya en camino, o resuelto, no se cambia nada.
 * Como la escritura es pública (la autoriza el código de seguimiento), hay un tope de cambios.
 */
public final class ReglaDeOpcionesDeEntrega {

    public static final int DIAS_MAXIMOS = 7;
    public static final int INSTRUCCIONES_MAXIMO = 200;
    public static final int NOMBRE_MAXIMO = 100;
    public static final int CAMBIOS_MAXIMOS = 5;

    private ReglaDeOpcionesDeEntrega() {
    }

    /** Lo que quedó después de validar y limpiar. fecha es null si no se pidió reprogramar. */
    public record Cambios(LocalDate fecha, String instrucciones, String nombreAutorizado) {
    }

    /**
     * ¿Todavía se puede cambiar algo? Una cuenta a domicilio, pendiente, o confirmada con un
     * repartidor de Rabbit (un confirmado sin repartidor lo lleva una empresa de logística, a la que
     * Rabbit no le pasa estos datos).
     */
    public static boolean sePuedeModificar(EstadoPedido estado, TipoEntrega tipo, Long idRepartidor) {
        if (tipo == TipoEntrega.RETIRO_EN_DEPOSITO) {
            return false;
        }
        return estado == EstadoPedido.PENDIENTE || (estado == EstadoPedido.CONFIRMADO && idRepartidor != null);
    }

    /** @throws ValidacionException con el motivo si no se puede modificar */
    public static void exigirModificable(EstadoPedido estado, TipoEntrega tipo, Long idRepartidor, int cambiosHechos) {
        if (tipo == TipoEntrega.RETIRO_EN_DEPOSITO) {
            throw new ValidacionException("Este pedido lo retirás en el depósito: no hay entrega que reprogramar");
        }
        if (estado == EstadoPedido.EN_CAMINO) {
            throw new ValidacionException("El pedido ya salió a reparto: no se puede cambiar. "
                    + "Si necesitás algo, comunicate con la tienda.");
        }
        if (estado == EstadoPedido.CONFIRMADO && idRepartidor == null) {
            throw new ValidacionException("Este pedido lo lleva una empresa de logística: "
                    + "para cambiarlo, comunicate con la tienda");
        }
        if (!sePuedeModificar(estado, tipo, idRepartidor)) {
            throw new ValidacionException("El pedido está " + estado + ": ya no se puede cambiar la entrega");
        }
        if (cambiosHechos >= CAMBIOS_MAXIMOS) {
            throw new ValidacionException("Ya hiciste el máximo de cambios (" + CAMBIOS_MAXIMOS
                    + ") en este pedido. Comunicate con la tienda.");
        }
    }

    /**
     * El día elegido para recibirlo, o null si no se pidió ninguno.
     *
     * @param prometida el día prometido hoy (null si nunca se prometió uno)
     * @throws ValidacionException si es anterior a hoy o a lo prometido, o pasa de {@value #DIAS_MAXIMOS} días
     */
    public static LocalDate validarFecha(LocalDate hoy, LocalDate prometida, LocalDate elegida) {
        if (elegida == null) {
            return null;
        }
        LocalDate desde = prometida != null && prometida.isAfter(hoy) ? prometida : hoy;
        if (elegida.isBefore(desde)) {
            throw new ValidacionException(prometida != null && prometida.isAfter(hoy)
                    ? "No podemos adelantar la entrega: elegí el día prometido o uno posterior"
                    : "Elegí hoy o un día posterior");
        }
        if (elegida.isAfter(hoy.plusDays(DIAS_MAXIMOS))) {
            throw new ValidacionException("Se puede reprogramar hasta " + DIAS_MAXIMOS + " días desde hoy");
        }
        return elegida;
    }

    /** Instrucciones sin caracteres de control y sin espacios de más; null si quedan vacías. */
    public static String limpiarInstrucciones(String texto) {
        String limpio = limpiar(texto, true);
        if (limpio != null && limpio.length() > INSTRUCCIONES_MAXIMO) {
            throw new ValidacionException("Las instrucciones pueden tener hasta " + INSTRUCCIONES_MAXIMO + " caracteres");
        }
        return limpio;
    }

    /** Nombre de la persona autorizada, en una sola línea; null si queda vacío. */
    public static String limpiarNombre(String texto) {
        String limpio = limpiar(texto, false);
        if (limpio != null && limpio.length() > NOMBRE_MAXIMO) {
            throw new ValidacionException("El nombre puede tener hasta " + NOMBRE_MAXIMO + " caracteres");
        }
        return limpio;
    }

    /** Junta las tres opciones ya validadas. */
    public static Cambios validar(LocalDate hoy, LocalDate prometida, LocalDate fecha, String instrucciones, String nombre) {
        return new Cambios(validarFecha(hoy, prometida, fecha), limpiarInstrucciones(instrucciones), limpiarNombre(nombre));
    }

    // Texto de un tercero: sin caracteres de control (incluido el nulo); los saltos de línea solo si se piden.
    private static String limpiar(String texto, boolean conSaltos) {
        if (texto == null) {
            return null;
        }
        String base = texto.replace("\r\n", "\n").replace('\r', '\n');
        String sinControl = conSaltos
                ? base.replaceAll("[\\p{Cntrl}&&[^\\n]]", "")
                : base.replaceAll("\\p{Cntrl}", " ").replaceAll(" {2,}", " ");
        String limpio = sinControl.strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
