package com.rabbit.inventario.negocio;

import static com.rabbit.inventario.datos.model.CategoriaDeTamano.*;
import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.inventario.datos.model.CategoriaDeTamano;
import com.rabbit.inventario.datos.model.ItemInventario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ADR-059: unidades-día de un mes a partir de las fotos diarias. */
class UnidadesDiaTest {

    private static final YearMonth NOVIEMBRE = YearMonth.of(2026, 11);   // 30 días
    private static final LocalDate FIN = LocalDate.of(2026, 12, 15);      // "hoy": el mes ya terminó

    private static Map<CategoriaDeTamano, Long> foto(long pequeno, long mediano, long grande) {
        Map<CategoriaDeTamano, Long> m = new java.util.EnumMap<>(CategoriaDeTamano.class);
        m.put(PEQUENO, pequeno);
        m.put(MEDIANO, mediano);
        m.put(GRANDE, grande);
        return m;
    }

    @Test
    void cienUnidadesPequenasUnMesEntero() {
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = new HashMap<>();
        for (int d = 1; d <= 30; d++) {
            fotos.put(NOVIEMBRE.atDay(d), foto(100, 0, 0));
        }
        Map<CategoriaDeTamano, Long> r = UnidadesDia.sumar(NOVIEMBRE, FIN, fotos, Map.of(), Map.of());
        assertEquals(3000, r.get(PEQUENO));    // 100 x 30, como el ejemplo de la propuesta
        assertEquals(0, r.get(MEDIANO));
        assertEquals(0, r.get(GRANDE));
    }

    @Test
    void siSeVendeLaMitadAMediadosDeMes() {
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = new HashMap<>();
        for (int d = 1; d <= 30; d++) {
            fotos.put(NOVIEMBRE.atDay(d), foto(d <= 15 ? 100 : 50, 0, 0));
        }
        // 100 x 15 + 50 x 15 = 2.250 unidades-día (el otro ejemplo de la propuesta)
        assertEquals(2250, UnidadesDia.sumar(NOVIEMBRE, FIN, fotos, Map.of(), Map.of()).get(PEQUENO));
    }

    @Test
    void unDiaSinFotoSeCompletaConLaAnterior() {
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = new HashMap<>();
        fotos.put(NOVIEMBRE.atDay(1), foto(10, 0, 0));
        fotos.put(NOVIEMBRE.atDay(5), foto(20, 0, 0));
        // días 1 a 4: 10; días 5 a 30: 20 (el servidor estuvo apagado y no hubo fotos entre medio)
        assertEquals(10 * 4 + 20 * 26, UnidadesDia.sumar(NOVIEMBRE, FIN, fotos, Map.of(), Map.of()).get(PEQUENO));
    }

    @Test
    void siElDiaUnoNoTieneFotoArrancaDeLaUltimaDelMesAnterior() {
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = new HashMap<>();
        fotos.put(NOVIEMBRE.atDay(3), foto(7, 0, 0));
        Map<CategoriaDeTamano, Long> octubre = foto(5, 0, 0);
        // días 1 y 2: 5 (lo último que se sabía); días 3 a 30: 7
        assertEquals(5 * 2 + 7 * 28, UnidadesDia.sumar(NOVIEMBRE, FIN, fotos, octubre, Map.of()).get(PEQUENO));
    }

    @Test
    void sinFotosNiPreviaNoHayNada() {
        Map<CategoriaDeTamano, Long> r = UnidadesDia.sumar(NOVIEMBRE, FIN, Map.of(), Map.of(), Map.of());
        assertEquals(0, r.get(PEQUENO) + r.get(MEDIANO) + r.get(GRANDE));
    }

