package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tiene que decir lo mismo que la expresión regular que reemplazó, y no tardar con una entrada larga. */
class FormatoDeEmailTest {

    private static final Pattern ANTERIOR = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    @ParameterizedTest
    @ValueSource(strings = {"ana@correo.com", "a@b.c", "ana.perez+tag@sub.correo.com.ar", "x@y..z", "a@b.c.d", "ñandú@correo.com"})
    void aceptaLoQueTieneForma(String email) {
        assertTrue(FormatoDeEmail.tieneFormato(email), email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ana", "@correo.com", "ana@", "ana@correo", "ana@.com", "ana@correo.", "ana@@correo.com",
            "ana@correo@x.com", "a b@correo.com", "ana@cor reo.com", "ana@correo.com ", "\tana@correo.com", "ana@c\norreo.com"})
    void rechazaLoQueNoLaTiene(String email) {
        assertFalse(FormatoDeEmail.tieneFormato(email), email);
    }

    @Test
    void sinTextoNoEsUnEmail() {
        assertFalse(FormatoDeEmail.tieneFormato(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@correo.com", "a@b.c", "x@y..z", "@a.b", "a@.b", "a@b.", "a@b", "a b@c.d", "a@@b.c", "a@b.c@d",
            "", "@", "a@", "a@.", "a@..", "a@b.c ", "a b@c.d", "a@ b.c"})
    void dicenLoMismoQueLaExpresionAnterior(String email) {
        assertEquals(ANTERIOR.matcher(email).matches(), FormatoDeEmail.tieneFormato(email), email);
    }

    // Con la expresión anterior, esto tardaba un tiempo cuadrático (200 000 puntos y un «@» al final).
    @Test
    void unaEntradaLargaYMalArmadaSeRechazaAlInstante() {
        String larga = "a@" + ".".repeat(200_000) + "@";
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertFalse(FormatoDeEmail.tieneFormato(larga)));
    }
}
