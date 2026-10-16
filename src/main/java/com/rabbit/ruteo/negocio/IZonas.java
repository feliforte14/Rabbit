package com.rabbit.ruteo.negocio;

import com.rabbit.ruteo.dto.DatosZonaDTO;
import com.rabbit.ruteo.dto.ZonaDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * CONTRATO del componente Ruteo: las zonas de reparto. Implementada por
 * {@link ZonaService}. Solo el personal de Rabbit.
 */
@Local
public interface IZonas {

    Long registrarZona(DatosZonaDTO datos);

    void darDeBajaZona(Long idZona);

    void reactivarZona(Long idZona);

    /**
     * Borra la zona definitivamente. Solo ADMINISTRADOR. Los pedidos no
     * guardan su zona (se calcula del código postal), así que los
     * pendientes de ahí pasan a "sin zona"; los repartidores que tenían
     * esa zona quedan sin zona fija (ver ZonaEliminada).
     */
    void eliminarZona(Long idZona);

    List<ZonaDTO> listarTodas();

    /** @return la zona activa que contiene el código postal, o null */
    ZonaDTO zonaDeCodigoPostal(String codigoPostal);
}
