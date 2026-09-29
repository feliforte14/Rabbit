# Registro de decisiones (ADRs)

Formato: contexto → decisión → consecuencias. Estado: Aceptada,
Propuesta o Reemplazada.

## ADR-001: Cola JMS + polling de respaldo para sincronizar pedidos

- **Estado:** Aceptada.
- **Contexto:** los pedidos externos se sincronizaban solo por polling
  (hasta 1 minuto de demora). Pasar a mensajes agrega el riesgo de perder
  alguno.
- **Decisión:** la cola es el camino principal; `SincronizadorDePedidos`
  sigue como red de contención. La concurrencia entre los dos se resuelve
  con `PESSIMISTIC_WRITE` y el flag `sincronizado`.
- **Consecuencias:** latencia casi nula en el caso normal y ningún pedido
  huérfano si falla el broker. A cambio hay dos disparadores, y por eso
  `sincronizarPedidoExterno` tiene que ser idempotente.

## ADR-002: El envío del mensaje no comparte transacción con el guardado

- **Estado:** Aceptada.
- **Contexto:** si el `send` JMS se suma a la transacción del INSERT, una
  falla del broker deshace un pedido válido.
- **Decisión:** evento CDI observado con `AFTER_SUCCESS` +
  `@TransactionAttribute(NOT_SUPPORTED)` en el publicador.
- **Consecuencias:** el mensaje sale solo si el pedido existe, y una
  falla del broker no afecta al pedido. Existe una ventana donde el pedido
  se guardó pero el mensaje no salió; la cubre el polling (ADR-001). Se
  descartó XA/outbox por complejidad.

## ADR-003: Timeout del padrón fiscal no bloquea el alta

- **Estado:** Reemplazada: se quitó la integración SOAP con el padrón
  fiscal (validar un CUIT era demasiado simple para justificarla). La
  reemplaza la integración con el banco legado (ADR-010).
- **Contexto:** el padrón es un sistema ajeno; puede no responder.
- **Decisión:** timeout de 5 s; si vence, se guarda el comercio con
  `cuitValidado = false`. Solo el Fault `CuitInexistente` bloquea el alta.
- **Consecuencias:** disponibilidad de Rabbit independiente del legado. Quedan
  comercios sin CUIT confirmado; una revalidación periódica queda
  propuesta para la Entrega Final (resiliencia).

## ADR-004: Importe y medio de pago vienen del ERP

- **Estado:** Aceptada.
- **Contexto:** para cobrar hace falta un importe, pero Rabbit no puede
  calcularlo: `ItemInventario` no referencia a `Producto` (que tiene
  precio), y lo que se retira de un punto de picking es texto libre.
- **Decisión:** el ERP del comercio manda `importe` y `medioPago` en el
  pedido externo; se copian al `Pedido` al sincronizar.
- **Consecuencias:** Rabbit cobra lo que el comercio vendió, sin mantener
  precios propios. Las columnas son nullable: los pedidos anteriores
  quedan sin importe.

## ADR-005: Máquina de estados del pedido en el enum

- **Estado:** Aceptada.
- **Contexto:** se agregan `EN_CAMINO` y `ENTREGADO`; las reglas de
  transición estaban repartidas en cada operación.
- **Decisión:** `EstadoPedido.puedePasarA` + un único
  `PedidoService.cambiarEstado`. No se usa el patrón State completo.
- **Consecuencias:** una sola regla para validar y para disparar el
  evento de cambio de estado. `cancelarPedido` deja de aceptar pedidos
  `EN_CAMINO` o `ENTREGADO`.
