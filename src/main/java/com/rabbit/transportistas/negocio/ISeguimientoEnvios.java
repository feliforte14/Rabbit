package com.rabbit.transportistas.negocio;

import com.rabbit.transportistas.datos.model.EstadoEnvio;
import com.rabbit.transportistas.dto.EnvioDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * CONTRATO interno del componente: lo usa SeguimientoDeEnvios (el timer
 * que consulta a los transportistas). Implementada por
 * {@link TransportistaService}.
 */
@Local
public interface ISeguimientoEnvios {

    /** Envíos SOLICITADO o EN_TRANSITO: los que todavía pueden cambiar. */
    List<EnvioDTO> listarEnviosActivos();

    /**
     * Registra el nuevo estado que informó el transportista, en su propia
     * transacción, y avisa con EstadoEnvioCambiado (Pedidos mueve el pedido
     * en esa misma transacción, antes de que se escriba el envío).
     *
     * @throws NovedadRechazadaException si el pedido o
     *         el envío cambiaron al mismo tiempo y la novedad no se aplicó:
     *         se deshace todo y se reintenta en la próxima pasada
     */
    void registrarNovedad(Long idEnvio, EstadoEnvio nuevo);
}
