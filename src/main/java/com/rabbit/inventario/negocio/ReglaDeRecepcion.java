package com.rabbit.inventario.negocio;

import com.rabbit.inventario.datos.model.EstadoOrdenDeRecepcion;
import com.rabbit.inventario.dto.DatosLineaRecepcionDTO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de las órdenes de recepción (ADR-058), separadas del servicio para probarlas sin base ni EJB.
 *
 * ANUNCIO (lo hace el comercio): de 1 a {@value #MAXIMO_LINEAS} productos, sin repetir; cantidad de 1
 * a {@value #CANTIDAD_MAXIMA}; peso de una unidad como en el alta de stock (hace falta si el producto
 * todavía no existe en el depósito).
 *
 * RECEPCIÓN (la hace el personal): se informa lo que llegó de CADA línea. Queda RECIBIDA si no faltó
 * nada (llegar de más no la vuelve parcial: se anota como diferencia) y PARCIAL si en alguna línea llegó
 * menos de lo anunciado. No se registra una recepción en la que no llegó nada: eso es cancelar.
 */
public final class ReglaDeRecepcion {

    public static final int MAXIMO_LINEAS = 50;
    public static final int CANTIDAD_MAXIMA = 100_000;
    public static final int NOTAS_MAXIMO = 200;
    public static final int PRODUCTO_MAXIMO = 100;
    private static final BigDecimal PESO_MAXIMO_KG = new BigDecimal("1000");

    private ReglaDeRecepcion() {
    }

    /** Cómo quedó la orden al registrar la recepción. */
    public record Resultado(EstadoOrdenDeRecepcion estado, int totalEsperado, int totalRecibido) {
    }

    /**
     * El anuncio con los nombres sin espacios de más.
     *
     * @throws ValidacionException si no hay productos, hay de más, repetidos o con datos inválidos
     */
    public static List<DatosLineaRecepcionDTO> validarAnuncio(List<DatosLineaRecepcionDTO> lineas) {
        List<DatosLineaRecepcionDTO> limpias = new ArrayList<>();
        if (lineas != null) {
            for (DatosLineaRecepcionDTO l : lineas) {
                // Una fila del formulario sin nada escrito no es un error: se ignora.
                if (l != null && (l.producto == null || l.producto.isBlank()) && l.cantidad == 0 && l.pesoKg == null) {
                    continue;
                }
                limpias.add(l);
            }
        }
        if (limpias.isEmpty()) {
            throw new ValidacionException("Agregá al menos un producto a la orden");
        }
        if (limpias.size() > MAXIMO_LINEAS) {
            throw new ValidacionException("Una orden admite hasta " + MAXIMO_LINEAS + " productos");
        }
        Set<String> vistos = new HashSet<>();
        List<DatosLineaRecepcionDTO> resultado = new ArrayList<>();
        for (DatosLineaRecepcionDTO l : limpias) {
            if (l == null || l.producto == null || l.producto.isBlank()) {
                throw new ValidacionException("Cada línea necesita el nombre del producto");
            }
            String producto = l.producto.strip();
            if (producto.length() > PRODUCTO_MAXIMO) {
                throw new ValidacionException("El nombre del producto puede tener hasta " + PRODUCTO_MAXIMO + " caracteres");
            }
            if (l.cantidad < 1 || l.cantidad > CANTIDAD_MAXIMA) {
                throw new ValidacionException("La cantidad de \"" + producto + "\" tiene que estar entre 1 y " + CANTIDAD_MAXIMA);
            }
            validarPeso(producto, l.pesoKg);
            if (!vistos.add(producto.toLowerCase(Locale.ROOT))) {
                throw new ValidacionException("\"" + producto + "\" está repetido: sumá las cantidades en una sola línea");
            }
            resultado.add(new DatosLineaRecepcionDTO(producto, l.cantidad, l.pesoKg));
        }
        return resultado;
    }

    private static void validarPeso(String producto, BigDecimal pesoKg) {
        if (pesoKg == null) {
            throw new ValidacionException("Ingresá el peso por unidad de \"" + producto + "\", en kilos");
        }
        if (pesoKg.signum() <= 0 || pesoKg.compareTo(PESO_MAXIMO_KG) > 0) {
            throw new ValidacionException("El peso por unidad de \"" + producto + "\" tiene que ser mayor a 0 y de hasta "
                    + PESO_MAXIMO_KG + " kg");
        }
        if (pesoKg.stripTrailingZeros().scale() > 3) {
            throw new ValidacionException("El peso admite hasta 3 decimales (gramos)");
        }
    }

    /** Las notas sin caracteres de control ni espacios de más; null si quedan vacías. */
    public static String limpiarNotas(String notas) {
        if (notas == null) {
            return null;
        }
        String limpio = notas.replaceAll("\\p{Cntrl}", " ").replaceAll(" {2,}", " ").strip();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > NOTAS_MAXIMO) {
            throw new ValidacionException("Las notas pueden tener hasta " + NOTAS_MAXIMO + " caracteres");
        }
        return limpio;
    }

    /**
     * Valida lo recibido y dice en qué estado queda la orden.
     *
     * @param esperadas lo anunciado de cada línea, en orden
     * @param recibidas lo que llegó de cada línea, en el mismo orden (null = no se informó)
     * @throws ValidacionException si falta informar una línea, hay negativos o pasados de rango, o no llegó nada
     */
    public static Resultado resolver(List<Integer> esperadas, List<Integer> recibidas) {
        if (recibidas == null || recibidas.size() != esperadas.size()) {
            throw new ValidacionException("Informá lo que llegó de cada producto de la orden");
        }
        int totalEsperado = 0;
        int totalRecibido = 0;
        boolean faltoAlgo = false;
        for (int i = 0; i < esperadas.size(); i++) {
            Integer recibida = recibidas.get(i);
            if (recibida == null) {
                throw new ValidacionException("Informá lo que llegó de cada producto (si no llegó, poné 0)");
            }
            if (recibida < 0 || recibida > CANTIDAD_MAXIMA) {
                throw new ValidacionException("Lo recibido tiene que estar entre 0 y " + CANTIDAD_MAXIMA);
            }
            totalEsperado += esperadas.get(i);
            totalRecibido += recibida;
            faltoAlgo |= recibida < esperadas.get(i);
        }
        if (totalRecibido == 0) {
            throw new ValidacionException("No llegó nada: si la mercadería no va a llegar, cancelá la orden");
        }
        return new Resultado(faltoAlgo ? EstadoOrdenDeRecepcion.PARCIAL : EstadoOrdenDeRecepcion.RECIBIDA,
                totalEsperado, totalRecibido);
    }
}
