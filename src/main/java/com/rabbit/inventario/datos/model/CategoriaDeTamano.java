package com.rabbit.inventario.datos.model;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Categoría de tamaño de un producto en el depósito (ADR-059): de ella depende cuánto se le cobra al
 * comercio por guardarlo (por unidad y por día). NO se carga a mano: se deduce del peso y de las medidas.
 *
 * Un producto entra en la categoría MÁS GRANDE que le toque por cualquiera de sus datos: un almohadón liviano
 * pero voluminoso es mediano aunque pese 300 g. Los límites son "hasta" (inclusivos) y salen de system
 * properties de WildFly, igual que la tarifa (se leen en cada cálculo; un valor inválido se ignora):
 *
 *   rabbit.almacenamiento.{pequeno|mediano|grande}.peso-kg
 *   rabbit.almacenamiento.{pequeno|mediano|grande}.lado-cm       (el lado mayor)
 *   rabbit.almacenamiento.{pequeno|mediano|grande}.volumen-litros (alto x ancho x largo)
 *
 * Por defecto (los de la propuesta, a confirmar por el equipo): pequeño hasta 1 kg, 25 cm y 3 L; mediano hasta
 * 10 kg, 60 cm y 50 L; grande hasta 50 kg, 150 cm y 500 L. Más que eso es FUERA_DE_RANGO: no se acepta en consignación.
 *
 * Un producto sin medidas se cuenta como MEDIANO (o la que le toque por peso, si es mayor), así no sale
 * gratis por un dato faltante.
 */
public enum CategoriaDeTamano {
    PEQUENO("Pequeño"), MEDIANO("Mediano"), GRANDE("Grande"), FUERA_DE_RANGO("Fuera de rango");

    private static final Logger LOG = Logger.getLogger(CategoriaDeTamano.class.getName());

    private final String etiqueta;

    CategoriaDeTamano(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Lo máximo que admite una categoría; volumenCm3 en centímetros cúbicos (1 litro = 1000 cm³). */
    public record Limites(BigDecimal pesoKg, BigDecimal ladoCm, BigDecimal volumenCm3) {
    }

    /** Los límites vigentes de PEQUENO, MEDIANO y GRANDE (de las system properties, o los de por defecto). */
    public static Map<CategoriaDeTamano, Limites> limitesVigentes() {
        Map<CategoriaDeTamano, Limites> limites = new EnumMap<>(CategoriaDeTamano.class);
        limites.put(PEQUENO, leer("pequeno", "1", "25", "3"));
        limites.put(MEDIANO, leer("mediano", "10", "60", "50"));
        limites.put(GRANDE, leer("grande", "50", "150", "500"));
        return limites;
    }

    private static Limites leer(String categoria, String peso, String lado, String litros) {
        String base = "rabbit.almacenamiento." + categoria;
        return new Limites(numero(base + ".peso-kg", peso), numero(base + ".lado-cm", lado),
                numero(base + ".volumen-litros", litros).multiply(BigDecimal.valueOf(1000)));
    }

    private static BigDecimal numero(String propiedad, String porDefecto) {
        String valor = System.getProperty(propiedad);
        if (valor != null) {
            try {
                BigDecimal n = new BigDecimal(valor.trim());
                if (n.signum() > 0) {
                    return n;
                }
            } catch (NumberFormatException e) {
                // Se avisa abajo y se usa el de por defecto.
            }
            LOG.warning("[Inventario] " + propiedad + "=" + valor + " no es un número válido: se usa " + porDefecto);
        }
        return new BigDecimal(porDefecto);
    }

    /** La categoría de un producto, con los límites vigentes. Cualquier dato puede ser null. */
    public static CategoriaDeTamano de(BigDecimal pesoKg, BigDecimal altoCm, BigDecimal anchoCm, BigDecimal largoCm) {
        return de(pesoKg, altoCm, anchoCm, largoCm, limitesVigentes());
    }

    /**
     * @param pesoKg peso de UNA unidad (null = no se conoce)
     * @param altoCm alto, ancho y largo de UNA unidad; si falta alguno, se considera que no tiene medidas
     */
    public static CategoriaDeTamano de(BigDecimal pesoKg, BigDecimal altoCm, BigDecimal anchoCm, BigDecimal largoCm,
                                       Map<CategoriaDeTamano, Limites> limites) {
        CategoriaDeTamano porPeso = pesoKg == null ? PEQUENO : primeraQueAdmite(limites, l -> pesoKg.compareTo(l.pesoKg()) <= 0);
        if (!tieneMedidas(altoCm, anchoCm, largoCm)) {
            // Sin medidas: mediano como mínimo, y más si el peso lo pide.
            return mayor(MEDIANO, porPeso);
        }
        BigDecimal lado = altoCm.max(anchoCm).max(largoCm);
        BigDecimal volumen = altoCm.multiply(anchoCm).multiply(largoCm);
        CategoriaDeTamano porLado = primeraQueAdmite(limites, l -> lado.compareTo(l.ladoCm()) <= 0);
        CategoriaDeTamano porVolumen = primeraQueAdmite(limites, l -> volumen.compareTo(l.volumenCm3()) <= 0);
        return mayor(mayor(porPeso, porLado), porVolumen);
    }

    /** ¿Tiene las tres medidas cargadas? */
    public static boolean tieneMedidas(BigDecimal altoCm, BigDecimal anchoCm, BigDecimal largoCm) {
        return altoCm != null && anchoCm != null && largoCm != null;
    }

    private static CategoriaDeTamano primeraQueAdmite(Map<CategoriaDeTamano, Limites> limites,
                                                      java.util.function.Predicate<Limites> entra) {
        for (CategoriaDeTamano c : new CategoriaDeTamano[] {PEQUENO, MEDIANO, GRANDE}) {
            if (entra.test(limites.get(c))) {
                return c;
            }
        }
        return FUERA_DE_RANGO;
    }

    private static CategoriaDeTamano mayor(CategoriaDeTamano a, CategoriaDeTamano b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
