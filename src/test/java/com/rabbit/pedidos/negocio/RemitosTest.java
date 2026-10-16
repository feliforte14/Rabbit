package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RemitosTest {

    private static byte[] bytes(int... valores) {
        byte[] b = new byte[valores.length + 20];
        for (int i = 0; i < valores.length; i++) {
            b[i] = (byte) valores[i];
        }
        return b;
    }

    @Test
    void reconoceLasImagenesPorSusPrimerosBytes() {
        assertEquals("image/jpeg", Remitos.tipoDeImagen(bytes(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals("image/png", Remitos.tipoDeImagen(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)));
        assertEquals("image/webp", Remitos.tipoDeImagen(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P')));
    }

    @Test
    void rechazaLoQueNoEsUnaImagenAunqueSeLlameJpg() {
        assertThrows(ValidacionException.class,
                () -> Remitos.tipoDeImagen("<html><script>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ValidacionException.class, () -> Remitos.tipoDeImagen(bytes('%', 'P', 'D', 'F')));
    }

    @Test
    void exigeLaFotoYUnTamanoMaximo() {
        assertThrows(ValidacionException.class, () -> Remitos.tipoDeImagen(null));
        assertThrows(ValidacionException.class, () -> Remitos.tipoDeImagen(new byte[0]));
        byte[] enorme = new byte[Remitos.TAMANO_MAXIMO + 1];
        enorme[0] = (byte) 0xFF;
        enorme[1] = (byte) 0xD8;
        enorme[2] = (byte) 0xFF;
        assertThrows(ValidacionException.class, () -> Remitos.tipoDeImagen(enorme));
    }
}
