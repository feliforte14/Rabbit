package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El plazo para retirar un pedido del depósito: 21 días, configurable, y un valor malo no rompe nada. */
class PlazoDeRetiroTest {

    @AfterEach
    void limpiar() {
        System.clearProperty(PlazoDeRetiro.PROPIEDAD);
    }

    @Test
    @DisplayName("Sin configurar son 21 días")
    void porDefecto() {
        assertEquals(21, PlazoDeRetiro.POR_DEFECTO);
        assertEquals(21, PlazoDeRetiro.dias());
    }

    @Test
    @DisplayName("Se cambia con la property rabbit.retiro.dias, también con espacios")
    void configurable() {
        System.setProperty(PlazoDeRetiro.PROPIEDAD, "30");
        assertEquals(30, PlazoDeRetiro.dias());

        System.setProperty(PlazoDeRetiro.PROPIEDAD, " 7 ");
        assertEquals(7, PlazoDeRetiro.dias());
    }

    @Test
    @DisplayName("Un valor que no sirve (texto, cero, negativo, vacío) se ignora y se usa el de siempre")
    void valoresInvalidos() {
        for (String malo : new String[] {"abc", "0", "-3", "", "  ", "2.5", "99999999999"}) {
            System.setProperty(PlazoDeRetiro.PROPIEDAD, malo);
            assertEquals(21, PlazoDeRetiro.dias(), "con '" + malo + "'");
        }
    }
}
