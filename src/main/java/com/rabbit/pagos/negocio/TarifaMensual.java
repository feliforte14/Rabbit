package com.rabbit.pagos.negocio;

/**
 * La tarifa del servicio mensual de Rabbit a cada comercio (ADR-034):
 *
 *   total = abono + porEnvio x pedidos entregados + porKilo x kilos entregados
 *           + almacenamiento (ADR-059): precio por unidad y por día guardada, según el tamaño
 *
 * Los valores salen de system properties de WildFly, así se cambian sin
 * tocar el código (y sin redesplegar: se leen en cada cálculo):
 *   rabbit.tarifa.abono     (por defecto 15000)
 *   rabbit.tarifa.por-envio (por defecto 1200)
 *   rabbit.tarifa.por-kilo  (por defecto 80)
 *   rabbit.tarifa.almacenamiento.pequeno | .mediano | .grande
 *       (por defecto 10, 30 y 90 por unidad y por día; con los tres en 0 el almacenamiento no se cobra)
 *   rabbit.tarifa.almacenamiento.recargo-stock-antiguo
 *       (por defecto 50: porcentaje que se suma a lo que se cobra por una unidad de un producto sin movimiento
 *       hace demasiado; ver PoliticaDeAlmacenamiento en Inventario, que define los días gratis y cuándo es antiguo)
 * Un valor inválido se ignora y se usa el de por defecto.
 */

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Logger;

public record TarifaMensual(BigDecimal abono, BigDecimal porEnvio, BigDecimal porKilo,
                            BigDecimal almacenamientoPequeno, BigDecimal almacenamientoMediano,
                            BigDecimal almacenamientoGrande, BigDecimal recargoStockAntiguo) {

    /** Sin almacenamiento (las tres tarifas de reparto, como antes del ADR-059). */
    public TarifaMensual(BigDecimal abono, BigDecimal porEnvio, BigDecimal porKilo) {
        this(abono, porEnvio, porKilo, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Con almacenamiento y sin recargo por stock antiguo (como antes de los días gratis). */
    public TarifaMensual(BigDecimal abono, BigDecimal porEnvio, BigDecimal porKilo, BigDecimal almacenamientoPequeno,
                         BigDecimal almacenamientoMediano, BigDecimal almacenamientoGrande) {
        this(abono, porEnvio, porKilo, almacenamientoPequeno, almacenamientoMediano, almacenamientoGrande,
                BigDecimal.ZERO);
    }

    private static final Logger LOG = Logger.getLogger(TarifaMensual.class.getName());

    /** La tarifa vigente (de las system properties, o la de por defecto). */
    public static TarifaMensual vigente() {
        return new TarifaMensual(
                leer("rabbit.tarifa.abono", "15000"),
                leer("rabbit.tarifa.por-envio", "1200"),
                leer("rabbit.tarifa.por-kilo", "80"),
                leer("rabbit.tarifa.almacenamiento.pequeno", "10"),
                leer("rabbit.tarifa.almacenamiento.mediano", "30"),
                leer("rabbit.tarifa.almacenamiento.grande", "90"),
                leer("rabbit.tarifa.almacenamiento.recargo-stock-antiguo", "50"));
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

    /**
     * El cargo por almacenamiento: unidades-día de cada categoría por su precio (ADR-059).
     * 100 unidades pequeñas guardadas 30 días son 3.000 unidades-día.
     */
    public BigDecimal cargoAlmacenamiento(long pequeno, long mediano, long grande) {
        return cargoAlmacenamiento(AlmacenamientoDelMes.sinBeneficios(pequeno, mediano, grande));
    }

    /**
     * El cargo por guardar la mercadería: por cada tamaño, tarifa x (unidades-día menos las gratis, más las de
     * stock antiguo con el recargo). Una unidad gratis no se cobra y una de stock antiguo se cobra con el recargo
     * encima (con 50 %, una vez y media).
     */
    public BigDecimal cargoAlmacenamiento(AlmacenamientoDelMes a) {
        return redondear(facturables(almacenamientoPequeno, a.pequeno(), a.gratisPequeno(), a.recargoPequeno())
                .add(facturables(almacenamientoMediano, a.mediano(), a.gratisMediano(), a.recargoMediano()))
                .add(facturables(almacenamientoGrande, a.grande(), a.gratisGrande(), a.recargoGrande())));
    }

    /** Lo que el comercio se ahorró por los días gratis. */
    public BigDecimal ahorroPorDiasGratis(AlmacenamientoDelMes a) {
        return redondear(almacenamientoPequeno.multiply(BigDecimal.valueOf(a.gratisPequeno()))
                .add(almacenamientoMediano.multiply(BigDecimal.valueOf(a.gratisMediano())))
                .add(almacenamientoGrande.multiply(BigDecimal.valueOf(a.gratisGrande()))));
    }

    /** Lo que se cobró de más por el stock antiguo (solo el recargo, sin lo que ya se cobraba por guardarlo). */
    public BigDecimal cargoPorStockAntiguo(AlmacenamientoDelMes a) {
        return redondear(almacenamientoPequeno.multiply(BigDecimal.valueOf(a.recargoPequeno()))
                .add(almacenamientoMediano.multiply(BigDecimal.valueOf(a.recargoMediano())))
                .add(almacenamientoGrande.multiply(BigDecimal.valueOf(a.recargoGrande())))
                .multiply(recargoComoFraccion()));
    }

    // tarifa x (unidades - gratis + recargo x fracción), sin dejar que las gratis o las de recargo superen al total.
    private BigDecimal facturables(BigDecimal tarifa, long unidades, long gratis, long conRecargo) {
        long pagan = Math.max(0, unidades - Math.min(gratis, unidades));
        long conRecargoQuePagan = Math.min(conRecargo, pagan);
        return tarifa.multiply(BigDecimal.valueOf(pagan)
                .add(BigDecimal.valueOf(conRecargoQuePagan).multiply(recargoComoFraccion())));
    }

    private BigDecimal recargoComoFraccion() {
        return recargoStockAntiguo == null ? BigDecimal.ZERO
                : recargoStockAntiguo.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    /** Total con el almacenamiento: abono + envíos + kilos + almacenamiento. */
    public BigDecimal total(int cantidad, BigDecimal kilos, long pequeno, long mediano, long grande) {
        return total(cantidad, kilos).add(cargoAlmacenamiento(pequeno, mediano, grande));
    }

    public BigDecimal total(int cantidad, BigDecimal kilos, AlmacenamientoDelMes almacenamiento) {
        return total(cantidad, kilos).add(cargoAlmacenamiento(almacenamiento));
    }

    /** Los días gratis y los de stock antiguo, para mostrarlos junto a los precios (los define Inventario). */
    public int diasGratis() {
        return com.rabbit.inventario.negocio.PoliticaDeAlmacenamiento.diasGratis();
    }

    public int diasParaRecargo() {
        return com.rabbit.inventario.negocio.PoliticaDeAlmacenamiento.diasParaRecargo();
    }

    /** ¿Se cobra el almacenamiento? Solo si el equipo definió algún precio. */
    public boolean cobraAlmacenamiento() {
        return almacenamientoPequeno.signum() > 0 || almacenamientoMediano.signum() > 0 || almacenamientoGrande.signum() > 0;
    }

    private static BigDecimal redondear(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }
}
