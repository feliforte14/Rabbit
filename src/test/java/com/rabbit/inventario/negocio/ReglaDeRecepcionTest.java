package com.rabbit.inventario.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.inventario.datos.model.EstadoOrdenDeRecepcion;
import com.rabbit.inventario.dto.DatosLineaRecepcionDTO;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ADR-058: anuncio de mercadería y registro de lo recibido. */
class ReglaDeRecepcionTest {

    private static DatosLineaRecepcionDTO linea(String producto, int cantidad, String peso) {
        return new DatosLineaRecepcionDTO(producto, cantidad, peso == null ? null : new BigDecimal(peso));
    }

    // ---- anuncio ----

    @Test
    void unAnuncioValidoSeLimpia() {
        List<DatosLineaRecepcionDTO> r = ReglaDeRecepcion.validarAnuncio(
                List.of(linea("  Remera azul ", 10, "0.25"), linea("Gorra", 5, "0.1")));
        assertEquals(2, r.size());
        assertEquals("Remera azul", r.get(0).producto);
        assertEquals(10, r.get(0).cantidad);
    }

    @Test
    void sinProductosNoHayOrden() {
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(List.of()));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(null));
    }

    @Test
    void lasFilasVaciasDelFormularioSeIgnoran() {
        List<DatosLineaRecepcionDTO> r = ReglaDeRecepcion.validarAnuncio(
                List.of(linea("Remera", 3, "0.2"), new DatosLineaRecepcionDTO(), linea("", 0, null)));
        assertEquals(1, r.size());
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(
                List.of(new DatosLineaRecepcionDTO(), new DatosLineaRecepcionDTO())), "solo filas vacías: no hay orden");
    }

    @Test
    void cadaLineaNecesitaProductoCantidadYPeso() {
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("", 3, "1"))));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("A", 0, "1"))));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("A", -1, "1"))));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("A", 1, null))));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("A", 1, "0"))));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("A", 1, "1.2345"))));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("A", 1, "1001"))));
    }

    @Test
    void laCantidadTieneTope() {
        assertDoesNotThrow(() -> ReglaDeRecepcion.validarAnuncio(List.of(linea("A", ReglaDeRecepcion.CANTIDAD_MAXIMA, "1"))));
        assertThrows(ValidacionException.class,
                () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("A", ReglaDeRecepcion.CANTIDAD_MAXIMA + 1, "1"))));
    }

    @Test
    void unProductoRepetidoSeRechazaSinImportarMayusculas() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeRecepcion.validarAnuncio(List.of(linea("Remera", 1, "1"), linea(" remera ", 2, "1"))));
        assertTrue(e.getMessage().contains("repetido"));
    }

    @Test
    void hayUnMaximoDeLineas() {
        DatosLineaRecepcionDTO[] muchas = new DatosLineaRecepcionDTO[ReglaDeRecepcion.MAXIMO_LINEAS + 1];
        for (int i = 0; i < muchas.length; i++) {
            muchas[i] = linea("Producto " + i, 1, "1");
        }
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.validarAnuncio(Arrays.asList(muchas)));
        assertDoesNotThrow(() -> ReglaDeRecepcion.validarAnuncio(Arrays.asList(muchas).subList(0, ReglaDeRecepcion.MAXIMO_LINEAS)));
    }

    @Test
    void lasNotasSeLimpianYTienenMaximo() {
        assertEquals("llega el jueves", ReglaDeRecepcion.limpiarNotas("  llega\tel   jueves \n"));
        assertNull(ReglaDeRecepcion.limpiarNotas("   "));
        assertNull(ReglaDeRecepcion.limpiarNotas(null));
        assertThrows(ValidacionException.class,
                () -> ReglaDeRecepcion.limpiarNotas("x".repeat(ReglaDeRecepcion.NOTAS_MAXIMO + 1)));
    }

    // ---- recepción ----

    @Test
    void siLlegoTodoQuedaRecibida() {
        ReglaDeRecepcion.Resultado r = ReglaDeRecepcion.resolver(List.of(10, 5), List.of(10, 5));
        assertEquals(EstadoOrdenDeRecepcion.RECIBIDA, r.estado());
        assertEquals(15, r.totalEsperado());
        assertEquals(15, r.totalRecibido());
    }

    @Test
    void siFaltoAlgoQuedaParcial() {
        ReglaDeRecepcion.Resultado r = ReglaDeRecepcion.resolver(List.of(10, 5), List.of(8, 5));
        assertEquals(EstadoOrdenDeRecepcion.PARCIAL, r.estado());
        assertEquals(13, r.totalRecibido());
    }

    @Test
    void siUnaLineaNoLlegoQuedaParcial() {
        assertEquals(EstadoOrdenDeRecepcion.PARCIAL, ReglaDeRecepcion.resolver(List.of(10, 5), List.of(10, 0)).estado());
    }

    @Test
    void llegarDeMasNoLaVuelveParcial() {
        ReglaDeRecepcion.Resultado r = ReglaDeRecepcion.resolver(List.of(10, 5), List.of(12, 5));
        assertEquals(EstadoOrdenDeRecepcion.RECIBIDA, r.estado());
        assertEquals(17, r.totalRecibido());
    }

    @Test
    void sobraEnUnaYFaltaEnOtraEsParcial() {
        assertEquals(EstadoOrdenDeRecepcion.PARCIAL, ReglaDeRecepcion.resolver(List.of(10, 5), List.of(12, 3)).estado());
    }

    @Test
    void hayQueInformarCadaLinea() {
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.resolver(List.of(10, 5), List.of(10)));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.resolver(List.of(10, 5), null));
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.resolver(List.of(10, 5), Arrays.asList(10, null)));
    }

    @Test
    void loRecibidoNoPuedeSerNegativoNiExagerado() {
        assertThrows(ValidacionException.class, () -> ReglaDeRecepcion.resolver(List.of(10), List.of(-1)));
        assertThrows(ValidacionException.class,
                () -> ReglaDeRecepcion.resolver(List.of(10), List.of(ReglaDeRecepcion.CANTIDAD_MAXIMA + 1)));
    }

    @Test
    void siNoLlegoNadaSeCancelaEnVezDeRecibir() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> ReglaDeRecepcion.resolver(List.of(10, 5), List.of(0, 0)));
        assertTrue(e.getMessage().contains("cancelá"));
    }
}
