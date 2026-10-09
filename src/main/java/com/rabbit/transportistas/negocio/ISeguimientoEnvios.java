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
     * @throws NovedadRechazadaException (PEDIDO_EN_CONFLICTO) si el pedido o
     *         el envío cambiaron al mismo tiempo y la novedad no se aplicó:
     *         se deshace todo y se reintenta en la próxima pasada
     */
    void registrarNovedad(Long idEnvio, EstadoEnvio nuevo);

    /**
     * Webhook: un transportista avisa que su envío cambió de estado, en vez
     * de esperar a que Rabbit le pregunte. Mismo efecto que el polling
     * (registrarNovedad). Repetir un aviso ya aplicado no cambia nada; si
     * el mismo aviso llega por los dos caminos exactamente a la vez, el
     * segundo puede recibir PEDIDO_EN_CONFLICTO y, al reintentar, lo
     * encuentra aplicado.
     *
     * @param estado en el vocabulario del transportista REST: SOLICITADO,
     *               EN_TRANSITO, ENTREGADO o CANCELADO
     * @return true si el envío cambió de estado
     * @throws NovedadRechazadaException si la clave no corresponde, el
     *         envío no es de ese transportista, el estado no se conoce, o
     *         el pedido/envío cambió al mismo tiempo (PEDIDO_EN_CONFLICTO,
     *         reintentar)
     */
    boolean recibirNovedad(Long idTransportista, String clave, String codigoSeguimiento, String estado);
}
