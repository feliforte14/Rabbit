package com.rabbit.pedidos.negocio;

/**
 * Cuántos días puede esperar en el depósito un pedido listo para retirar (ADR-053) antes de volver al
 * comercio. Pasado el plazo, el pedido se devuelve (VencimientoDeRetiros): el stock vuelve al comercio y se
 * anula el cobro, igual que cuando una entrega a domicilio no se puede hacer.
 *
 * Se lee de la system property rabbit.retiro.dias, como las demás tarifas y topes de Rabbit, así se cambia en
 * WildFly sin tocar el código. Por defecto 21 días (decisión del equipo). Un valor que no es un número entero
 * mayor a cero se ignora y se usa el de por defecto.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */
public final class PlazoDeRetiro {

    public static final String PROPIEDAD = "rabbit.retiro.dias";

    /** Los días que se esperan por defecto. */
    public static final int POR_DEFECTO = 21;

    private PlazoDeRetiro() {
    }

    /** Los días de plazo vigentes. Se lee en cada uso, así un cambio de la property rige sin redesplegar. */
    public static int dias() {
        String valor = System.getProperty(PROPIEDAD);
        if (valor == null) {
            return POR_DEFECTO;
        }
        try {
            int dias = Integer.parseInt(valor.trim());
            return dias > 0 ? dias : POR_DEFECTO;
        } catch (NumberFormatException e) {
            return POR_DEFECTO;
        }
    }
}
