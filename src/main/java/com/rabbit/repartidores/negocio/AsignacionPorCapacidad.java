package com.rabbit.repartidores.negocio;

/**
 * A qué repartidor se le da un pedido (ADR-032, ADR-037): se recorren los
 * candidatos en orden (los de la zona del pedido primero) y gana el
 * primero al que le entra. Si el primero no tiene lugar, se pasa al
 * siguiente: el pedido nunca queda esperando a un repartidor puntual.
 *
 * El repartidor que devolvió el pedido al depósito no vuelve a ser
 * candidato para ese pedido.
 *
 * Separado de RepartidorService para poder probarlo sin contenedor.
 */

import com.rabbit.repartidores.dto.RepartidorDTO;
import java.math.BigDecimal;
import java.util.List;

public final class AsignacionPorCapacidad {

    private AsignacionPorCapacidad() {
    }

    /**
     * @param candidatos repartidores en orden de preferencia, con lo que ya llevan
     * @param idExcluido repartidor que no puede llevar este pedido; null = ninguno
     * @return el elegido, o null si no le entra a ninguno
     */
    public static RepartidorDTO elegir(List<RepartidorDTO> candidatos, Long idExcluido,
                                       BigDecimal pesoKg, int bultos) {
        for (RepartidorDTO candidato : candidatos) {
            if (candidato.getId().equals(idExcluido)) {
                continue;
            }
            if (candidato.entra(pesoKg, bultos)) {
                return candidato;
            }
        }
        return null;
    }
}
