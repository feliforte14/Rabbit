package com.rabbit.pedidos.presentacion.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pagos.dto.MedioPago;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Las reglas de formato del cuerpo del alta (el 400 con errores por campo). */
class PedidoExternoRequestTest {

    private static Validator validador;

    @BeforeAll
    static void preparar() {
        validador = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static PedidoExternoRequest valido() {
        PedidoExternoRequest r = new PedidoExternoRequest();
        PedidoExternoRequest.Linea linea = new PedidoExternoRequest.Linea();
        linea.idItem = 3L;
        linea.cantidad = 2;
        r.lineas.add(linea);
        r.importe = new BigDecimal("2500");
        r.medioPago = MedioPago.PREPAGO;
        r.direccionEntrega = "Av. Corrientes 1234, CABA";
        return r;
    }

    private static Set<String> camposConError(PedidoExternoRequest r) {
        return validador.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void unPedidoCompletoEsValido() {
        assertTrue(validador.validate(valido()).isEmpty());
    }

    @Test
    void importeEnCeroYSinDireccion() {
        PedidoExternoRequest r = valido();
        r.importe = BigDecimal.ZERO;
        r.direccionEntrega = " ";
        assertEquals(Set.of("importe", "direccionEntrega"), camposConError(r));
    }

    @Test
    void sinLineasNiMedioDePago() {
        PedidoExternoRequest r = valido();
        r.lineas.clear();
        r.medioPago = null;
        assertEquals(Set.of("lineas", "medioPago"), camposConError(r));
    }

    @Test
    void lineaConCantidadCero() {
        PedidoExternoRequest r = valido();
        r.lineas.get(0).cantidad = 0;
        Set<ConstraintViolation<PedidoExternoRequest>> errores = validador.validate(r);
        assertEquals(1, errores.size());
        assertTrue(errores.iterator().next().getPropertyPath().toString().contains("cantidad"));
        assertEquals("La cantidad debe ser mayor a cero", errores.iterator().next().getMessage());
    }
}
