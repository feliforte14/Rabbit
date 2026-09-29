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

    List<ZonaDTO> listarTodas();

    /** @return la zona activa que contiene el código postal, o null */
    ZonaDTO zonaDeCodigoPostal(String codigoPostal);
}
