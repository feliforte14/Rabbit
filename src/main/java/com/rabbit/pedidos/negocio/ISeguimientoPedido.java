package com.rabbit.pedidos.negocio;

/**
 * CONTRATO DE SOLO LECTURA del componente ServicioDePedidos — contracara
 * de IGestionPedidos, mismo principio de segregación de interfaces que en
 * los demás componentes.
 *
 * Implementada por {@link PedidoService}.
 */

import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import jakarta.ejb.Local;
import java.util.List;

@Local
public interface ISeguimientoPedido {

    /**
     * @return todas las filas del mock del ERP, sincronizadas o no —
     *         pensado para que la vista muestre el "antes y después" de
     *         la sincronización.
     */
    List<PedidoExternoDTO> listarPedidosExternos();

    /**
     * @param idPedido identificador del pedido
     * @return el pedido como DTO
     * @throws ValidacionException si no existe
     */
    PedidoDTO consultarEstadoPedido(Long idPedido);

    /**
     * @return todos los pedidos, de todos los comercios
     */
    List<PedidoDTO> listarTodos();

    /**
     * @param idComercio comercio a consultar
     * @return los pedidos de ese comercio
     */
    List<PedidoDTO> listarPedidosDeComercio(Long idComercio);
}
