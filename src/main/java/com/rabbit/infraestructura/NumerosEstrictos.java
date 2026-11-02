package com.rabbit.infraestructura;

/**
 * Deserializadores JSON-B para los enteros de la API REST que no aceptan
 * un número que no entra en el tipo.
 *
 * Por defecto JSON-B convierte con longValue()/intValue(): un número fuera
 * de rango no falla, da la vuelta. Con "idItem": -9223372036854775809 el
 * pedido llegaba con Long.MAX_VALUE, que es positivo, y pasaba la validación
 * (CON-02). Lo mismo con un decimal: 2.7 se truncaba a 2.
 *
 * Con estos, un número fuera de rango, con decimales o que no es un número
 * corta con JsonbException, y ProblemaMapper responde 400 en Problem Details.
 *
 * Uso: @JsonbTypeDeserializer(NumerosEstrictos.Largo.class) sobre el campo.
 */

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.math.BigDecimal;

public final class NumerosEstrictos {

    private NumerosEstrictos() {
    }

    /** Para un Long: entero entre Long.MIN_VALUE y Long.MAX_VALUE. */
    public static class Largo implements JsonbDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext contexto, Type tipo) {
            try {
                return numero(parser).longValueExact();
            } catch (ArithmeticException e) {
                throw new JsonbException("Se esperaba un entero sin decimales dentro del rango de un Long", e);
            }
        }
    }

    /** Para un Integer o un int: entero entre Integer.MIN_VALUE e Integer.MAX_VALUE. */
    public static class Entero implements JsonbDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext contexto, Type tipo) {
            try {
                return numero(parser).intValueExact();
            } catch (ArithmeticException e) {
                throw new JsonbException("Se esperaba un entero sin decimales dentro del rango de un Integer", e);
            }
        }
    }

    // El parser ya está parado en el valor. Un texto ("3") o un booleano no
    // son números: getBigDecimal() falla con IllegalStateException.
    private static BigDecimal numero(JsonParser parser) {
        try {
            return parser.getBigDecimal();
        } catch (IllegalStateException | NumberFormatException e) {
            throw new JsonbException("Se esperaba un número", e);
        }
    }
}
