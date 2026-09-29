package com.rabbit.pagos.negocio;

import com.rabbit.pagos.dto.CobroDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * CONTRATO DE LECTURA del componente ServicioDePagosYCobranzas.
 */
@Local
public interface IConsultaCobros {

    /**
     * @return el cobro del pedido, o null si todavía no tiene (un pedido
     *         recién se cobra al confirmarlo)
     */
    CobroDTO obtenerCobroDePedido(Long idPedido);

    List<CobroDTO> listarTodos();

    /** Cobros del comercio que representa el usuario que llama (rol COMERCIO). */
    List<CobroDTO> listarCobrosDelComercioActual();
}
