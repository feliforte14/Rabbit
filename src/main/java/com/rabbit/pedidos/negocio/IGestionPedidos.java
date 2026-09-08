package com.rabbit.pedidos.negocio;

/**
 * CONTRATO DE ESCRITURA del componente ServicioDePedidos.
 *
 * Es la interfaz que materializa el patrón Facade del componente: agrupa
 * todo el ciclo de vida del pedido (sincronizar, confirmar, cancelar)
 * detrás de operaciones simples, sin que el cliente necesite conocer que
 * por detrás hay que validar el comercio (ServicioDeComercios) y
 * comprometer stock (ServicioDeInventario) — ver PedidoService.
 *
 * @Local la marca como interfaz de negocio local (misma JVM, sin red).
 * Implementada por {@link PedidoService}.
 */

import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import jakarta.ejb.Local;

@Local
public interface IGestionPedidos {

    /**
     * Simula la llegada de un pedido nuevo desde el ERP del comercio: da
     * de alta la fila mock (PedidoExterno), NO un Pedido — ese lo crea
     * recién SincronizadorDePedidos en su próxima pasada. Es el
     * reemplazo, para esta etapa, de lo que en producción sería el
     * webhook o polling contra el sistema del comercio (ver Sección 1.6).
     *
     * @param datos comercio, ítem y cantidad del pedido simulado
     * @return el ID de la fila mock creada
     * @throws ValidacionException si los datos son inválidos
     */
    Long registrarPedidoExterno(DatosPedidoExternoDTO datos);

    /**
     * Convierte una fila del mock del ERP (PedidoExterno) en un Pedido
     * real de Rabbit: valida que el comercio esté activo, reserva y
     * confirma el stock correspondiente, y marca la fila externa como
     * procesada. La invoca SincronizadorDePedidos en cada pasada — no es
     * un alta manual, ver Sección 1.1 y 5.3 del documento técnico.
     *
     * @param idPedidoExterno fila del mock a sincronizar
     * @return el ID del Pedido creado
     * @throws ValidacionException si la fila no existe, ya fue
     *         sincronizada, el comercio no está activo o no hay stock
     *         suficiente
     */
    Long sincronizarPedidoExterno(Long idPedidoExterno);

    /**
     * Avanza el pedido a CONFIRMADO. El stock ya se comprometió al
     * sincronizar (ver sincronizarPedidoExterno); esta operación refleja
     * el avance del pedido dentro de la orquestación de Rabbit, no un
     * nuevo compromiso de stock.
     *
     * @param idPedido ID del pedido a confirmar
     * @throws ValidacionException si el pedido no existe o no está PENDIENTE
     */
    void confirmarPedido(Long idPedido);

    /**
     * Cancela el pedido.
     *
     * LIMITACIÓN CONOCIDA DE ESTE ALCANCE: no revierte el stock ya
     * confirmado en ServicioDeInventario — esa operación (devolver stock
     * de un pedido cancelado) queda fuera del alcance de esta entrega,
     * documentada como pendiente en la Sección 5.3 del documento técnico.
     *
     * @param idPedido ID del pedido a cancelar
     * @throws ValidacionException si el pedido no existe o ya está CANCELADO
     */
    void cancelarPedido(Long idPedido);
}
