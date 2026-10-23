package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** ADR-055: el código de 4 dígitos con el que se entrega un pedido. */
class CodigoDeEntregaTest {

    @Test
    void generaCuatroDigitosSiempre() {
        for (int i = 0; i < 2000; i++) {
            String codigo = CodigoDeEntrega.generar();
            assertTrue(codigo.matches("\\d{4}"), "no tiene 4 dígitos: " + codigo);
        }
    }

    @Test
    void coincideSinImportarEspaciosDeMas() {
        assertTrue(CodigoDeEntrega.coincide("0427", "0427"));
        assertTrue(CodigoDeEntrega.coincide("0427", " 0427 "));
        assertFalse(CodigoDeEntrega.coincide("0427", "427"), "el cero inicial importa");
        assertFalse(CodigoDeEntrega.coincide("0427", "0428"));
        assertFalse(CodigoDeEntrega.coincide("0427", null));
        assertFalse(CodigoDeEntrega.coincide(null, "0427"));
    }

    @Test
    void sinCodigoEnElPedidoNoSePide() {
        assertDoesNotThrow(() -> CodigoDeEntrega.exigir(null, null, 0));
        assertDoesNotThrow(() -> CodigoDeEntrega.exigir(null, "9999", 0));
    }

    @Test
    void conElCodigoCorrectoDeja() {
        assertDoesNotThrow(() -> CodigoDeEntrega.exigir("0427", "0427", 0));
        assertDoesNotThrow(() -> CodigoDeEntrega.exigir("0427", "0427", CodigoDeEntrega.MAXIMO_INTENTOS - 1));
    }

    @Test
    void faltaElCodigoNoCuentaComoIntento() {
        for (String vacio : new String[] {null, "", "   "}) {
            ValidacionException e = assertThrows(ValidacionException.class, () -> CodigoDeEntrega.exigir("0427", vacio, 0));
            assertFalse(e instanceof CodigoIncorrectoException, "pedir el código no es equivocarse");
        }
    }

    @Test
    void unCodigoIncorrectoSeRechazaYSeCuenta() {
        for (String mal : new String[] {"0428", "abcd", "12345", "42"}) {
            assertThrows(CodigoIncorrectoException.class, () -> CodigoDeEntrega.exigir("0427", mal, 0), mal);
        }
    }

    @Test
    void elMensajeDiceCuantosIntentosQuedan() {
        CodigoIncorrectoException primero = assertThrows(CodigoIncorrectoException.class,
                () -> CodigoDeEntrega.exigir("0427", "0000", 0));
        assertTrue(primero.getMessage().contains("te quedan 4 intentos"));
        CodigoIncorrectoException anteultimo = assertThrows(CodigoIncorrectoException.class,
                () -> CodigoDeEntrega.exigir("0427", "0000", 3));
        assertTrue(anteultimo.getMessage().contains("te quedan 1 intento)"));
        CodigoIncorrectoException ultimo = assertThrows(CodigoIncorrectoException.class,
                () -> CodigoDeEntrega.exigir("0427", "0000", 4));
        assertTrue(ultimo.getMessage().contains("se agotaron los intentos"));
    }

    @Test
    void agotadosLosIntentosNoDejaNiConElCodigoCorrecto() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> CodigoDeEntrega.exigir("0427", "0427", CodigoDeEntrega.MAXIMO_INTENTOS));
        assertTrue(e.getMessage().contains("administrador"));
        assertFalse(e instanceof CodigoIncorrectoException);
    }

    @Test
    void elCodigoIncorrectoNoDeshaceLaTransaccion() {
        jakarta.ejb.ApplicationException anotacion = CodigoIncorrectoException.class
                .getAnnotation(jakarta.ejb.ApplicationException.class);
        assertNotNull(anotacion);
        assertFalse(anotacion.rollback(), "si deshiciera la transacción, el intento no quedaría contado");
        assertTrue(ValidacionException.class.getAnnotation(jakarta.ejb.ApplicationException.class).rollback());
    }
}
