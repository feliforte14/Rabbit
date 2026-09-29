package com.rabbit.transportistas.negocio;

import com.rabbit.transportistas.dto.DatosEnvioDTO;
import com.rabbit.transportistas.dto.EnvioDTO;
import jakarta.ejb.Local;
import java.util.List;

/**
 * CONTRATO del componente Transportistas para derivar pedidos. Lo usa
 * Pedidos (derivar y cancelar) y las vistas (consultar). Implementada por
 * {@link TransportistaService}.
 */
@Local
public interface IEnvios {

    /**
     * Le pide el envío al transportista y lo registra. Se suma a la
     * transacción del llamador: si después se deshace, el envío se cancela
     * en el transportista (compensación, ver CancelacionesDeEnvios).
     *
     * @throws ValidacionException si el transportista no existe, está de
     *         baja, rechaza el envío o no responde
     */
    EnvioDTO solicitarEnvio(Long idPedido, Long idComercio, Long idTransportista, DatosEnvioDTO datos);

    /** Cancela el envío del pedido, si tiene uno activo. Si no, no hace nada. */
    void cancelarEnvioDePedido(Long idPedido);

    /** Todos los envíos (personal de Rabbit). */
    List<EnvioDTO> listarEnvios();

    /** Los envíos del comercio que representa el usuario que llama (rol COMERCIO). */
    List<EnvioDTO> listarEnviosDelComercioActual();
}
