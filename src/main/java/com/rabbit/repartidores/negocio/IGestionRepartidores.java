package com.rabbit.repartidores.negocio;

import com.rabbit.repartidores.dto.DatosRepartidorDTO;
import com.rabbit.repartidores.dto.RepartidorDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * Alta y consulta de repartidores, para la pantalla repartidores.xhtml.
 * La asignación a pedidos va por IAsignacionRepartidores, que es lo único
 * que Pedidos conoce de este componente.
 */
@Local
public interface IGestionRepartidores {

    /**
     * @return el ID del repartidor nuevo, que nace DISPONIBLE
     * @throws ValidacionException si falta el nombre
     */
    Long registrarRepartidor(DatosRepartidorDTO datos);

    List<RepartidorDTO> listarTodos();

    /** @return el repartidor, o null si no existe */
    RepartidorDTO obtenerRepartidor(Long idRepartidor);

    /**
     * Borra el repartidor definitivamente. Solo ADMINISTRADOR. No se puede
     * si tiene entregas en curso o una cuenta de usuario (ver
     * EliminacionDeRepartidor); sus pedidos ya terminados quedan sin
     * repartidor asignado.
     *
     * @throws ValidacionException si no existe o algo lo impide
     */
    void eliminarRepartidor(Long idRepartidor);

    /** Cambia la zona donde reparte (null: sin zona fija). */
    void asignarZona(Long idRepartidor, Long idZona);

    /**
     * Cambia lo que soporta su vehículo (ADR-032). Los dos null: un pedido
     * a la vez. No le quita los pedidos que ya lleva.
     *
     * @throws ValidacionException si el repartidor no existe o un valor no es positivo
     */
    void actualizarCapacidad(Long idRepartidor, java.math.BigDecimal capacidadKg, Integer capacidadBultos);

    /**
     * Guarda la ubicación del repartidor que llama (la manda su celular
     * mientras tiene entregas en curso, ADR-035). Solo REPARTIDOR.
     *
     * @throws ValidacionException si las coordenadas no son válidas o no tiene entregas en curso
     */
    void registrarUbicacion(double latitud, double longitud);

    /** Repartidores con entregas en curso y ubicación reciente, para el mapa del personal. */
    java.util.List<com.rabbit.repartidores.dto.UbicacionDTO> listarUbicacionesRecientes();

    /**
     * La ubicación reciente de un repartidor que tiene ese pedido en curso,
     * para el seguimiento del cliente. Null si no la hay (no la mandó hace
     * rato, o el pedido ya no es suyo).
     */
    com.rabbit.repartidores.dto.UbicacionDTO ubicacionParaPedido(Long idRepartidor, Long idPedido);
}