- **Nota de despliegue:** Hibernate crea un `CHECK` con los valores del
  enum que `hbm2ddl=update` no regenera; ver README (sección "Cómo
  levantar el sistema").

## ADR-006: Tópico para los cambios de estado del pedido

- **Estado:** Aceptada (implementada).
- **Contexto:** Notificaciones y Pagos necesitan enterarse de los cambios
  de estado, cada uno por su motivo.
- **Decisión:** tópico `topico.pedidos.estado` con el formato de
  `EstadoPedidoCambiado`; orden resuelto por `fechaCambio` en cada
  suscriptor.
- **Consecuencias:** Pedidos no conoce a sus suscriptores. Cada suscriptor
  tiene que ser idempotente y tolerar desorden.

## ADR-007: Endpoint REST de entrada reemplaza al formulario del ERP

- **Estado:** Aceptada (implementada). El partner se autentica con HTTP
  Basic y rol `ERP`; el formulario queda para la demo.
- **Contexto:** el formulario JSF no marca una frontera real entre el ERP
  y Rabbit.
- **Decisión:** `POST /api/pedidos-externos` llama al mismo
  `registrarPedidoExterno`.
- **Consecuencias:** cola, polling y sincronización no cambian. Hay que
  definir cómo se autentica el partner.

## ADR-008: Restricciones CHECK de columnas enum alineadas al desplegar

- **Estado:** Aceptada.
- **Contexto:** Hibernate crea cada columna `@Enumerated(STRING)` con un
  `CHECK` que lista los valores del enum al momento de crear la tabla, y
  `hbm2ddl=update` nunca lo actualiza. Al sumar `EN_CAMINO` y `ENTREGADO`
  a `EstadoPedido`, despachar un pedido fallaba con `violates check
  constraint pedidos_estado_check`. Hibernate 7 no permite desactivar ese
  `CHECK` (`columnDefinition`, `AttributeConverter` y `@JdbcTypeCode` lo
  generan igual).
- **Decisión:** `AlineadorDeRestriccionesEnum` (`@Singleton @Startup`)
  reemplaza al desplegar el `CHECK` de cada columna enum por uno con los
  valores actuales del enum. Por el mismo motivo, las columnas nuevas
  `NOT NULL` llevan `default`.
- **Consecuencias:** agregar un valor a un enum ya no rompe la base y se
  conserva la validación en PostgreSQL. Cada columna enum nueva hay que
  sumarla a la lista del alineador. Es un parche del alcance del TP: lo
  correcto sería reemplazar `hbm2ddl=update` por migraciones versionadas
  (Flyway/Liquibase).

## ADR-009: Suscripciones durables y selector en el tópico de estados

- **Estado:** Aceptada.
- **Contexto:** si un suscriptor no está activo cuando se publica un
  cambio de estado (por ejemplo durante un redeploy), una suscripción
  común pierde el mensaje. Para Pagos eso es un cobro contra entrega que
  nunca se acredita. Además, Pagos solo necesita `ENTREGADO`.
- **Decisión:** las dos suscripciones son durables (`clientId` +
  `subscriptionName`, con `shareSubscriptions` para el pool del MDB).
  Pagos usa `messageSelector = "estado = 'ENTREGADO'"` sobre una propiedad
  JMS del mensaje, así el broker no le entrega el resto.
- **Consecuencias:** ningún cambio de estado se pierde por un suscriptor
  caído (probado con `stop-delivery`/`start-delivery`). A cambio, el
  broker guarda mensajes por cada suscripción hasta que se consumen, y
  cambiar `clientId` o `subscriptionName` crea una suscripción nueva.
  Si falla la publicación misma, el aviso se pierde: no hay polling de
  respaldo como en la cola (ADR-001).

## ADR-010: Cobro por SOAP en un banco legado, con reversa compensatoria

- **Estado:** Aceptada.
- **Contexto:** la integración sincrónica con un sistema legado era una
  validación de CUIT de sí/no, que no modificaba nada en el otro sistema.
  Cobrar sí lo modifica: una vez que el banco cobró, un rollback de
  Rabbit no lo deshace.
- **Decisión:** `PagoService` cobra los PREPAGO llamando por SOAP a un
  banco legado (`autorizarPago`). Cada autorización dispara
  `PagoAutorizado`; si la transacción de confirmación se deshace,
  `ReversasBancarias` (`AFTER_FAILURE`) pide `reversarPago`. Al anular un
  cobro, la reversa se pide recién después del commit (`AFTER_SUCCESS`).
  En `confirmarPedido` se cobra antes de asignar el repartidor.
- **Consecuencias:** el cliente nunca queda cobrado por un pedido que no
  se confirmó (salvo que falle la reversa, que se loguea). Sin respuesta
  del banco el pedido no se confirma. Se descartaron la clave de
  idempotencia y la consulta de estado tras un timeout, por simplicidad;
  quedan como limitación conocida.

## ADR-011: Circuit breaker propio frente al banco legado

- **Estado:** Aceptada.
- **Contexto:** con el banco caído o colgado, cada confirmación PREPAGO
  bloquea un hilo durante los 5 s del timeout para terminar sin
  confirmar. Con carga, la caída del banco agota el pool de WildFly y
  arrastra al resto de la plataforma.
- **Decisión:** `BancoClient` consulta a `CircuitBreakerBanco`
  (`@Singleton`, estado en memoria) antes de cada llamada. Tras 3 fallas
  seguidas (sin respuesta, no rechazos) se abre y se contesta
  `NO_DISPONIBLE` al instante; a los 30 s pasa a semiabierto y deja
  pasar una llamada de prueba. Implementado a mano en vez de con
  MicroProfile Fault Tolerance, que no viene en `standalone-full`.
- **Consecuencias:** con el banco caído, los pedidos PREPAGO fallan en
  milisegundos y el resto de Rabbit no se degrada. Durante los 30 s de
  espera se rechazan confirmaciones aunque el banco ya haya vuelto. Las
  reversas con el circuito abierto no se intentan y quedan para hacer a
  mano (mismo límite que ADR-010). En un cluster cada nodo tiene su
  propio circuito.

## ADR-012: Sin alta pública de usuarios

- **Estado:** Aceptada.
- **Contexto:** `usuarios.xhtml` era pública: cualquiera podía crearse un
  `OPERADOR`, y como casi todas las operaciones de negocio son
  `@PermitAll`, eso equivalía a operar el sistema sin autorización.
  Además, mientras no hubiera un administrador activo, el alta pública
  permitía crear uno, y esa situación podía repetirse si el último
  administrador se daba de baja.
- **Decisión:** `registrarUsuario` exige `ADMINISTRADOR` y la pantalla de
  usuarios es solo para ese rol. El primer administrador se crea en el
  servidor con `add-user.sh`. Un administrador no puede darse de baja a sí
  mismo ni dar de baja al último administrador activo. El username se
  valida con una lista blanca, porque termina escrito en los archivos del
  realm.
- **Consecuencias:** nadie obtiene acceso sin que un administrador lo
  habilite. El arranque de una instalación nueva requiere acceso al
  servidor (ya era así para el usuario del ERP).

## ADR-013: Vistas por tipo de usuario y ruteo mínimo

- **Estado:** Aceptada.
- **Contexto:** Rabbit conecta comercios con depósitos y repartidores,
  pero solo el personal de Rabbit tenía acceso: el comercio no podía
  seguir sus pedidos ni el repartidor ver adónde ir. Los pedidos tampoco
  tenían dirección de entrega, así que no había recorrido que mostrar.
- **Decisión:** dos tipos de cuenta nuevos, `COMERCIO` y `REPARTIDOR`,
  asociados en la tabla `usuarios` a su comercio o repartidor. Cada uno
  tiene su menú y sus pantallas. El comercio o el repartidor sale de la
  identidad autenticada (`IContextoUsuario`), nunca de un parámetro. El
  pedido suma `direccionEntrega` (la manda el ERP) y un componente Ruteo
  mínimo arma la hoja de ruta (retiro → entrega) para el tablero del
  personal y para el repartidor.
- **Consecuencias:** cada actor ve y mueve solo lo suyo, controlado en el
  EJB y no solo en la vista. El ERP tiene que mandar la dirección de
  entrega (la API responde `400` sin ella). Los pedidos anteriores quedan
  sin dirección. El ruteo no optimiza recorridos ni agrupa pedidos: queda
  para la Entrega Final.

## ADR-014: Escalar el consumidor de la cola con consumidores competidores

- **Estado:** Aceptada.
- **Contexto:** un comercio que manda muchos pedidos juntos llena
  `cola.pedidos.externos` y los pedidos tardan en aparecer. Hacía falta
  poder escalar ese consumidor sin tocar código (desafío de
  escalabilidad, ver [DESAFIOS-OPCIONALES.md](DESAFIOS-OPCIONALES.md)).
- **Decisión:** la cantidad de instancias del consumidor (`maxSession` de
  `PedidoExternoListener`) se fija con la system property
  `rabbit.cola.consumidores`, aplicada en `WEB-INF/jboss-ejb3.xml` (15 por
  defecto). Varias instancias compiten por la misma cola y el broker le da
  cada mensaje a una sola. Se sumó `rabbit.sincronizador.pausado` para
  poder medir sin que el timer de respaldo procese la carga.
- **Alternativas descartadas:** escalar con varios servidores (requiere un
  broker compartido en lugar del embebido); particionar la cola por
  comercio (agrega colas y configuración sin ganar nada con un solo
  consumidor lógico); procesar en lotes dentro de un consumidor (una falla
  de un pedido afectaría al lote).
- **Consecuencias:** escalar es cambiar una propiedad y redesplegar.
  Medido: 100 pedidos pasan de 43,2 s con un consumidor a 11,3 s con 4
  (x3,8) y 6,8 s con 8 (x6,3). El techo lo pone la base: el pooler de Supabase admite 15 conexiones por
  proyecto, así que el pool de WildFly se limita a 10 y más consumidores
  que conexiones no mejoran nada.

## ADR-015: Banco legado en Node.js, detrás del mismo WSDL

- **Estado:** Aceptada.
- **Contexto:** el banco legado es un sistema externo, pero estaba
  implementado en Java dentro del mismo WAR que Rabbit. El desafío de
  heterogeneidad pide integrar componentes de tecnologías distintas.
- **Decisión:** el banco se implementa también como un servicio aparte en
  Node.js (`banco-legado/`, librería `soap`), construido a partir del mismo
  WSDL. Rabbit elige a cuál llamar con `rabbit.banco.wsdl`; el banco en
  Java sigue siendo el de por defecto, para que levantar Rabbit no exija
  Node.
- **Alternativas descartadas:** reemplazar el banco en Java (obligaría a
  todo el equipo a tener Node para cualquier prueba); un componente nuevo
  en otra tecnología conectado al broker (el broker embebido no expone
  STOMP ni AMQP, habría que abrir protocolos solo para esto); un cliente
  del ERP en otro lenguaje (sería un consumidor externo de la API, no un
  componente del sistema).
- **Consecuencias:** se prueba que el acoplamiento es el contrato y no la
  tecnología: Rabbit usa el banco en Node sin cambiar código, incluido el
  circuit breaker (probado). Hay dos implementaciones del banco que
  mantener con las mismas reglas.
