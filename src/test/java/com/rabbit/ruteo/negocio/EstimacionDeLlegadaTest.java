package com.rabbit.ruteo.negocio;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbit.ruteo.dto.LlegadaEstimadaDTO;
import com.rabbit.ruteo.dto.ParadaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** En qué parada está el pedido y en qué franja llega (ADR-043). */
class EstimacionDeLlegadaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 14, 7);

    @AfterEach
    void limpiar() {
        System.clearProperty(EstimacionDeLlegada.PROPIEDAD);
    }

    // Un retiro y tres entregas; el pedido 12 va con el 11 a la misma dirección.
    private static RecorridoDTO recorrido() {
        RecorridoDTO r = new RecorridoDTO();
        r.paradas.add(new ParadaDTO(ParadaDTO.Tipo.RETIRO, "Depósito", "Depósito", "1000", 13L));
        ParadaDTO compartida = new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "Callao 800", "Callao 800", "1023", 11L);
        compartida.pedidos.add(12L);
        r.paradas.add(compartida);
        r.paradas.add(new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "Corrientes 1234", "Corrientes 1234", "1043", 10L));
        r.paradas.add(new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "Cabildo 2000", "Cabildo 2000", "1428", 13L));
        return r;
    }

    @Test
    void cuentaLasParadasDeAntesIncluidosLosRetiros() {
        LlegadaEstimadaDTO llegada = EstimacionDeLlegada.estimar(recorrido(), 10L, AHORA, 20);
        assertEquals(3, llegada.numeroParada);
        assertEquals(4, llegada.totalParadas);
        assertEquals(2, llegada.getParadasAntes());
    }

    @Test
    void laFranjaSeRedondeaACuartosDeHora() {
        // 2 paradas antes x 20 min = 14:47 → 14:45; con 2 paradas más de margen = 15:27 → 15:30.
        LlegadaEstimadaDTO llegada = EstimacionDeLlegada.estimar(recorrido(), 10L, AHORA, 20);
        assertEquals(LocalTime.of(14, 45), llegada.desde);
        assertEquals(LocalTime.of(15, 30), llegada.hasta);
    }

    @Test
    void variosPedidosALaMismaDireccionSonLaMismaParada() {
        assertEquals(2, EstimacionDeLlegada.estimar(recorrido(), 11L, AHORA, 20).numeroParada);
        assertEquals(2, EstimacionDeLlegada.estimar(recorrido(), 12L, AHORA, 20).numeroParada);
    }

    @Test
    void seBuscaLaParadaDeEntregaNoLaDelRetiro() {
        // El 13 tiene un retiro primero, pero lo que le importa al cliente es su entrega.
        assertEquals(4, EstimacionDeLlegada.estimar(recorrido(), 13L, AHORA, 20).numeroParada);
    }

    @Test
    void laProximaParadaArrancaAhora() {
        RecorridoDTO r = new RecorridoDTO();
        r.paradas.add(new ParadaDTO(ParadaDTO.Tipo.ENTREGA, "Callao 800", "Callao 800", "1023", 11L));
        LlegadaEstimadaDTO llegada = EstimacionDeLlegada.estimar(r, 11L, LocalDateTime.of(2026, 10, 16, 14, 0), 20);
        assertTrue(llegada.isEsLaProxima());
        assertEquals(LocalTime.of(14, 0), llegada.desde);
        assertEquals(LocalTime.of(14, 45), llegada.hasta);
    }

    @Test
    void unPedidoQueNoEstaEnElRecorridoNoTieneEstimacion() {
        assertNull(EstimacionDeLlegada.estimar(recorrido(), 99L, AHORA, 20));
    }

    @Test
    void losMinutosPorParadaSeConfiguran() {
        assertEquals(20, EstimacionDeLlegada.minutosPorParada());
        System.setProperty(EstimacionDeLlegada.PROPIEDAD, "30");
        assertEquals(30, EstimacionDeLlegada.minutosPorParada());
        assertEquals(EstimacionDeLlegada.POR_DEFECTO, EstimacionDeLlegada.interpretar("1"));
        assertEquals(EstimacionDeLlegada.POR_DEFECTO, EstimacionDeLlegada.interpretar("veinte"));
    }
}