    @Test
    void elMesEnCursoLlegaHastaHoyConLoQueHayAhora() {
        LocalDate hoy = NOVIEMBRE.atDay(10);
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = new HashMap<>();
        for (int d = 1; d <= 9; d++) {
            fotos.put(NOVIEMBRE.atDay(d), foto(10, 0, 0));
        }
        // 9 días de foto + hoy en vivo (12): 90 + 12; los días que faltan del mes no cuentan
        assertEquals(102, UnidadesDia.sumar(NOVIEMBRE, hoy, fotos, Map.of(), foto(12, 0, 0)).get(PEQUENO));
    }

    @Test
    void siHoyYaTieneFotoCuentaLaFotoYNoLoDeAhora() {
        LocalDate hoy = NOVIEMBRE.atDay(2);
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = new HashMap<>();
        fotos.put(NOVIEMBRE.atDay(1), foto(10, 0, 0));
        fotos.put(hoy, foto(11, 0, 0));
        assertEquals(21, UnidadesDia.sumar(NOVIEMBRE, hoy, fotos, Map.of(), foto(99, 0, 0)).get(PEQUENO));
    }

    @Test
    void unMesFuturoNoTieneUnidadesDia() {
        Map<CategoriaDeTamano, Long> r = UnidadesDia.sumar(YearMonth.of(2027, 1), FIN, Map.of(), foto(5, 5, 5), foto(5, 5, 5));
        assertEquals(0, r.get(PEQUENO) + r.get(MEDIANO) + r.get(GRANDE));
    }

    @Test
    void lasTresCategoriasSeSumanPorSeparado() {
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = new HashMap<>();
        fotos.put(NOVIEMBRE.atDay(1), foto(10, 4, 1));
        Map<CategoriaDeTamano, Long> r = UnidadesDia.sumar(NOVIEMBRE, FIN, fotos, Map.of(), Map.of());
        assertEquals(300, r.get(PEQUENO));
        assertEquals(120, r.get(MEDIANO));
        assertEquals(30, r.get(GRANDE));
    }

    @Test
    void loFueraDeRangoSeCobraComoGrande() {
        Map<LocalDate, Map<CategoriaDeTamano, Long>> fotos = new HashMap<>();
        Map<CategoriaDeTamano, Long> f = new java.util.EnumMap<>(CategoriaDeTamano.class);
        f.put(FUERA_DE_RANGO, 2L);
        fotos.put(NOVIEMBRE.atDay(1), f);
        assertEquals(60, UnidadesDia.sumar(NOVIEMBRE, FIN, fotos, Map.of(), Map.of()).get(GRANDE));
    }

    // ---- el stock de ahora, por comercio y categoría ----

    private static ItemInventario item(Long comercio, int disponible, String peso, String alto, String ancho, String largo) {
        ItemInventario i = new ItemInventario();
        i.setIdComercio(comercio);
        i.setCantidadDisponible(disponible);
        i.setPesoKg(peso == null ? null : new BigDecimal(peso));
        i.setMedidas(alto == null ? null : new BigDecimal(alto), ancho == null ? null : new BigDecimal(ancho),
                largo == null ? null : new BigDecimal(largo));
        return i;
    }

    @Test
    void elStockDeAhoraSeAgrupaPorComercioYCategoria() {
        Map<Long, Map<CategoriaDeTamano, Long>> r = OcupacionService.unidadesActuales(List.of(
                item(1L, 100, "1", "25", "10", "7"),         // yerba: pequeña
                item(1L, 8, "8", "50", "40", "30"),          // microondas: grande
                item(1L, 3, "0.2", null, null, null),        // sin medidas: mediana
                item(2L, 5, "1", "10", "10", "10"),
                item(2L, 0, "1", "10", "10", "10"),          // sin unidades: no cuenta
                item(null, 9, "1", "10", "10", "10")));      // sin dueño: no cuenta
        assertEquals(100L, r.get(1L).get(PEQUENO));
        assertEquals(8L, r.get(1L).get(GRANDE));
        assertEquals(3L, r.get(1L).get(MEDIANO));
        assertEquals(5L, r.get(2L).get(PEQUENO));
        assertEquals(2, r.size());
    }
}
