package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodigosDeSeguimientoTest {

    @Test
    void tieneElFormatoPublicoSinCaracteresAmbiguos() {
        for (int i = 0; i < 1000; i++) {
            String codigo = CodigosDeSeguimiento.nuevo();
            // RB- + 10 caracteres, sin 0/O ni 1/I/L (se confunden al dictarlos).
            assertTrue(codigo.matches("RB-[2-9A-HJKMNP-Z]{10}"), codigo);
        }
    }

    @Test
    void noSeRepiten() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(vistos.add(CodigosDeSeguimiento.nuevo()));
        }
    }
}
