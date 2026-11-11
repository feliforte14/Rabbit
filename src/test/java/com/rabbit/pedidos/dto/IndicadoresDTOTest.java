package com.rabbit.pedidos.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Las diferencias contra el período anterior del tablero (ADR-044). */
class IndicadoresDTOTest {

    private static IndicadoresDTO tablero(int recibidos, int aTiempo, int conPromesa, String horas) {
        IndicadoresDTO t = new IndicadoresDTO();
        t.recibidos = recibidos;
        t.entregadosATiempo = aTiempo;
        t.entregadosConPromesa = conPromesa;
        t.horasPromedioEntrega = horas == null ? null : new BigDecimal(horas);
        return t;
    }

    @Test
    void sinPeriodoAnteriorNoHayDiferencias() {
        IndicadoresDTO t = tablero(10, 8, 10, "5.0");
        assertNull(t.getDeltaATiempo());
        assertNull(t.getDeltaRecibidos());
        assertNull(t.getDeltaHoras());
    }

    @Test
    void laDiferenciaDePorcentajesEsEnPuntos() {
        IndicadoresDTO t = tablero(10, 8, 10, "5.0");     // 80 %
        t.anterior = tablero(20, 15, 20, "6.5");          // 75 %
        assertEquals(5, t.getDeltaATiempo());
        assertEquals(-50, t.getDeltaRecibidos());
        assertEquals(new BigDecimal("-1.5"), t.getDeltaHoras());
    }

    @Test
    void siAntesNoHabiaBaseNoSeInventaUnaDiferencia() {
        IndicadoresDTO t = tablero(10, 8, 10, "5.0");
        t.anterior = tablero(0, 0, 0, null);
        assertNull(t.getDeltaATiempo(), "antes no hubo entregas con promesa");
        assertNull(t.getDeltaRecibidos(), "antes no hubo pedidos: no hay porcentaje de variación");
        assertNull(t.getDeltaHoras());
    }
}
