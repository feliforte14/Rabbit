package com.rabbit.ruteo.negocio;

import com.rabbit.ruteo.dto.GrupoZonaDTO;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.ResultadoDespachoDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * CONTRATO del componente Ruteo. Implementada por {@link RuteoService}.
 *
 * Arma la hoja de ruta de cada pedido (retiro → entrega), agrupa los
 * pendientes por zona y los despacha según la cobertura de su zona:
 * repartidor propio de la zona, transportista de la zona o de respaldo.
 * No optimiza el orden de las paradas (no hay coordenadas; ver ADR-017).
 */
@Local
public interface IRuteo {

    /** Tablero del personal de Rabbit: pedidos CONFIRMADO o EN_CAMINO con su hoja de ruta. */
    List<HojaDeRutaDTO> listarEntregasEnCurso();

    /** La entrega que tiene a cargo el repartidor que llama, o null si no tiene ninguna. */
    HojaDeRutaDTO entregaActualDelRepartidor();

    /** Entregas terminadas (ENTREGADO o CANCELADO) del repartidor que llama. */
    List<HojaDeRutaDTO> historialDelRepartidor();

    /** Tablero de ruteo: pedidos PENDIENTE agrupados por zona (y los sin zona al final). */
    List<GrupoZonaDTO> listarPendientesPorZona();

    /**
     * Despacha un pedido PENDIENTE según su zona: con cobertura de
     * transportista lo deriva; con cobertura propia lo confirma con un
     * repartidor de la zona, y si no hay ninguno libre lo deriva al
     * transportista de respaldo (o, sin respaldo, usa un repartidor de otra
     * zona). Sin zona no hace nada. Nunca lanza: el resultado lo dice.
     */
    ResultadoDespachoDTO despacharPedido(Long idPedido);

    /** Despacha todos los pedidos pendientes de una zona, cada uno en su transacción. */
    List<ResultadoDespachoDTO> despacharZona(Long idZona);
}
