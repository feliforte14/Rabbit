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
        r.emailDestinatario = "cliente@correo.com";
        return r;
    }

    @Test
    void sinEmailDeQuienRecibeEs400ConElCampo() {
        PedidoExternoRequest r = valido();
        r.emailDestinatario = null;
        assertEquals(Set.of("emailDestinatario"), camposConError(r));
        r.emailDestinatario = "   ";
        r.normalizar();
        assertEquals(Set.of("emailDestinatario"), camposConError(r));
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

    @Test
    void elItemTieneQueSerUnIdMayorACero() {
        // CON-02: 0 y negativos se aceptaban con 201.
        for (long mal : new long[] {0, -5, Long.MIN_VALUE}) {
            PedidoExternoRequest r = valido();
            r.lineas.get(0).idItem = mal;
            Set<ConstraintViolation<PedidoExternoRequest>> errores = validador.validate(r);
            assertEquals(Set.of("lineas[0].idItem"), camposConError(r), "idItem " + mal);
            assertEquals("El ítem tiene que ser un id mayor a cero", errores.iterator().next().getMessage());
        }
        // Con PUNTO_PICKING no viene: sigue siendo válido.
        PedidoExternoRequest r = valido();
        r.lineas.get(0).idItem = null;
        assertTrue(validador.validate(r).isEmpty());
    }

    @Test
    void quienRecibeEsOpcionalPeroElEmailTieneQueSerValido() {
        // ADR-039: sin destinatario sigue siendo válido (no rompe a los ERP de antes).
        PedidoExternoRequest r = valido();
        r.nombreDestinatario = "Ana Pérez";
        r.telefonoDestinatario = "+54 11 4567-8901";
        r.emailDestinatario = "ana@correo.com";
        assertTrue(validador.validate(r).isEmpty());
        r.emailDestinatario = "ana.correo.com";
        assertEquals(Set.of("emailDestinatario"), camposConError(r));
    }

    @Test
    void losEspaciosDeMasNoHacenInvalidoAlEmail() {
        PedidoExternoRequest r = valido();
        r.emailDestinatario = "  Ana@Correo.com ";
        r.nombreDestinatario = "   ";
        r.normalizar();
        assertTrue(validador.validate(r).isEmpty());
        assertEquals("Ana@Correo.com", r.emailDestinatario);
        assertNull(r.nombreDestinatario);
    }

    // ---- Retiro en depósito (ADR-053) y bultos (ADR-056) ----

    private static Set<String> camposInvalidos(PedidoExternoRequest r) {
        return validador.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void unRetiroEnDepositoNoNecesitaDireccion() {
        PedidoExternoRequest r = valido();
        r.tipoEntrega = com.rabbit.pedidos.datos.model.TipoEntrega.RETIRO_EN_DEPOSITO;
        r.direccionEntrega = null;
        assertTrue(camposInvalidos(r).isEmpty(), "un retiro sin dirección es válido");
    }

    @Test
    void aDomicilioLaDireccionSigueSiendoObligatoria() {
        for (com.rabbit.pedidos.datos.model.TipoEntrega tipo : new com.rabbit.pedidos.datos.model.TipoEntrega[] {
                null, com.rabbit.pedidos.datos.model.TipoEntrega.DOMICILIO}) {
            PedidoExternoRequest r = valido();
            r.tipoEntrega = tipo;
            r.direccionEntrega = "  ";
            assertEquals(Set.of("direccionEntrega"), camposInvalidos(r), "tipo " + tipo);
        }
    }

    @Test
    void elTipoDeEntregaViajaAlNegocio() {
        PedidoExternoRequest r = valido();
        r.tipoEntrega = com.rabbit.pedidos.datos.model.TipoEntrega.RETIRO_EN_DEPOSITO;
        r.bultos = 3;
        com.rabbit.pedidos.dto.DatosPedidoExternoDTO datos = r.aDatos();
        assertEquals(com.rabbit.pedidos.datos.model.TipoEntrega.RETIRO_EN_DEPOSITO, datos.tipoEntrega);
        assertEquals(3, datos.bultos);
    }

    @Test
    void losBultosVanDeUnoANoventaYNueve() {
        for (int bien : new int[] {1, 5, 99}) {
            PedidoExternoRequest r = valido();
            r.bultos = bien;
            assertTrue(camposInvalidos(r).isEmpty(), "bultos " + bien);
        }
        for (int mal : new int[] {0, -2, 100}) {
            PedidoExternoRequest r = valido();
            r.bultos = mal;
            assertEquals(Set.of("bultos"), camposInvalidos(r), "bultos " + mal);
        }
    }
}
