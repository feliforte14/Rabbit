package com.rabbit.pagos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rabbit.inventario.dto.OcupacionMensualDTO;
import org.junit.jupiter.api.Test;

class AlmacenamientoDelMesTest {

    @Test
    void sinBeneficiosTieneSoloElTotal() {
        AlmacenamientoDelMes a = AlmacenamientoDelMes.sinBeneficios(1, 2, 3);
        assertEquals(6, a.total());
        assertEquals(0, a.totalGratis());
        assertEquals(0, a.totalConRecargo());
    }

    @Test
    void sinOcupacionTodoEsCero() {
        assertEquals(AlmacenamientoDelMes.sinBeneficios(0, 0, 0), AlmacenamientoDelMes.de(null));
    }

    @Test
    void tomaLosDatosDeLaOcupacionMensual() {
        OcupacionMensualDTO o = new OcupacionMensualDTO(100, 50, 10, 0, 10, 5, 1, 2, 20, 10);
        AlmacenamientoDelMes a = AlmacenamientoDelMes.de(o);
        assertEquals(160, a.total());
        assertEquals(16, a.totalGratis());
        assertEquals(32, a.totalConRecargo());
        assertEquals(20, a.recargoMediano());
    }
}
