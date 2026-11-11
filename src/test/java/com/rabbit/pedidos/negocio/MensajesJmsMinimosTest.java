package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SEC-08: los mensajes JMS llevan lo mínimo. Se prueba el cuerpo que arman los
 * publicadores (el mismo que se manda al broker), sin necesidad de un broker.
 */
class MensajesJmsMinimosTest {

    // Nombres que NO pueden aparecer en ningún mensaje: datos personales o de dinero.
    private static final List<String> PROHIBIDOS = List.of(
            "direccion", "direccionEntrega", "email", "emailDestinatario", "telefono",
            "nombre", "destinatario", "importe", "monto", "tarjeta");

    private static JsonObject leer(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }

    @Test
    void laColaLlevaSoloElIdDelPedidoExterno() {
        JsonObject cuerpo = leer(PublicadorPedidosExternos.construirPayload(42L));
        assertEquals(List.of("idPedidoExterno"), List.copyOf(cuerpo.keySet()));
        assertEquals(42, cuerpo.getInt("idPedidoExterno"));
    }

    @Test
    void elTopicoLlevaSoloIdsEstadoYFecha() {
        EstadoPedidoCambiado evento = new EstadoPedidoCambiado(7L, 3L, EstadoPedido.PENDIENTE,
                LocalDateTime.of(2026, 10, 21, 10, 30));
        JsonObject cuerpo = leer(PublicadorEstadosPedido.construirPayload(evento));
        assertEquals(java.util.Set.of("idPedido", "idComercio", "estado", "fechaCambio"), cuerpo.keySet());
        assertEquals(7, cuerpo.getInt("idPedido"));
        assertEquals(3, cuerpo.getInt("idComercio"));
        assertEquals("PENDIENTE", cuerpo.getString("estado"));
    }

    @Test
    void ningunMensajeTieneCamposDePersonasNiDeDinero() {
        EstadoPedidoCambiado evento = new EstadoPedidoCambiado(1L, 1L, EstadoPedido.ENTREGADO, LocalDateTime.now());
        for (String json : List.of(PublicadorPedidosExternos.construirPayload(1L),
                PublicadorEstadosPedido.construirPayload(evento))) {
            JsonObject cuerpo = leer(json);
            for (String campo : PROHIBIDOS) {
                assertFalse(cuerpo.containsKey(campo), "el mensaje no debe llevar '" + campo + "': " + json);
            }
        }
    }
}
