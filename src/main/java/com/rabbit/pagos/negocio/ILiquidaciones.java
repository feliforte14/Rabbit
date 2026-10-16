package com.rabbit.pagos.negocio;

/**
 * CONTRATO de la liquidación mensual a comercios (ADR-034). La implementa
 * LiquidacionService.
 */

import com.rabbit.pagos.dto.LiquidacionDTO;
import jakarta.ejb.Local;
import java.time.YearMonth;
import java.util.List;

@Local
public interface ILiquidaciones {

    /**
     * Liquida un mes cerrado a cada comercio activo que no la tenga todavía,
     * y la cobra por el banco. Idempotente: correrlo dos veces no duplica
     * nada. Solo ADMINISTRADOR (o el timer mensual).
     *
     * @return las liquidaciones del período, nuevas y anteriores
     * @throws ValidacionException si el mes no terminó todavía
     */
    List<LiquidacionDTO> liquidarMes(YearMonth periodo);

    /**
     * Lo que se le cobraría a cada comercio activo por un mes, sin guardar ni
     * cobrar nada. Sirve para el mes en curso (todavía no se puede liquidar).
     */
    List<LiquidacionDTO> previsualizar(YearMonth periodo);

    /** Vuelve a pedirle al banco el cobro de una liquidación PENDIENTE o RECHAZADA. Solo ADMINISTRADOR. */
    LiquidacionDTO reintentarCobro(Long idLiquidacion);

    /** Reintenta las PENDIENTE (el banco no respondió). Lo usa el timer diario. */
    void reintentarPendientes();

    List<LiquidacionDTO> listarDelPeriodo(YearMonth periodo);

    /** Meses que ya tienen liquidaciones, del más nuevo al más viejo. */
    List<String> listarPeriodos();

    /** Las del comercio que representa el usuario que llama. Solo COMERCIO. */
    List<LiquidacionDTO> listarDelComercioActual();

    /**
     * Lo que lleva acumulado en el mes en curso el comercio que llama, sin
     * guardar nada: para que sepa cuánto se le va a cobrar. Solo COMERCIO.
     */
    LiquidacionDTO previsualizarMesEnCursoDelComercioActual();

    /** La tarifa vigente, para mostrarla. */
    TarifaMensual tarifaVigente();
}
