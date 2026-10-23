package com.rabbit.inventario.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** ADR-058: cómo se muestra la diferencia entre lo anunciado y lo recibido. */
class OrdenDeRecepcionDTOTest {

    private static OrdenDeRecepcionDTO.Linea linea(int esperada, Integer recibida) {
        OrdenDeRecepcionDTO.Linea l = new OrdenDeRecepcionDTO.Linea();
        l.producto = "Remera";
        l.cantidadEsperada = esperada;
        l.cantidadRecibida = recibida;
        return l;
    }

    @Test
    void antesDeRecibirNoHayDiferencia() {
        OrdenDeRecepcionDTO.Linea l = linea(10, null);
        assertNull(l.getDiferencia());
        assertEquals("", l.getDiferenciaTexto());
    }

    @Test
    void faltoLlegoDeMasOCompleto() {
        assertEquals(-3, linea(10, 7).getDiferencia());
        assertEquals("Faltaron 3", linea(10, 7).getDiferenciaTexto());
        assertEquals("Llegaron 2 de más", linea(10, 12).getDiferenciaTexto());
        assertEquals("Completo", linea(10, 10).getDiferenciaTexto());
    }

    @Test
    void laOrdenSabeSiHayDiferencia() {
        OrdenDeRecepcionDTO o = new OrdenDeRecepcionDTO();
        o.lineas = java.util.List.of(linea(10, 10), linea(5, 4));
        assertTrue(o.isHayDiferencia());
        assertEquals(15, o.getTotalEsperado());
        assertEquals(14, o.getTotalRecibido());
        o.lineas = java.util.List.of(linea(10, 10), linea(5, 5));
        assertFalse(o.isHayDiferencia());
    }

    @Test
    void sinRecibirNadaNoHayDiferenciaYElResumenCuenta() {
        OrdenDeRecepcionDTO o = new OrdenDeRecepcionDTO();
        o.lineas = java.util.List.of(linea(10, null), linea(5, null));
        assertFalse(o.isHayDiferencia());
        assertEquals("2 productos · 15 u.", o.getResumen());
    }
}
