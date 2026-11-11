package com.rabbit.pagos.negocio;

/**
 * Lo que un comercio tuvo guardado en el depósito durante un mes, en unidades-día por tamaño, y de eso cuántas
 * fueron gratis (el producto había ingresado hace poco) y cuántas llevaron recargo (stock antiguo, sin movimiento
 * por mucho tiempo). Con esto TarifaMensual calcula el cargo por almacenamiento.
 *
 * Las unidades gratis y las con recargo son PARTE del total de cada tamaño, no se suman a él.
 *
 * Sin estado ni contenedor: se prueba con tests unitarios.
 */

import com.rabbit.inventario.dto.OcupacionMensualDTO;

public record AlmacenamientoDelMes(long pequeno, long mediano, long grande,
                                   long gratisPequeno, long gratisMediano, long gratisGrande,
                                   long recargoPequeno, long recargoMediano, long recargoGrande) {

    /** Sin días gratis ni recargo: lo de antes. */
    public static AlmacenamientoDelMes sinBeneficios(long pequeno, long mediano, long grande) {
        return new AlmacenamientoDelMes(pequeno, mediano, grande, 0, 0, 0, 0, 0, 0);
    }

    /** Lo que informó Inventario para el mes, o todo en cero si el comercio nunca tuvo stock. */
    public static AlmacenamientoDelMes de(OcupacionMensualDTO o) {
        if (o == null) {
            return sinBeneficios(0, 0, 0);
        }
        return new AlmacenamientoDelMes(o.pequeno, o.mediano, o.grande, o.gratisPequeno, o.gratisMediano,
                o.gratisGrande, o.recargoPequeno, o.recargoMediano, o.recargoGrande);
    }

    public long total() {
        return pequeno + mediano + grande;
    }

    public long totalGratis() {
        return gratisPequeno + gratisMediano + gratisGrande;
    }

    public long totalConRecargo() {
        return recargoPequeno + recargoMediano + recargoGrande;
    }
}
