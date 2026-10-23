package com.rabbit.pedidos.negocio;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

/**
 * El código de 4 dígitos que recibe quien compra y le dice al repartidor al recibir el
 * pedido (ADR-055). Sin el código correcto el repartidor no puede marcar la entrega.
 *
 * Es una prueba de que la persona estaba ahí, no un secreto fuerte: son solo 10.000
 * combinaciones. Por eso se limitan los intentos (MAXIMO_INTENTOS) y el código viaja
 * únicamente en el mail al destinatario: no se guarda en los avisos que ve el personal ni
 * se muestra al repartidor ni en el seguimiento público.
 */
public final class CodigoDeEntrega {

    public static final int MAXIMO_INTENTOS = 5;
    private static final int DIGITOS = 4;
    private static final SecureRandom AZAR = new SecureRandom();

    private CodigoDeEntrega() {
    }

    /** Cuatro dígitos, con ceros a la izquierda ("0042"). */
    public static String generar() {
        return String.format("%0" + DIGITOS + "d", AZAR.nextInt(10_000));
    }

    /** ¿Lo ingresado coincide con el código del pedido? Sin espacios de más; compara en tiempo constante. */
    public static boolean coincide(String esperado, String ingresado) {
        if (esperado == null || ingresado == null) {
            return false;
        }
        String limpio = ingresado.strip();
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), limpio.getBytes(StandardCharsets.UTF_8));
    }

    /** ¿Tiene forma de código (exactamente 4 dígitos)? */
    public static boolean tieneFormato(String ingresado) {
        return ingresado != null && ingresado.strip().matches("\\d{" + DIGITOS + "}");
    }

    /**
     * Controla el código ingresado al entregar.
     *
     * @param esperado   el código del pedido; null en un pedido anterior a este cambio (no se pide)
     * @param ingresado  lo que escribió el repartidor
     * @param intentosPrevios cuántas veces se equivocó ya con este pedido
     * @throws CodigoIncorrectoException si falta, no tiene formato o no coincide (y hay que contar el intento)
     * @throws ValidacionException si ya se agotaron los intentos
     */
    public static void exigir(String esperado, String ingresado, int intentosPrevios) {
        if (esperado == null) {
            return;
        }
        if (intentosPrevios >= MAXIMO_INTENTOS) {
            throw new ValidacionException("Se agotaron los intentos con el código de entrega: "
                    + "el pedido lo tiene que entregar un administrador");
        }
        if (ingresado == null || ingresado.isBlank()) {
            throw new ValidacionException("Pedile a quien recibe el código de 4 dígitos que le llegó por mail");
        }
        if (!tieneFormato(ingresado) || !coincide(esperado, ingresado)) {
            int quedan = MAXIMO_INTENTOS - intentosPrevios - 1;
            throw new CodigoIncorrectoException("El código no es correcto"
                    + (quedan > 0 ? " (te quedan " + quedan + " intento" + (quedan == 1 ? "" : "s") + ")"
                                 : ": se agotaron los intentos, lo tiene que entregar un administrador"));
        }
    }
}
