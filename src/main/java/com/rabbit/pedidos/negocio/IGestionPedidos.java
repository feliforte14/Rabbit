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
import com.rabbit.pedidos.dto.PedidoExternoDTO;
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
     * Si llama un ERP, el comercio es SIEMPRE el de su cuenta (el
     * idComercio de los datos se ignora). Con clave de idempotencia, un
     * reintento con la misma clave y el mismo pedido devuelve el pedido
     * externo ya creado, sin duplicarlo.
     *
     * @param datos comercio, origen y las líneas de producto+cantidad del pedido simulado
     * @param claveIdempotencia header Idempotency-Key de la API, o null (pantalla)
     * @return el ID de la fila mock creada (o la ya existente, si es un reintento)
     * @throws ValidacionException si los datos son inválidos
     * @throws ClaveIdempotenciaReutilizadaException si la clave ya se usó con otro pedido
     */
    Long registrarPedidoExterno(DatosPedidoExternoDTO datos, String claveIdempotencia);

    /**
     * El ERP cancela un pedido que mandó. Si todavía no se convirtió en
     * pedido, no se convierte; si ya es un pedido PENDIENTE, se cancela
     * devolviendo el stock. Una vez CONFIRMADO ya tiene cobro y repartidor:
     * eso lo cancela solo el personal de Rabbit (cancelarPedido).
     * Cancelar algo ya cancelado (o descartado) no hace nada: se puede
     * reintentar sin miedo.
     *
     * @param idPedidoExterno pedido externo del comercio del ERP que llama
     * @return cómo quedó el pedido externo
     * @throws PedidoNoEncontradoException si no existe o es de otro comercio
     * @throws CancelacionNoPermitidaException si el pedido ya avanzó demasiado
     */
    PedidoExternoDTO cancelarPedidoExterno(Long idPedidoExterno);

    /**
     * Convierte una fila del mock del ERP (PedidoExterno) en un Pedido
     * real de Rabbit: valida que el comercio esté activo y, para cada
     * línea del pedido, reserva y confirma el stock correspondiente (con
     * origen STOCK_CONSIGNADO) o valida el punto de picking (con
     * PUNTO_PICKING); marca la fila externa como procesada. La invoca
     * SincronizadorDePedidos en cada pasada — no es un alta manual, ver
     * Sección 1.1 y 5.3 del documento técnico.
     *
     * Corre en su PROPIA transacción (REQUIRES_NEW): si una fila falla, su
     * rollback no debe arrastrar al resto de la pasada del sincronizador
     * ni a la transacción del timer.
     *
     * @param idPedidoExterno fila del mock a sincronizar
     * @return el ID del Pedido creado
     * @throws ValidacionException si la fila no existe, el comercio no
     *         está activo o no hay stock suficiente
     * @throws PedidoYaSincronizadoException si la fila ya fue procesada
     *         (por el otro disparador o por una redelivery de JMS)
     */
    Long sincronizarPedidoExterno(Long idPedidoExterno);

    /**
     * Marca una fila del mock como procesada SIN generar Pedido, dejando
     * asentado por qué se descartó. La usa SincronizadorDePedidos cuando
     * la fila falla por una regla de negocio (comercio dado de baja, sin
     * stock, ítem inexistente): son fallas permanentes, y sin esto la fila
     * se reintentaría en cada pasada, para siempre.
     *
     * También corre en su propia transacción, porque se invoca justo
     * después de que la transacción de la sincronización hizo rollback.
     *
     * @param idPedidoExterno fila a descartar
     * @param motivo texto que se guarda y se muestra en la vista
     */
    void descartarPedidoExterno(Long idPedidoExterno, String motivo);

    /**
     * Avanza el pedido a CONFIRMADO en una sola transacción: registra el
     * cobro (IRegistroCobros; un PREPAGO se autoriza en el banco), asigna un
     * repartidor (IAsignacionRepartidores) y cambia el estado. Si cualquiera
     * de los pasos falla, no queda nada hecho en Rabbit, y si el banco ya
     * había cobrado, Pagos le pide la reversa. El stock ya se comprometió al
     * sincronizar (ver sincronizarPedidoExterno).
     *
     * @param idPedido ID del pedido a confirmar
     * @throws ValidacionException si el pedido no existe, no está
     *         PENDIENTE, no hay repartidores disponibles, el banco rechaza
     *         el pago o no responde
     */
    void confirmarPedido(Long idPedido);

    /**
     * Como confirmarPedido, pero prefiere un repartidor de la zona indicada
     * (si no hay ninguno libre ahí, toma cualquiera). Lo usa el despacho por
     * zona del componente Ruteo.
     */
    void confirmarPedidoEnZona(Long idPedido, Long idZona);

    /**
     * Alternativa a confirmarPedido cuando el pedido no lo lleva un
     * repartidor propio: cobra (si es PREPAGO), le pide el envío al
     * transportista elegido y pasa a CONFIRMADO, todo en una transacción.
     * Después el transportista lo lleva: el seguimiento lo pasa a EN_CAMINO
     * y ENTREGADO según lo que informe.
     *
     * @return el código de seguimiento que dio el transportista
     *
     * @throws ValidacionException si el pedido no está PENDIENTE, el banco
     *         rechaza el pago o no responde, o el transportista rechaza el
     *         envío, no responde o está dado de baja
     */
    String derivarATransportista(Long idPedido, Long idTransportista);

    /**
     * El repartidor retiró el pedido: CONFIRMADO → EN_CAMINO. Desde acá ya
     * no se puede cancelar (ver EstadoPedido).
     *
     * @param idPedido ID del pedido a despachar
     * @throws ValidacionException si el pedido no existe o no está CONFIRMADO
     */
    void despacharPedido(Long idPedido);

    /**
     * El repartidor entregó el pedido: EN_CAMINO → ENTREGADO, y queda libre
     * para otro pedido. Con pago CONTRA_ENTREGA, este cambio es el que
     * dispara el cobro (vía el tópico de estados, suscriptor de Pagos).
     *
     * @param idPedido ID del pedido entregado
     * @throws ValidacionException si el pedido no existe o no está EN_CAMINO
     */
    void registrarEntrega(Long idPedido);

    /**
     * Cancela el pedido y DEVUELVE el stock que había comprometido: por
     * cada línea con origen STOCK_CONSIGNADO llama a
     * IReservaStock.registrarDevolucion() sobre la reserva que quedó
     * CONFIRMADA al sincronizar, con lo que la cantidad vuelve al stock
     * disponible del ítem y la reserva pasa a DEVUELTA. Las líneas de
     * origen PUNTO_PICKING no reservaron nada y se ignoran.
     *
     * Si el pedido ya estaba CONFIRMADO, además anula su cobro y libera al
     * repartidor. Anular el cobro exige rol ADMINISTRADOR: un OPERADOR
     * recibe EJBAccessException y el pedido no se cancela.
     *
     * @param idPedido ID del pedido a cancelar
     * @throws ValidacionException si el pedido no existe, no está
     *         PENDIENTE ni CONFIRMADO, o no se pudo devolver el stock
     */
    void cancelarPedido(Long idPedido);
}
