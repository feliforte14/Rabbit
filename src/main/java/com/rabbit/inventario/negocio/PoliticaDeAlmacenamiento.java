package com.rabbit.inventario.negocio;

/**
 * La política de antigüedad del stock para el cobro de almacenamiento: cuántos días son gratis y cuándo un producto
 * pasa a ser "stock antiguo" y se cobra con recargo.
 *
 * - DÍAS GRATIS: un producto que ingresó hace menos de N días no se cobra por guardarlo. Cuenta desde el último
 *   ingreso de mercadería (alta, recepción o corrección hacia arriba).
 * - STOCK ANTIGUO: un producto que lleva N días sin movimiento (sin ingresos ni ventas) se cobra con un recargo. Es
 *   la regla de Mercado Libre Full: el recargo es por lo que no se vende. El porcentaje es parte de la tarifa
 *   (ver TarifaMensual).
 *
 * Es por producto y no por lote: si un producto recibe mercadería nueva, todas sus unidades cuentan como recién
 * ingresadas. Hacerlo exacto unidad por unidad exige llevar cada lote, que hoy no se justifica.
 *
 * Se configura con system properties, así el equipo cambia los valores sin tocar el código:
 *   rabbit.almacenamiento.dias-gratis    por defecto 7 (0 = sin días gratis)
 *   rabbit.almacenamiento.dias-recargo   por defecto 120 (0 = sin recargo por stock antiguo)
 * Un valor que no es un número entero mayor o igual a cero se ignora y se usa el de por defecto.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.inventario.datos.model.ItemInventario;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class PoliticaDeAlmacenamiento {

    public static final String PROPIEDAD_DIAS_GRATIS = "rabbit.almacenamiento.dias-gratis";
    public static final String PROPIEDAD_DIAS_RECARGO = "rabbit.almacenamiento.dias-recargo";

    public static final int DIAS_GRATIS_POR_DEFECTO = 7;
    public static final int DIAS_RECARGO_POR_DEFECTO = 120;

    private PoliticaDeAlmacenamiento() {
    }

    /** Los días gratis vigentes. Se lee en cada uso: un cambio de la property rige sin redesplegar. */
    public static int diasGratis() {
        return leer(PROPIEDAD_DIAS_GRATIS, DIAS_GRATIS_POR_DEFECTO);
    }

    /** Los días sin movimiento a partir de los cuales hay recargo. */
    public static int diasParaRecargo() {
        return leer(PROPIEDAD_DIAS_RECARGO, DIAS_RECARGO_POR_DEFECTO);
    }

    /** Si el producto está dentro de sus días gratis ese día. Sin fecha de ingreso no hay días gratis. */
    public static boolean esGratis(ItemInventario item, LocalDate dia) {
        int dias = diasGratis();
        LocalDate ingreso = item.getFechaUltimoIngreso();
        if (dias <= 0 || ingreso == null) {
            return false;
        }
        long transcurridos = ChronoUnit.DAYS.between(ingreso, dia);
        return transcurridos >= 0 && transcurridos < dias;
    }

    /** Si el producto lleva demasiado sin moverse ese día (y no está en sus días gratis). */
    public static boolean esAntiguo(ItemInventario item, LocalDate dia) {
        int dias = diasParaRecargo();
        LocalDate movimiento = item.getFechaUltimoMovimiento();
        if (dias <= 0 || movimiento == null || esGratis(item, dia)) {
            return false;
        }
        return ChronoUnit.DAYS.between(movimiento, dia) >= dias;
    }

    private static int leer(String propiedad, int porDefecto) {
        String valor = System.getProperty(propiedad);
        if (valor == null) {
            return porDefecto;
        }
        try {
            int dias = Integer.parseInt(valor.trim());
            return dias >= 0 ? dias : porDefecto;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
