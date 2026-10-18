package com.rabbit.notificaciones.negocio;

import static com.rabbit.pedidos.datos.model.MotivoEntregaFallida.*;
import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;

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

    private static TextosAlDestinatario.Datos datos(String estado, LocalDate prometida, MotivoEntregaFallida motivo) {
        return datos(estado, prometida, motivo, motivo != null && motivo.isCuentaComoVisita(), 1, 3);
    }

    private static TextosAlDestinatario.Datos datos(String estado, LocalDate prometida, MotivoEntregaFallida motivo,
                                                    boolean conto, int visitas, int maximo) {
        return new TextosAlDestinatario.Datos(estado, "Ana", "Librería Sur", "RB-7KQ2M9XHTA",
                "https://rabbit.example/seguimiento.xhtml?codigo=RB-7KQ2M9XHTA", prometida, HOY, motivo, conto,
                visitas, maximo);
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
        TextosAlDestinatario.Datos d = datos("PENDIENTE", HOY, CLIENTE_AUSENTE);
        assertEquals("No pudimos entregar tu pedido de Librería Sur", TextosAlDestinatario.asunto(d));
        assertTrue(TextosAlDestinatario.cuerpo(d).contains("no había nadie"));
        assertTrue(TextosAlDestinatario.cuerpo(d).contains("Lo vamos a intentar de nuevo"));
        assertFalse(TextosAlDestinatario.cuerpo(d).contains("Llega"));
    }

    @Test
    void cadaMotivoLeDiceAlClienteQueHacer() {
        assertTrue(TextosAlDestinatario.cuerpo(datos("PENDIENTE", null, DIRECCION_INCORRECTA))
                .contains("Comunicate con Librería Sur para confirmarla o corregirla"));
        assertTrue(TextosAlDestinatario.cuerpo(datos("PENDIENTE", null, NADIE_HABILITADO))
                .contains("mayor de edad con DNI"));
        assertTrue(TextosAlDestinatario.cuerpo(datos("PENDIENTE", null, DOMICILIO_CERRADO))
                .contains("en qué horario se puede entregar"));
    }

    @Test
    void avisaCuandoLaProximaEsLaUltimaVisita() {
        // Visita 1 de 2 fallida: la próxima es la última.
        String cuerpo = TextosAlDestinatario.cuerpo(datos("PENDIENTE", null, CLIENTE_AUSENTE, true, 1, 2));
        assertTrue(cuerpo.contains("es la última visita"));
        assertTrue(cuerpo.contains("vuelve a Librería Sur"));
    }

    @Test
    void unProblemaDelRepartidorNoCulpaAlCliente() {
        TextosAlDestinatario.Datos d = datos("PENDIENTE", null, PROBLEMA_DEL_REPARTIDOR, false, 0, 2);
        assertEquals("Tu pedido de Librería Sur se demora un poco", TextosAlDestinatario.asunto(d));
        assertTrue(TextosAlDestinatario.cuerpo(d).contains("No tenés que hacer nada"));
        assertFalse(TextosAlDestinatario.cuerpo(d).contains("Pasamos"));
    }

    @Test
    void unPaqueteDanadoPideCoordinarUnReemplazo() {
        String cuerpo = TextosAlDestinatario.cuerpo(datos("DEVUELTO_AL_COMERCIO", null, PAQUETE_DANADO, false, 0, 2));
        assertTrue(cuerpo.contains("se dañó"));
        assertTrue(cuerpo.contains("coordinar un reemplazo"));
    }

    @Test
    void enCaminoLlegaHoy() {
        assertEquals("¡Tu pedido de Librería Sur llega hoy!", TextosAlDestinatario.asunto(datos("EN_CAMINO", null, null)));
    }

    @Test
    void devueltoAlComercioDiceConQuienHablar() {
        String cuerpo = TextosAlDestinatario.cuerpo(datos("DEVUELTO_AL_COMERCIO", null, RECHAZADO));
        assertTrue(cuerpo.contains("se lo devolvimos a Librería Sur"));
        String agotadas = TextosAlDestinatario.cuerpo(datos("DEVUELTO_AL_COMERCIO", null, CLIENTE_AUSENTE, true, 2, 2));
        assertTrue(agotadas.contains("después de 2 visitas (cliente ausente)"));
    }

    @Test
    void sinNombreSaludaIgual() {
        TextosAlDestinatario.Datos d = new TextosAlDestinatario.Datos("ENTREGADO", null, "Librería Sur", "RB-X",
                "link", null, HOY, null, false, 0, 2);
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
