package com.rabbit.inventario.negocio;

import com.rabbit.inventario.datos.model.CategoriaDeTamano;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/**
 * Cuántas unidades-día de cada categoría ocupó un comercio en un mes (ADR-059): la suma, día por día, de
 * las unidades que tenía guardadas. 100 unidades pequeñas durante 30 días son 3.000 unidades-día.
 * Separada del servicio para probarla sin base ni EJB.
 *
 * Se arma con las fotos diarias que haya:
 *  - un día con foto cuenta esa foto;
 *  - un día SIN foto (por ejemplo, WildFly estuvo apagado a las 23:55) cuenta lo de la foto anterior: se completa
 *    con lo último que se sabía, mismo criterio que la liquidación de completar lo que falte;
 *  - el día de HOY, que todavía no tiene foto, cuenta lo que hay ahora, así la vista previa llega hasta hoy;
 *  - los días futuros no cuentan.
 * Lo que está FUERA_DE_RANGO (no debería existir: no se acepta en consignación) se cuenta como grande, para no
 * dejarlo sin cobrar.
 */
public final class UnidadesDia {

    private UnidadesDia() {
    }

    /**
     * @param fotos    las fotos del mes que existan, por día (cada una: unidades por categoría)
     * @param previa   la última foto anterior al mes (para arrancar si el 1° no tiene), o vacía
     * @param enVivo   lo que hay hoy en el depósito, para el día de hoy si todavía no tiene foto
     * @return unidades-día por categoría (PEQUENO, MEDIANO y GRANDE siempre presentes)
     */
    public static Map<CategoriaDeTamano, Long> sumar(YearMonth mes, LocalDate hoy,
            Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos,
            Map<CategoriaDeTamano, Long> previa,
            Map<CategoriaDeTamano, Long> enVivo) {
        Map<CategoriaDeTamano, Long> total = vacio();
        Map<CategoriaDeTamano, Long> ultima = previa == null ? Map.of() : previa;
        LocalDate ultimoDia = mes.atEndOfMonth().isBefore(hoy) ? mes.atEndOfMonth() : hoy;
        for (LocalDate dia = mes.atDay(1); !dia.isAfter(ultimoDia); dia = dia.plusDays(1)) {
            Map<CategoriaDeTamano, Long> delDia;
            if (fotos.containsKey(dia)) {
                ultima = fotos.get(dia);
                delDia = ultima;
            } else if (dia.equals(hoy)) {
                delDia = enVivo == null ? Map.of() : enVivo;
            } else {
                delDia = ultima;
            }
            delDia.forEach((categoria, unidades) -> total.merge(cobrable(categoria), unidades, Long::sum));
        }
        return total;
    }

    /** FUERA_DE_RANGO se cobra como grande. */
    static CategoriaDeTamano cobrable(CategoriaDeTamano categoria) {
        return categoria == CategoriaDeTamano.FUERA_DE_RANGO ? CategoriaDeTamano.GRANDE : categoria;
    }

    private static Map<CategoriaDeTamano, Long> vacio() {
        Map<CategoriaDeTamano, Long> m = new EnumMap<>(CategoriaDeTamano.class);
        m.put(CategoriaDeTamano.PEQUENO, 0L);
        m.put(CategoriaDeTamano.MEDIANO, 0L);
        m.put(CategoriaDeTamano.GRANDE, 0L);
        return m;
    }
}
