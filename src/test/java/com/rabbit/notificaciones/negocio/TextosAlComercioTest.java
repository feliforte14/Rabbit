package com.rabbit.notificaciones.negocio;

import static com.rabbit.pedidos.datos.model.MotivoEntregaFallida.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** El aviso al comercio cuando un pedido no se pudo entregar (ADR-041, ADR-045). */
class TextosAlComercioTest {

    @Test
    void unaVisitaFallidaDiceEnCualVaYQueHacer() {
        String texto = TextosAlComercio.entregaFallida(7L, "PENDIENTE", DIRECCION_INCORRECTA,
                "Dirección incorrecta o inexistente: no existe el número 1234", true, 1, 3);
        assertTrue(texto.startsWith("Tu pedido #7: visita 1 de 3 fallida"));
        assertTrue(texto.contains("no existe el número 1234"));
        assertTrue(texto.contains("Revisá la dirección con tu cliente"));
        assertTrue(texto.contains("Ver visitas"));
    }

    @Test
    void avisaCuandoLaProximaEsLaUltima() {
        String texto = TextosAlComercio.entregaFallida(7L, "PENDIENTE", CLIENTE_AUSENTE, null, true, 1, 2);
        assertTrue(texto.contains("La próxima es la última"));
    }

    @Test
    void siNoContoComoVisitaNoLoCulpaAlCliente() {
        String texto = TextosAlComercio.entregaFallida(7L, "PENDIENTE", PROBLEMA_DEL_REPARTIDOR, null, false, 0, 2);
        assertTrue(texto.contains("no le cuenta una visita a tu cliente"));
    }

    @Test
    void elTextoMasLargoEntraEnLaColumnaDelPortal() {
        // notificaciones.mensaje es de 255; con un comentario de 200 se recorta, pero sin comentario tiene que entrar.
        for (var motivo : com.rabbit.pedidos.datos.model.MotivoEntregaFallida.values()) {
            for (String estado : new String[] { "PENDIENTE", "DEVUELTO_AL_COMERCIO" }) {
                String texto = TextosAlComercio.entregaFallida(123456L, estado, motivo, null, true, 1, 2);
                assertTrue(texto.length() <= 255, motivo + " " + estado + ": " + texto.length());
            }
        }
    }

    @Test
    void unPaqueteDanadoEsUnaIncidenciaYNoVuelveAlStock() {
        String texto = TextosAlComercio.entregaFallida(7L, "DEVUELTO_AL_COMERCIO", PAQUETE_DANADO,
                "Paquete dañado: caja aplastada", false, 0, 2);
        assertTrue(texto.startsWith("INCIDENCIA:"));
        assertTrue(texto.contains("NO vuelve a tu stock"));
        assertTrue(texto.contains("caja aplastada"));
    }

    @Test
    void unRechazoODevolucionPorVisitasDevuelveElStock() {
        assertTrue(TextosAlComercio.entregaFallida(7L, "DEVUELTO_AL_COMERCIO", RECHAZADO, null, true, 1, 2)
                .contains("porque el cliente lo rechazó"));
        String agotadas = TextosAlComercio.entregaFallida(7L, "DEVUELTO_AL_COMERCIO", CLIENTE_AUSENTE, null, true, 2, 2);
        assertTrue(agotadas.contains("después de 2 visita(s)"));
        assertTrue(agotadas.contains("el stock consignado vuelve a tu stock"));
    }
}
