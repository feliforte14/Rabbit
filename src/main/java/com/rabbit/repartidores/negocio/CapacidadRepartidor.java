package com.rabbit.repartidores.negocio;

/**
 * La regla de cuánto puede llevar un repartidor a la vez (ADR-032), en un
 * solo lugar: la usan la asignación (RepartidorService) y el despacho por
 * zona de Ruteo (a través de RepartidorDTO), para que los dos decidan
 * igual.
 *
 *   - Sin capacidad cargada (ni kilos ni bultos): un pedido a la vez, como
 *     antes de que existiera la capacidad.
 *   - Con capacidad: varios pedidos, mientras la suma no pase ninguno de
 *     los dos límites que tenga cargados.
 *   - Un pedido de peso desconocido (null) solo se controla por bultos.
 */

import java.math.BigDecimal;

public final class CapacidadRepartidor {

    private CapacidadRepartidor() {}

    /**
     * Si un pedido entra en lo que le queda libre al repartidor.
     *
     * @param capacidadKg kilos que soporta el vehículo; null = sin límite de kilos
     * @param capacidadBultos bultos que entran; null = sin límite de bultos
     * @param cargaKg kilos que ya lleva (los pedidos de peso desconocido no suman)
     * @param cargaBultos bultos que ya lleva
     * @param pedidosEnCurso pedidos que ya lleva
     * @param pesoKg peso del pedido nuevo; null = desconocido
     * @param bultos bultos del pedido nuevo
     */
    public static boolean entra(BigDecimal capacidadKg, Integer capacidadBultos,
                                BigDecimal cargaKg, int cargaBultos, int pedidosEnCurso,
                                BigDecimal pesoKg, int bultos) {
        if (capacidadKg == null && capacidadBultos == null) {
            return pedidosEnCurso == 0;
        }
        if (capacidadBultos != null && cargaBultos + bultos > capacidadBultos) {
            return false;
        }
        if (capacidadKg != null && pesoKg != null) {
            BigDecimal carga = cargaKg != null ? cargaKg : BigDecimal.ZERO;
            return carga.add(pesoKg).compareTo(capacidadKg) <= 0;
        }
        return true;
    }

    /** Si al repartidor le queda lugar para algo más (un pedido mínimo: 1 bulto, sin peso). */
    public static boolean tieneLugar(BigDecimal capacidadKg, Integer capacidadBultos,
                                     BigDecimal cargaKg, int cargaBultos, int pedidosEnCurso) {
        if (!entra(capacidadKg, capacidadBultos, cargaKg, cargaBultos, pedidosEnCurso, null, 1)) {
            return false;
        }
        return capacidadKg == null || cargaKg == null || cargaKg.compareTo(capacidadKg) < 0;
    }
}
