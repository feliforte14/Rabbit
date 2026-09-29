package com.rabbit.transportistas.negocio;

import com.rabbit.transportistas.dto.DatosTransportistaDTO;
import com.rabbit.transportistas.dto.TransportistaDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * CONTRATO del componente Transportistas: alta y consulta de las empresas
 * de envío. Implementada por {@link TransportistaService}. Solo el personal
 * de Rabbit.
 */
@Local
public interface IGestionTransportistas {

    Long registrarTransportista(DatosTransportistaDTO datos);

    void darDeBajaTransportista(Long idTransportista);

    void reactivarTransportista(Long idTransportista);

    List<TransportistaDTO> listarTodos();

    /** Los que pueden recibir envíos nuevos. */
    List<TransportistaDTO> listarActivos();
}
