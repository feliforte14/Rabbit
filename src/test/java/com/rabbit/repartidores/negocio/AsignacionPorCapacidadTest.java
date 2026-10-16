package com.rabbit.repartidores.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rabbit.repartidores.dto.RepartidorDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsignacionPorCapacidadTest {

    private static RepartidorDTO repartidor(long id, String capacidadKg, String cargaKg) {
        RepartidorDTO r = new RepartidorDTO();
        r.id = id;
        r.capacidadKg = capacidadKg != null ? new BigDecimal(capacidadKg) : null;
        if (cargaKg != null) {
            r.sumar(100 + id, new BigDecimal(cargaKg), 1);
        }
        return r;
    }

    private static BigDecimal kg(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void siElPrimeroNoTieneLugarSeLoDaAOtroQueSi() {
        RepartidorDTO lleno = repartidor(1, "10", "9");
        RepartidorDTO conLugar = repartidor(2, "10", "2");
        assertEquals(2L, AsignacionPorCapacidad.elegir(List.of(lleno, conLugar), null, kg("5"), 1).getId());
    }

    @Test
    void respetaElOrdenDePreferenciaEntreLosQueTienenLugar() {
        RepartidorDTO deLaZona = repartidor(1, "10", null);
        RepartidorDTO deOtraZona = repartidor(2, "50", null);
        assertEquals(1L, AsignacionPorCapacidad.elegir(List.of(deLaZona, deOtraZona), null, kg("5"), 1).getId());
    }

    @Test
    void sinNadieConLugarNoHayElegidoYElPedidoVaALaListaDeEspera() {
        RepartidorDTO lleno = repartidor(1, "10", "9");
        RepartidorDTO ocupadoSinCapacidad = repartidor(2, null, "1");
        assertNull(AsignacionPorCapacidad.elegir(List.of(lleno, ocupadoSinCapacidad), null, kg("5"), 1));
        assertNull(AsignacionPorCapacidad.elegir(List.of(), null, kg("5"), 1));
    }

    @Test
    void elQueDevolvioElPedidoNoVuelveACandidatearse() {
        RepartidorDTO elQueLoDevolvio = repartidor(1, "10", null);
        RepartidorDTO otro = repartidor(2, "10", null);
        assertEquals(2L, AsignacionPorCapacidad.elegir(List.of(elQueLoDevolvio, otro), 1L, kg("5"), 1).getId());
        assertNull(AsignacionPorCapacidad.elegir(List.of(elQueLoDevolvio), 1L, kg("5"), 1));
    }

    @Test
    void alLiberarseLugarElPedidoQueEsperabaEntra() {
        RepartidorDTO r = repartidor(1, "10", "9");
        assertNull(AsignacionPorCapacidad.elegir(List.of(r), null, kg("5"), 1));
        RepartidorDTO liberado = repartidor(1, "10", "4");
        assertEquals(1L, AsignacionPorCapacidad.elegir(List.of(liberado), null, kg("5"), 1).getId());
    }
}
