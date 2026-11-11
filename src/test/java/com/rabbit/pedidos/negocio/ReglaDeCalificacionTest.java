package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import org.junit.jupiter.api.Test;

/** ADR-052: qué se puede calificar y con qué datos. */
class ReglaDeCalificacionTest {

    @Test
    void unPedidoEntregadoSeCalificaDeUnoACinco() {
        for (int estrellas = 1; estrellas <= 5; estrellas++) {
            assertNull(ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, estrellas, null));
        }
    }

    @Test
    void soloSeCalificaUnPedidoEntregado() {
        for (EstadoPedido estado : EstadoPedido.values()) {
            if (estado == EstadoPedido.ENTREGADO) {
                continue;
            }
            ValidacionException e = assertThrows(ValidacionException.class,
                    () -> ReglaDeCalificacion.validar(estado, 5, "ok"), "no debería calificarse en " + estado);
            assertTrue(e.getMessage().contains("entregado"));
        }
    }

    @Test
    void lasEstrellasFueraDeRangoSeRechazan() {
        for (int invalida : new int[] {0, -1, 6, 100}) {
            assertThrows(ValidacionException.class,
                    () -> ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, invalida, null));
        }
    }

    @Test
    void elComentarioEsOpcionalYUnoVacioNoSeGuarda() {
        assertNull(ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 4, null));
        assertNull(ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 4, ""));
        assertNull(ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 4, "   \n\t  "));
    }

    @Test
    void elComentarioSeRecortaYSeConserva() {
        assertEquals("Llegó rápido", ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 5, "  Llegó rápido  "));
    }

    @Test
    void sinCaracteresDeControlYConSaltosDeLineaNormalizados() {
        assertEquals("a\nb", ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 3, "a\r\nb"));
        assertEquals("ab", ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 3, "a\u0000b"));
        assertEquals("ab", ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 3, "a\u0007b"));
    }

    @Test
    void elComentarioTieneUnMaximo() {
        String justo = "x".repeat(ReglaDeCalificacion.COMENTARIO_MAXIMO);
        assertEquals(justo, ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 5, justo));
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 5, justo + "x"));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void losEspaciosDeLosBordesNoCuentanParaElMaximo() {
        String justo = "x".repeat(ReglaDeCalificacion.COMENTARIO_MAXIMO);
        assertEquals(justo, ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 5, "   " + justo + "   "));
    }

    @Test
    void elHtmlNoSeToca_seEscapaAlMostrar() {
        // La regla no "limpia" HTML: JSF escapa #{...} al dibujar. Acá solo importa que no se pierda texto.
        assertEquals("<b>hola</b>", ReglaDeCalificacion.validar(EstadoPedido.ENTREGADO, 2, "<b>hola</b>"));
    }
}
