package com.rabbit.infraestructura;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.json.Json;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;
import java.io.StringReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** CON-02: un entero fuera de rango o con decimales no da la vuelta, corta. */
class NumerosEstrictosTest {

    // Deja el parser parado en el valor, como lo recibe el deserializador.
    private static JsonParser en(String valor) {
        JsonParser parser = Json.createParser(new StringReader(valor));
        parser.next();
        return parser;
    }

    private static Long largo(String valor) {
        return new NumerosEstrictos.Largo().deserialize(en(valor), null, Long.class);
    }

    private static Integer entero(String valor) {
        return new NumerosEstrictos.Entero().deserialize(en(valor), null, Integer.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0", "-5", "9223372036854775807", "-9223372036854775808", "3.0"})
    void unLargoDentroDelRangoPasaTalCual(String valor) {
        assertEquals(new java.math.BigDecimal(valor).longValueExact(), largo(valor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-9223372036854775809", "9223372036854775808", "1e30", "2.7", "\"3\"", "true"})
    void unLargoFueraDeRangoConDecimalesONoNumericoCorta(String valor) {
        assertThrows(JsonbException.class, () -> largo(valor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2147483647", "-2147483648"})
    void unEnteroDentroDelRangoPasaTalCual(String valor) {
        assertEquals(Integer.valueOf(valor), entero(valor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "4294967297", "-2147483649", "1.5", "\"2\""})
    void unEnteroFueraDeRangoConDecimalesONoNumericoCorta(String valor) {
        assertThrows(JsonbException.class, () -> entero(valor));
    }
}
