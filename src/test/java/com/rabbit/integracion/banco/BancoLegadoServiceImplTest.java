package com.rabbit.integracion.banco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** El banco legado simulado rechaza con un Fault lo que un banco real no autorizaría. */
class BancoLegadoServiceImplTest {

    private final BancoLegadoServiceImpl banco = new BancoLegadoServiceImpl();

    @Test
    void autorizaUnImporteNormal() throws Exception {
        assertTrue(banco.autorizarPago("PEDIDO-1", new BigDecimal("2500")).startsWith("AUT-"));
    }

    @Test
    void autorizaElLimiteExacto() throws Exception {
        assertTrue(banco.autorizarPago("PEDIDO-2", BancoLegadoServiceImpl.LIMITE).startsWith("AUT-"));
    }

    @Test
    void rechazaLoQueSuperaElLimite() {
        PagoRechazadoException e = assertThrows(PagoRechazadoException.class,
                () -> banco.autorizarPago("PEDIDO-3", new BigDecimal("500000.01")));
        assertTrue(e.getMessage().contains("supera el límite"), e.getMessage());
    }

    @Test
    void rechazaImportesNegativosCeroYNulo() {
        for (BigDecimal importe : new BigDecimal[] {new BigDecimal("-5"), BigDecimal.ZERO, null}) {
            PagoRechazadoException e = assertThrows(PagoRechazadoException.class,
                    () -> banco.autorizarPago("PEDIDO-4", importe), String.valueOf(importe));
            assertEquals("El importe tiene que ser mayor a cero", e.getMessage());
        }
    }
}
