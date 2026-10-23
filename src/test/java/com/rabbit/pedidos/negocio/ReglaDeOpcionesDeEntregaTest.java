package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.TipoEntrega;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** ADR-057: qué puede cambiar quien recibe desde el seguimiento. */
class ReglaDeOpcionesDeEntregaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 22);
    private static final TipoEntrega DOM = TipoEntrega.DOMICILIO;

    // ---- cuándo se puede ----

    @Test
    void pendienteOConfirmadoConRepartidorSePuedeModificar() {
        assertTrue(ReglaDeOpcionesDeEntrega.sePuedeModificar(EstadoPedido.PENDIENTE, DOM, null));
        assertTrue(ReglaDeOpcionesDeEntrega.sePuedeModificar(EstadoPedido.CONFIRMADO, DOM, 7L));
    }

    @Test
    void yaEnCaminoOResueltoNoSePuede() {
        for (EstadoPedido estado : new EstadoPedido[] {EstadoPedido.EN_CAMINO, EstadoPedido.ENTREGADO,
                EstadoPedido.CANCELADO, EstadoPedido.DEVUELTO_AL_COMERCIO}) {
            assertFalse(ReglaDeOpcionesDeEntrega.sePuedeModificar(estado, DOM, 7L), "estado " + estado);
        }
    }

    @Test
    void confirmadoSinRepartidorLoLlevaUnaEmpresaYNoSePuede() {
        assertFalse(ReglaDeOpcionesDeEntrega.sePuedeModificar(EstadoPedido.CONFIRMADO, DOM, null));
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeOpcionesDeEntrega.exigirModificable(EstadoPedido.CONFIRMADO, DOM, null, 0));
        assertTrue(e.getMessage().contains("empresa de logística"));
    }

    @Test
    void unRetiroEnDepositoNoTieneEntregaQueCambiar() {
        assertFalse(ReglaDeOpcionesDeEntrega.sePuedeModificar(EstadoPedido.PENDIENTE, TipoEntrega.RETIRO_EN_DEPOSITO, null));
        assertThrows(ValidacionException.class, () -> ReglaDeOpcionesDeEntrega.exigirModificable(
                EstadoPedido.CONFIRMADO, TipoEntrega.RETIRO_EN_DEPOSITO, null, 0));
    }

    @Test
    void enCaminoElMensajeDiceQueYaSalio() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeOpcionesDeEntrega.exigirModificable(EstadoPedido.EN_CAMINO, DOM, 7L, 0));
        assertTrue(e.getMessage().contains("ya salió"));
    }

    @Test
    void hayUnTopeDeCambios() {
        assertDoesNotThrow(() -> ReglaDeOpcionesDeEntrega.exigirModificable(
                EstadoPedido.PENDIENTE, DOM, null, ReglaDeOpcionesDeEntrega.CAMBIOS_MAXIMOS - 1));
        ValidacionException e = assertThrows(ValidacionException.class, () -> ReglaDeOpcionesDeEntrega.exigirModificable(
                EstadoPedido.PENDIENTE, DOM, null, ReglaDeOpcionesDeEntrega.CAMBIOS_MAXIMOS));
        assertTrue(e.getMessage().contains("máximo de cambios"));
    }

    // ---- fecha ----

    @Test
    void sinFechaNoSeReprograma() {
        assertNull(ReglaDeOpcionesDeEntrega.validarFecha(HOY, HOY.plusDays(1), null));
    }

    @Test
    void hastaSieteDiasDesdeHoySeAcepta() {
        assertEquals(HOY.plusDays(7), ReglaDeOpcionesDeEntrega.validarFecha(HOY, HOY, HOY.plusDays(7)));
        assertEquals(HOY.plusDays(3), ReglaDeOpcionesDeEntrega.validarFecha(HOY, HOY.plusDays(1), HOY.plusDays(3)));
    }

    @Test
    void masDeSieteDiasSeRechaza() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeOpcionesDeEntrega.validarFecha(HOY, HOY, HOY.plusDays(8)));
        assertTrue(e.getMessage().contains("7 días"));
    }

    @Test
    void noSePuedeElegirUnDiaPasado() {
        assertThrows(ValidacionException.class, () -> ReglaDeOpcionesDeEntrega.validarFecha(HOY, HOY, HOY.minusDays(1)));
        assertThrows(ValidacionException.class, () -> ReglaDeOpcionesDeEntrega.validarFecha(HOY, null, HOY.minusDays(1)));
    }

    @Test
    void noSePuedeAdelantarLoPrometido() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeOpcionesDeEntrega.validarFecha(HOY, HOY.plusDays(3), HOY.plusDays(1)));
        assertTrue(e.getMessage().contains("adelantar"));
        assertEquals(HOY.plusDays(3), ReglaDeOpcionesDeEntrega.validarFecha(HOY, HOY.plusDays(3), HOY.plusDays(3)));
    }

    @Test
    void siLoPrometidoYaPasoSeCuentaDesdeHoy() {
        assertEquals(HOY, ReglaDeOpcionesDeEntrega.validarFecha(HOY, HOY.minusDays(2), HOY));
    }

    // ---- textos de un tercero ----

    @Test
    void lasInstruccionesSeLimpianYUnaVaciaNoSeGuarda() {
        assertEquals("dejar en portería", ReglaDeOpcionesDeEntrega.limpiarInstrucciones("  dejar en portería \u0000"));
        assertNull(ReglaDeOpcionesDeEntrega.limpiarInstrucciones("   "));
        assertNull(ReglaDeOpcionesDeEntrega.limpiarInstrucciones(null));
        assertEquals("tocar timbre\n3B", ReglaDeOpcionesDeEntrega.limpiarInstrucciones("tocar timbre\r\n3B"));
    }

    @Test
    void lasInstruccionesTienenUnMaximo() {
        String justo = "x".repeat(ReglaDeOpcionesDeEntrega.INSTRUCCIONES_MAXIMO);
        assertEquals(justo, ReglaDeOpcionesDeEntrega.limpiarInstrucciones(justo));
        assertThrows(ValidacionException.class, () -> ReglaDeOpcionesDeEntrega.limpiarInstrucciones(justo + "x"));
    }

    @Test
    void elNombreVaEnUnaSolaLinea() {
        assertEquals("Juan Pérez", ReglaDeOpcionesDeEntrega.limpiarNombre("  Juan\nPérez "));
        assertNull(ReglaDeOpcionesDeEntrega.limpiarNombre(""));
        assertThrows(ValidacionException.class,
                () -> ReglaDeOpcionesDeEntrega.limpiarNombre("x".repeat(ReglaDeOpcionesDeEntrega.NOMBRE_MAXIMO + 1)));
    }

    @Test
    void elHtmlNoSeToca_seEscapaAlMostrar() {
        assertEquals("<b>hola</b>", ReglaDeOpcionesDeEntrega.limpiarInstrucciones("<b>hola</b>"));
    }

    @Test
    void validarJuntaLasTresOpciones() {
        ReglaDeOpcionesDeEntrega.Cambios c = ReglaDeOpcionesDeEntrega.validar(HOY, HOY, HOY.plusDays(2),
                " dejar en portería ", " Ana ");
        assertEquals(HOY.plusDays(2), c.fecha());
        assertEquals("dejar en portería", c.instrucciones());
        assertEquals("Ana", c.nombreAutorizado());
    }
}
