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

import com.rabbit.pedidos.datos.model.MotivoEntregaFallida;
import com.rabbit.pedidos.dto.DatosPedidoExternoDTO;
import com.rabbit.pedidos.dto.PedidoExternoDTO;
import com.rabbit.pedidos.dto.RemitoDTO;
import com.rabbit.transportistas.dto.CotizacionDTO;
import java.util.List;
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
     * Si ningún repartidor tiene lugar, o ya pasó la hora de corte de la
     * hoja de ruta, no se cobra nada: el pedido sigue PENDIENTE en la lista
     * de espera y se asigna solo cuando se pueda (ADR-037).
     *
     * @param idPedido ID del pedido a confirmar
     * @return el ID del repartidor asignado, o null si quedó en espera
     * @throws ValidacionException si el pedido no existe, no está
     *         PENDIENTE, el banco rechaza el pago o no responde
     */
    Long confirmarPedido(Long idPedido);

    /**
     * Como confirmarPedido, pero prefiere un repartidor de la zona indicada
     * (si no hay ninguno libre ahí, toma cualquiera). Lo usa el despacho por
     * zona del componente Ruteo.
     *
     * @return el ID del repartidor asignado, o null si quedó en espera
     */
    Long confirmarPedidoEnZona(Long idPedido, Long idZona);

    /**
     * Intenta asignar un pedido de la lista de espera (lo llama
     * AsignadorDeEspera, en su propia transacción). Si sigue sin haber
     * lugar, lo deja esperando sin cambios.
     *
     * @return el ID del repartidor asignado, o null si sigue en espera
     * @throws ValidacionException si falla por otra cosa (por ejemplo, el
     *         banco rechaza el pago): ahí conviene sacarlo de la espera
     */
    Long asignarDesdeEspera(Long idPedido);

    /**
     * Saca un pedido de la lista de espera porque no se pudo asignar solo
     * (el motivo queda a la vista del personal). Sigue PENDIENTE.
     */
    void sacarDeEspera(Long idPedido, String motivo);

    /**
     * El repartidor no pudo entregar el pedido (cliente ausente, dirección
     * incorrecta, no entra en el vehículo...) y lo devuelve al depósito:
     * CONFIRMADO o EN_CAMINO → PENDIENTE, deja de llevarlo y el pedido
     * entra en la lista de espera para otro repartidor (ADR-037). Conserva
     * su cobro: al reasignarlo no se cobra de nuevo.
     *
     * Si estaba EN_CAMINO y el motivo implica haber ido a la dirección,
     * cuenta como un intento de entrega. Al agotar los intentos
     * (IntentosDeEntrega), o si el cliente lo rechazó, pasa a
     * DEVUELTO_AL_COMERCIO: el stock consignado vuelve al comercio y Pagos
     * anula el cobro (ADR-041).
     *
     * Un paquete dañado vuelve al comercio sin reintentar, aunque no haya
     * salido, y sin devolver el stock (ADR-045).
     *
     * Cada vez queda registrada la visita con su evidencia (ADR-045):
     * motivo, comentario, hasta 3 fotos y dónde estaba el repartidor (ver
     * EvidenciaDeVisita).
     *
     * Solo el REPARTIDOR asignado o un ADMINISTRADOR.
     *
     * @param idPedido pedido a devolver
     * @param datos motivo (obligatorio), comentario, fotos y ubicación
     * @throws ValidacionException si no es suyo, no está en reparto, lo
     *         lleva un transportista, falta el motivo o la evidencia que
     *         pide ese motivo, o una foto no es válida
     */
    void devolverADeposito(Long idPedido, com.rabbit.pedidos.dto.EntregaFallidaDTO datos);

    /**
     * Las visitas fallidas de un pedido con su evidencia (ADR-045), de la
     * más vieja a la más nueva. El personal ve las de cualquier pedido; un
     * COMERCIO, las de los suyos.
     *
     * @throws PedidoNoEncontradoException si no existe o no lo puede ver
     */
    java.util.List<com.rabbit.pedidos.dto.IntentoEntregaDTO> listarIntentosDeEntrega(Long idPedido);

    /**
     * Una foto de una visita fallida (ADR-045), con el mismo criterio que
     * listarIntentosDeEntrega.
     *
     * @throws PedidoNoEncontradoException si no existe o no la puede ver
     */
    com.rabbit.pedidos.dto.FotoDeVisitaDTO obtenerFotoDeVisita(Long idFoto);

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
     * Cotiza la derivación de un pedido PENDIENTE con cada transportista
     * activo, para elegir antes de derivar. No cobra ni pide ningún envío.
     *
     * @throws ValidacionException si el pedido no existe o ya no está PENDIENTE
     */
    List<CotizacionDTO> cotizarDerivacion(Long idPedido);

    /**
     * El repartidor retiró el pedido: CONFIRMADO → EN_CAMINO. Desde acá ya
     * no se puede cancelar (ver EstadoPedido). Lo puede marcar el
     * repartidor asignado o el personal (por ejemplo, al despachar desde el
     * depósito): no mueve plata, así que no pide prueba (ADR-033).
     *
     * @param idPedido ID del pedido a despachar
     * @throws ValidacionException si el pedido no existe o no está CONFIRMADO
     */
    void despacharPedido(Long idPedido);

    /**
     * El repartidor entregó el pedido: EN_CAMINO → ENTREGADO, con la foto
     * del remito firmado como prueba (ADR-033), y deja de llevarlo. Con pago
     * CONTRA_ENTREGA, este cambio es el que dispara el cobro (vía el tópico
     * de estados, suscriptor de Pagos).
     *
     * Solo el REPARTIDOR asignado, o un ADMINISTRADOR como contingencia
     * (por ejemplo, el repartidor se quedó sin batería): un OPERADOR no.
     *
     * @param idPedido ID del pedido entregado
     * @param imagen foto del remito: JPEG, PNG o WEBP, hasta 5 MB
     * @throws ValidacionException si el pedido no existe, no está EN_CAMINO,
     *         no es del repartidor, o falta la foto o no es una imagen válida
     */
    void registrarEntrega(Long idPedido, byte[] imagen);

    /**
     * ENTREGADO informado por el transportista externo de un pedido
     * derivado (ver ActualizacionDePedidosPorEnvio): la prueba de entrega
     * es la del transportista, no hay remito de Rabbit. Solo para pedidos
     * sin repartidor propio.
     *
     * @throws ValidacionException si el pedido tiene repartidor de Rabbit o no está EN_CAMINO
     */
    void registrarEntregaDeTransportista(Long idPedido);

    /**
     * Entrega un pedido de retiro en depósito (ADR-053): quien retira muestra el código de
     * seguimiento y el personal lo ingresa. Solo con el pedido CONFIRMADO ("listo para retirar").
     *
     * @throws ValidacionException si el pedido no es de retiro, no está listo o el código no coincide
     */
    void entregarEnDeposito(Long idPedido, String codigoMostrado);

    /**
     * La foto del remito de un pedido. El personal ve cualquiera; un
     * COMERCIO, las de sus pedidos; un REPARTIDOR, las de los suyos.
     *
     * @throws PedidoNoEncontradoException si no existe, no tiene remito o
     *         quien llama no lo puede ver (no se distingue: no revela nada)
     */
    RemitoDTO obtenerRemito(Long idPedido);

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
