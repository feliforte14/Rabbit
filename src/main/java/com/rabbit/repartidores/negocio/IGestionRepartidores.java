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

    /** Cambia la zona donde reparte (null: sin zona fija). */
    void asignarZona(Long idRepartidor, Long idZona);
}
