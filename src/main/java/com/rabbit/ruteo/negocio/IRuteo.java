package com.rabbit.ruteo.negocio;

import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * CONTRATO del componente Ruteo. Implementada por {@link RuteoService}.
 *
 * Alcance mínimo: arma la hoja de ruta de cada pedido (retiro → entrega).
 * No optimiza recorridos ni agrupa pedidos por zona.
 */
@Local
public interface IRuteo {

    /** Tablero del personal de Rabbit: pedidos CONFIRMADO o EN_CAMINO con su hoja de ruta. */
    List<HojaDeRutaDTO> listarEntregasEnCurso();

    /** La entrega que tiene a cargo el repartidor que llama, o null si no tiene ninguna. */
    HojaDeRutaDTO entregaActualDelRepartidor();

    /** Entregas terminadas (ENTREGADO o CANCELADO) del repartidor que llama. */
    List<HojaDeRutaDTO> historialDelRepartidor();
}
