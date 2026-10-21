package com.rabbit.pedidos.negocio;

/**
 * CONTRATO DE SOLO LECTURA del componente ServicioDePedidos — contracara
 * de IGestionPedidos, mismo principio de segregación de interfaces que en
 * los demás componentes.
 *
 * Implementada por {@link PedidoService}.
 */

import com.rabbit.pedidos.dto.CalificacionDTO;
import com.rabbit.pedidos.dto.PedidoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.dto.ResumenEntregasDTO;
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
     * Un pedido externo puntual, con su resultado (Pendiente, Sincronizado
     * o Descartado) y, si ya se sincronizó, el ID del pedido real. Lo usa
     * la API REST para que el ERP siga el pedido que envió.
     *
     * @throws ValidacionException si no existe
     */
    PedidoExternoDTO consultarPedidoExterno(Long idPedidoExterno);

    /**
     * @param idPedido identificador del pedido
     * @return el pedido como DTO
     * @throws ValidacionException si no existe
     */
    PedidoDTO consultarEstadoPedido(Long idPedido);

    /**
     * Seguimiento público (sin login) por el código aleatorio del pedido.
     *
     * @throws PedidoNoEncontradoException si ningún pedido tiene ese código
     */
    PedidoDTO consultarSeguimiento(String codigoSeguimiento);

    /**
     * La calificación que dejó quien recibió el pedido (ADR-052), por el
     * código de seguimiento. Pública, igual que el seguimiento.
     *
     * @return la calificación, o null si todavía no calificó
     * @throws PedidoNoEncontradoException si ningún pedido tiene ese código
     */
    CalificacionDTO consultarCalificacion(String codigoSeguimiento);

    /**
     * Califica la entrega (ADR-052), desde el seguimiento público: 1 a 5
     * estrellas y un comentario opcional. Una sola vez por pedido y solo si
     * ya está ENTREGADO (ver ReglaDeCalificacion).
     *
     * @throws PedidoNoEncontradoException si ningún pedido tiene ese código
     * @throws ValidacionException si no se puede calificar, los datos no sirven o ya la calificaron
     */
    CalificacionDTO calificarEntrega(String codigoSeguimiento, int estrellas, String comentario);

    /**
     * @return todos los pedidos, de todos los comercios
     */
    List<PedidoDTO> listarTodos();

    /** Los pedidos PENDIENTE, del más viejo al más nuevo (los usa el Ruteo). */
    List<PedidoDTO> listarPendientes();

    /**
     * @param idComercio comercio a consultar
     * @return los pedidos de ese comercio
     */
    List<PedidoDTO> listarPedidosDeComercio(Long idComercio);

    /**
     * Pedidos entregados en [desde, hasta), por comercio: cantidad y kilos.
     * Para la liquidación mensual (ADR-034). Solo el personal.
     */
    List<ResumenEntregasDTO> resumenEntregas(java.time.LocalDateTime desde, java.time.LocalDateTime hasta);

    /**
     * Lo mismo, solo del comercio que representa el usuario que llama (la
     * vista previa de su liquidación). Solo COMERCIO: no ve lo de otros.
     */
    ResumenEntregasDTO resumenEntregasDelComercioActual(java.time.LocalDateTime desde, java.time.LocalDateTime hasta);

    /** Pedidos CONFIRMADO o EN_CAMINO: los que tienen que salir o están en viaje. */
    List<PedidoDTO> listarEntregasEnCurso();

    /** Pedidos del comercio que representa el usuario que llama (rol COMERCIO). */
    List<PedidoDTO> listarPedidosDelComercioActual();

    /** Pedidos que tuvo o tiene asignados el repartidor que llama (rol REPARTIDOR). */
    List<PedidoDTO> listarPedidosDelRepartidorActual();

    /**
     * Lo que un repartidor tiene en curso (CONFIRMADO o EN_CAMINO), en el
     * orden en que se le asignó: lo mismo que ve él en "Mis entregas". Solo
     * el personal (el seguimiento público entra con @RunAs, ADR-043).
     */
    List<PedidoDTO> listarEntregasEnCursoDeRepartidor(Long idRepartidor);
}
