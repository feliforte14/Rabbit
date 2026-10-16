package com.rabbit.notificaciones.negocio;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Lo que le llega a quien recibe el pedido (ADR-040). */
class TextosAlDestinatarioTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 16);

    @AfterEach
    void limpiar() {
        System.clearProperty(AvisoDestinatarioService.PROPIEDAD_URL);
    }

    private static TextosAlDestinatario.Datos datos(String estado, LocalDate prometida, String motivo) {
        return new TextosAlDestinatario.Datos(estado, "Ana", "Librería Sur", "RB-7KQ2M9XHTA",
                "https://rabbit.example/seguimiento.xhtml?codigo=RB-7KQ2M9XHTA", prometida, HOY, motivo);
    }

    @Test
    void alRecibirloLePrometeElDia() {
        TextosAlDestinatario.Datos d = datos("PENDIENTE", HOY, null);
        assertEquals("Recibimos tu pedido de Librería Sur", TextosAlDestinatario.asunto(d));
        String cuerpo = TextosAlDestinatario.cuerpo(d);
        assertTrue(cuerpo.startsWith("Hola, Ana. "));
        assertTrue(cuerpo.contains("Llega hoy."));
        assertTrue(cuerpo.endsWith("Seguilo con el código RB-7KQ2M9XHTA: https://rabbit.example/seguimiento.xhtml?codigo=RB-7KQ2M9XHTA"));
    }

    @Test
    void unaVisitaFallidaNoEsUnPedidoNuevo() {
        TextosAlDestinatario.Datos d = datos("PENDIENTE", HOY, "Cliente ausente");
        assertEquals("No pudimos entregar tu pedido de Librería Sur", TextosAlDestinatario.asunto(d));
        assertTrue(TextosAlDestinatario.cuerpo(d).contains("(Cliente ausente)"));
        assertFalse(TextosAlDestinatario.cuerpo(d).contains("Llega"));
    }

    @Test
    void enCaminoLlegaHoy() {
        assertEquals("¡Tu pedido de Librería Sur llega hoy!", TextosAlDestinatario.asunto(datos("EN_CAMINO", null, null)));
    }

    @Test
    void devueltoAlComercioDiceConQuienHablar() {
        String cuerpo = TextosAlDestinatario.cuerpo(datos("DEVUELTO_AL_COMERCIO", null, "El cliente rechazó el pedido"));
        assertTrue(cuerpo.contains("se lo devolvimos a Librería Sur"));
    }

    @Test
    void sinNombreSaludaIgual() {
        TextosAlDestinatario.Datos d = new TextosAlDestinatario.Datos("ENTREGADO", null, "Librería Sur", "RB-X",
                "link", null, HOY, null);
        assertTrue(TextosAlDestinatario.cuerpo(d).startsWith("Hola. "));
    }

    @Test
    void elDiaPrometidoComoLoDiriaUnaPersona() {
        assertEquals(" Llega mañana.", TextosAlDestinatario.cuando(HOY.plusDays(1), HOY));
        assertEquals(" Llega el lunes 19/10.", TextosAlDestinatario.cuando(LocalDate.of(2026, 10, 19), HOY));
        assertEquals("", TextosAlDestinatario.cuando(null, HOY));
        assertEquals("", TextosAlDestinatario.cuando(HOY.minusDays(1), HOY));
    }

    @Test
    void elLinkUsaLaUrlPublicaConfigurada() {
        assertEquals("https://localhost:8443/Rabbit/seguimiento.xhtml?codigo=RB-X",
                AvisoDestinatarioService.enlaceSeguimiento("RB-X"));
        System.setProperty(AvisoDestinatarioService.PROPIEDAD_URL, "https://rabbit.example.com/Rabbit/");
        assertEquals("https://rabbit.example.com/Rabbit/seguimiento.xhtml?codigo=RB-X",
                AvisoDestinatarioService.enlaceSeguimiento("RB-X"));
    }
}
