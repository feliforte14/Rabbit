package com.rabbit.pedidos.negocio;

import java.security.SecureRandom;

/**
 * Código público de seguimiento de un pedido (por ejemplo RB-7KQ2M9XHTA):
 * lo que el comercio le pasa a su cliente para consultar
 * /api/v1/seguimiento/{codigo} sin login.
 *
 * Aleatorio (SecureRandom), no derivado del ID: con IDs secuenciales
 * cualquiera podía recorrer los estados de todos los pedidos. 10
 * caracteres de un alfabeto de 31 (sin 0/O, 1/I/L, que se confunden al
 * dictarlos) dan ~49 bits: imposible de adivinar por fuerza bruta.
 */
final class CodigosDeSeguimiento {

    private static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LARGO = 10;
    private static final SecureRandom AZAR = new SecureRandom();

    private CodigosDeSeguimiento() {
    }

    static String nuevo() {
        StringBuilder codigo = new StringBuilder("RB-");
        for (int i = 0; i < LARGO; i++) {
            codigo.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }
}
