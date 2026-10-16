package com.rabbit.ruteo.negocio;

import com.rabbit.ruteo.dto.GrupoZonaDTO;
import com.rabbit.ruteo.dto.HojaDeRutaDTO;
import com.rabbit.ruteo.dto.RecorridoDTO;
import com.rabbit.ruteo.dto.ResultadoDespachoDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * CONTRATO del componente Ruteo. Implementada por {@link RuteoService}.
 *
 * Arma la hoja de ruta de cada pedido (retiro → entrega), agrupa los
 * pendientes por zona y los despacha según la cobertura de su zona:
 * repartidor propio de la zona, transportista de la zona o de respaldo.
 * El orden recomendado de las paradas es aproximado, por código postal:
 * no hay coordenadas (ver ADR-017 y ADR-038).
 */
@Local
public interface IRuteo {

    /** Tablero del personal de Rabbit: pedidos CONFIRMADO o EN_CAMINO con su hoja de ruta. */
    List<HojaDeRutaDTO> listarEntregasEnCurso();

    /**
     * Las entregas que tiene a cargo el repartidor que llama (CONFIRMADO o
     * EN_CAMINO), en el orden en que se le asignaron. Con capacidad en el
     * vehículo pueden ser varias (ADR-032); vacía si no tiene ninguna.
     */
    List<HojaDeRutaDTO> entregasEnCursoDelRepartidor();

    /**
     * La hoja de ruta recomendada del repartidor: los retiros que le faltan
     * y después las entregas, encadenadas por cercanía de código postal,
     * con el recorrido en Google Maps en tramos de hasta 10 puntos
     * (ADR-038).
     *
     * @param entregasEnCurso lo que devolvió entregasEnCursoDelRepartidor
     */
    RecorridoDTO recorridoRecomendado(List<HojaDeRutaDTO> entregasEnCurso);

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
