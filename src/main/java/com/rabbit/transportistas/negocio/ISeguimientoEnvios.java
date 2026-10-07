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
     * en esa misma transacción).
     */
    void registrarNovedad(Long idEnvio, EstadoEnvio nuevo);

    /**
     * Webhook: un transportista avisa que su envío cambió de estado, en vez
     * de esperar a que Rabbit le pregunte. Mismo efecto que el polling
     * (registrarNovedad). Repetir el mismo aviso no cambia nada.
     *
     * @param estado en el vocabulario del transportista REST: SOLICITADO,
     *               EN_TRANSITO, ENTREGADO o CANCELADO
     * @return true si el envío cambió de estado
     * @throws NovedadRechazadaException si la clave no corresponde, el
     *         envío no es de ese transportista o el estado no se conoce
     */
    boolean recibirNovedad(Long idTransportista, String clave, String codigoSeguimiento, String estado);
}
