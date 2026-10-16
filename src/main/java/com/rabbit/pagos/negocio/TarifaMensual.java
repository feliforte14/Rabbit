package com.rabbit.pagos.negocio;

/**
 * La tarifa del servicio mensual de Rabbit a cada comercio (ADR-034):
 *
 *   total = abono + porEnvio x pedidos entregados + porKilo x kilos entregados
 *
 * Los valores salen de system properties de WildFly, así se cambian sin
 * tocar el código (y sin redesplegar: se leen en cada cálculo):
 *   rabbit.tarifa.abono     (por defecto 15000)
 *   rabbit.tarifa.por-envio (por defecto 1200)
 *   rabbit.tarifa.por-kilo  (por defecto 80)
 * Un valor inválido se ignora y se usa el de por defecto.
 */

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Logger;

public record TarifaMensual(BigDecimal abono, BigDecimal porEnvio, BigDecimal porKilo) {

    private static final Logger LOG = Logger.getLogger(TarifaMensual.class.getName());

    /** La tarifa vigente (de las system properties, o la de por defecto). */
    public static TarifaMensual vigente() {
        return new TarifaMensual(
                leer("rabbit.tarifa.abono", "15000"),
                leer("rabbit.tarifa.por-envio", "1200"),
                leer("rabbit.tarifa.por-kilo", "80"));
    }

    private static BigDecimal leer(String propiedad, String porDefecto) {
        String valor = System.getProperty(propiedad);
        if (valor != null) {
            try {
                BigDecimal numero = new BigDecimal(valor.trim());
                if (numero.signum() >= 0) {
                    return numero;
                }
            } catch (NumberFormatException e) {
                // Se avisa abajo y se usa el de por defecto.
            }
            LOG.warning("[Pagos] " + propiedad + "=" + valor + " no es un importe válido: se usa " + porDefecto);
        }
        return new BigDecimal(porDefecto);
    }

    public BigDecimal abonoMensual() {
        return redondear(abono);
    }

    public BigDecimal cargoEnvios(int cantidad) {
        return redondear(porEnvio.multiply(BigDecimal.valueOf(cantidad)));
    }

    public BigDecimal cargoKilos(BigDecimal kilos) {
        return redondear(porKilo.multiply(kilos != null ? kilos : BigDecimal.ZERO));
    }

    public BigDecimal total(int cantidad, BigDecimal kilos) {
        return abonoMensual().add(cargoEnvios(cantidad)).add(cargoKilos(kilos));
    }

    private static BigDecimal redondear(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }
}
