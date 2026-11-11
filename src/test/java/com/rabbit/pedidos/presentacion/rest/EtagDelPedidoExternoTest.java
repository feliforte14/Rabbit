package com.rabbit.pedidos.presentacion.rest;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

/** ETag del pedido externo (If-Match → 412): cambia con el estado, no con el host de los links. */
class EtagDelPedidoExternoTest {

    private static JsonObject pedido(String resultado, String host) {
        return Json.createObjectBuilder()
                .add("idPedidoExterno", 42)
                .add("resultado", resultado)
                .add("_links", Json.createObjectBuilder()
                        .add("self", Json.createObjectBuilder().add("href", host + "/api/v1/pedidos-externos/42")))
                .build();
    }

    @Test
    void elMismoEstadoDaElMismoEtagAunqueCambieElHost() {
        assertEquals(PedidosExternosResource.huella(pedido("Pendiente", "https://localhost:8443/Rabbit")),
                PedidosExternosResource.huella(pedido("Pendiente", "https://rabbit.example/Rabbit")));
    }

    @Test
    void siCambiaElEstadoCambiaElEtag() {
        assertNotEquals(PedidosExternosResource.huella(pedido("Pendiente", "https://h")),
                PedidosExternosResource.huella(pedido("Sincronizado", "https://h")));
    }

    @Test
    void sonLos32HexadecimalesDeUnSha256() {
        String huella = PedidosExternosResource.huella(pedido("Pendiente", "https://h"));
        assertTrue(huella.matches("[0-9a-f]{32}"), huella);
    }
}
