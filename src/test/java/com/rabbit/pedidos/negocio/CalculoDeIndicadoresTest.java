package com.rabbit.pedidos.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.pedidos.datos.model.EstadoPedido;
import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.datos.model.Pedido;
import com.rabbit.pedidos.dto.IndicadoresDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El tablero de indicadores de entrega (ADR-044). */
class CalculoDeIndicadoresTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 12);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 16);

    private static Pedido pedido(EstadoPedido estado, Long idRepartidor) {
        Pedido p = new Pedido();
        p.setEstado(estado);
        p.setIdRepartidor(idRepartidor);
        p.setFechaCreacion(LUNES.atTime(10, 0));
        p.setFechaPrometida(LUNES);
        return p;
    }

    private static Pedido entregado(Long idRepartidor, LocalDateTime cuando, int intentosFallidos) {
        Pedido p = pedido(EstadoPedido.ENTREGADO, idRepartidor);
        p.setFechaEntrega(cuando);
        p.setIntentosFallidos(intentosFallidos);
        if (intentosFallidos > 0) {
            p.setMotivoEntregaFallida(MotivoEntregaFallida.CLIENTE_AUSENTE);
        }
        return p;
    }

    @Test
    void sinPedidosLosPorcentajesNoSeInventan() {
        IndicadoresDTO t = CalculoDeIndicadores.calcular(List.of(), LUNES, HASTA, 0);
        assertNull(t.getPorcentajeATiempo());
        assertNull(t.getPorcentajePrimerIntento());
        assertNull(t.horasPromedioEntrega);
        assertTrue(t.porRepartidor.isEmpty());
    }

    @Test
    void aTiempoEsElDiaPrometidoOAntes() {
        Pedido mismoDia = entregado(1L, LUNES.atTime(18, 0), 0);
        Pedido alOtroDia = entregado(1L, LUNES.plusDays(1).atTime(9, 0), 0);
        Pedido sinPromesa = entregado(1L, LUNES.atTime(12, 0), 0);
        sinPromesa.setFechaPrometida(null);

        IndicadoresDTO t = CalculoDeIndicadores.calcular(List.of(mismoDia, alOtroDia, sinPromesa), LUNES, HASTA, 0);

        assertEquals(3, t.entregados);
        assertEquals(2, t.entregadosConPromesa);
        assertEquals(1, t.entregadosATiempo);
        assertEquals(50, t.getPorcentajeATiempo());
    }

    @Test
    void elPrimerIntentoCuentaTambienLosDevueltosAlComercio() {
        Pedido alPrimero = entregado(1L, LUNES.atTime(18, 0), 0);
        Pedido alSegundo = entregado(1L, LUNES.plusDays(1).atTime(12, 0), 1);
        Pedido devuelto = pedido(EstadoPedido.DEVUELTO_AL_COMERCIO, 2L);
        devuelto.setIntentosFallidos(2);
        devuelto.setMotivoEntregaFallida(MotivoEntregaFallida.CLIENTE_AUSENTE);
        Pedido cancelado = pedido(EstadoPedido.CANCELADO, null);

        IndicadoresDTO t = CalculoDeIndicadores.calcular(List.of(alPrimero, alSegundo, devuelto, cancelado), LUNES, HASTA, 0);

        assertEquals(3, t.getTerminados());
        assertEquals(1, t.alPrimerIntento);
        assertEquals(33, t.getPorcentajePrimerIntento());
        assertEquals(1, t.devueltos);
        assertEquals(1, t.cancelados);
        assertEquals(1, t.motivosFallidos.size());
        assertEquals(2, t.motivosFallidos.get(0).cantidad);
    }

    @Test
    void lasEntregasSeCuentanPorDiaSegunLaPromesa() {
        Pedido mismoDia = entregado(1L, LUNES.atTime(18, 0), 0);
        Pedido alOtroDia = entregado(1L, LUNES.plusDays(1).atTime(9, 0), 0);
        Pedido sinPromesa = entregado(1L, LUNES.plusDays(1).atTime(12, 0), 0);
        sinPromesa.setFechaPrometida(null);

        IndicadoresDTO t = CalculoDeIndicadores.calcular(List.of(mismoDia, alOtroDia, sinPromesa), LUNES, HASTA, 0);

        assertEquals(5, t.porDia.size(), "un elemento por día del período, aunque no tenga entregas");
        assertEquals(1, t.porDia.get(0).aTiempo);
        assertEquals(1, t.porDia.get(1).fueraDeTermino);
        assertEquals(1, t.porDia.get(1).sinPromesa);
        assertEquals(0, t.porDia.get(4).aTiempo + t.porDia.get(4).fueraDeTermino + t.porDia.get(4).sinPromesa);
    }

    @Test
    void horasPromedioDeRecibidoAEntregado() {
        Pedido ocho = entregado(1L, LUNES.atTime(18, 0), 0);       // 8 h
        Pedido cuatro = entregado(1L, LUNES.atTime(14, 0), 0);     // 4 h
        IndicadoresDTO t = CalculoDeIndicadores.calcular(List.of(ocho, cuatro), LUNES, HASTA, 0);
        assertEquals(new BigDecimal("6.0"), t.horasPromedioEntrega);
    }

    @Test
    void porRepartidorElQueMasEntregoPrimero() {
        Pedido a1 = entregado(1L, LUNES.atTime(12, 0), 0);
        Pedido b1 = entregado(2L, LUNES.atTime(12, 0), 0);
        Pedido b2 = entregado(2L, LUNES.plusDays(2).atTime(12, 0), 1);
        Pedido enCurso = pedido(EstadoPedido.EN_CAMINO, 3L);

        IndicadoresDTO t = CalculoDeIndicadores.calcular(List.of(a1, b1, b2, enCurso), LUNES, HASTA, 4);

        assertEquals(2, t.porRepartidor.size(), "el que no terminó nada no aparece");
        IndicadoresDTO.PorRepartidor primero = t.porRepartidor.get(0);
        assertEquals(2L, primero.idRepartidor);
        assertEquals(2, primero.entregas);
        assertEquals(50, primero.getPorcentajeATiempo());
        assertEquals(50, primero.getPorcentajePrimerIntento());
        assertEquals(1, t.enCurso);
        assertEquals(4, t.enEsperaAhora);
    }
}
