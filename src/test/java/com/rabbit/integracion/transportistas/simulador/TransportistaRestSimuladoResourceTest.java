package com.rabbit.integracion.transportistas.simulador;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cotización del transportista REST simulado: el recargo por cobrar al entregar se aplica solo si el pedido
 * trae un importe a cobrar. Antes alcanzaba con que el campo existiera, y {"cobrarAlEntregar": false}
 * cotizaba como contra entrega.
 */
class TransportistaRestSimuladoResourceTest {

    private final TransportistaRestSimuladoResource recurso = new TransportistaRestSimuladoResource();

    private String precio(String cuerpo) {
        Response r = recurso.cotizar(cuerpo);
        assertEquals(200, r.getStatus());
        return r.getEntity().toString();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("El recargo de $500 aparece solo con un importe a cobrar")
    @CsvSource(delimiter = '|', value = {
            "{\"bultos\":3}|3550.00",
            "{\"bultos\":3,\"cobrarAlEntregar\":false}|3550.00",
            "{\"bultos\":3,\"cobrarAlEntregar\":null}|3550.00",
            "{\"bultos\":3,\"cobrarAlEntregar\":0}|3550.00",
            "{\"bultos\":3,\"cobrarAlEntregar\":2500}|4050.00",
            "{\"bultos\":3,\"cobrarAlEntregar\":true}|4050.00" })
    void recargoSoloConImporteACobrar(String cuerpo, String esperado) {
        assertEquals(true, precio(cuerpo).contains("\"precio\":" + esperado));
    }

    @Test
    @DisplayName("Cotizar rechaza lo que el envío real también rechazaría")
    void rechazos() {
        assertEquals(422, recurso.cotizar("{\"bultos\":51}").getStatus());
        assertEquals(422, recurso.cotizar("{\"bultos\":0}").getStatus());
        assertEquals(400, recurso.cotizar("{x").getStatus());
    }
}
