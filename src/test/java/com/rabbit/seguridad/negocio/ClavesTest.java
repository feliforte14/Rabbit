package com.rabbit.seguridad.negocio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClavesTest {

    @Test
    void elHashTieneElFormatoQueLeeElRealmDeWildFly() {
        String hash = Claves.hashear("secreta1");
        assertTrue(hash.startsWith("$2a$10$"), hash);
        assertTrue(hash.length() == 60, hash);
    }

    @Test
    void laMismaContrasenaDaHashesDistintos() {
        assertNotEquals(Claves.hashear("secreta1"), Claves.hashear("secreta1"));
    }

    @Test
    void soloCoincideConLaContrasenaCorrecta() {
        String hash = Claves.hashear("secreta1");
        assertTrue(Claves.coincide("secreta1", hash));
        assertFalse(Claves.coincide("secreta2", hash));
        assertFalse(Claves.coincide("secreta1", null));
    }
}
