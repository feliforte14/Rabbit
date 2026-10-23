package com.rabbit.inventario.negocio;

import com.rabbit.inventario.dto.OcupacionMensualDTO;
import jakarta.ejb.Local;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * La ocupación diaria del depósito (ADR-059): una foto por día de cuántas unidades de cada categoría de
 * tamaño guarda cada comercio, y las unidades-día de un mes, que es lo que se le cobra.
 */
@Local
public interface IOcupacionDeDeposito {

    /**
     * Guarda la foto de un día (la reemplaza si ya existía). La saca solo un timer a las 23:55; el administrador
     * puede sacar la de hoy a mano.
     */
    void tomarFoto(LocalDate dia);

    /** Las unidades-día de cada comercio con stock, en un mes. Solo el personal (la usa la liquidación). */
    Map<Long, OcupacionMensualDTO> ocupacionDelMes(YearMonth mes);

    /** Las unidades-día del comercio que llama, en un mes (vista previa del mes en curso). */
    OcupacionMensualDTO ocupacionDelMesDelComercioActual(YearMonth mes);

    /** El día de la última foto guardada, o null si todavía no hay ninguna. */
    LocalDate fechaDeLaUltimaFoto();
}
