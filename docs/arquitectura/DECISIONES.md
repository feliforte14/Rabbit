# Registro de decisiones (ADRs)

Formato: contexto → decisión → consecuencias. Estado: Aceptada,
Propuesta o Reemplazada.

## Índice

| ADR | Decisión | Estado |
|---|---|---|
| [ADR-001](#adr-001-cola-jms--polling-de-respaldo-para-sincronizar-pedidos) | Cola JMS + polling de respaldo para sincronizar pedidos | Aceptada |
| [ADR-002](#adr-002-el-envío-del-mensaje-no-comparte-transacción-con-el-guardado) | El envío del mensaje no comparte transacción con el guardado | Aceptada |
| [ADR-003](#adr-003-timeout-del-padrón-fiscal-no-bloquea-el-alta) | Timeout del padrón fiscal no bloquea el alta | Reemplazada |
| [ADR-004](#adr-004-importe-y-medio-de-pago-vienen-del-erp) | Importe y medio de pago vienen del ERP | Aceptada |
| [ADR-005](#adr-005-máquina-de-estados-del-pedido-en-el-enum) | Máquina de estados del pedido en el enum | Aceptada |
| [ADR-006](#adr-006-tópico-para-los-cambios-de-estado-del-pedido) | Tópico para los cambios de estado del pedido | Aceptada |
| [ADR-007](#adr-007-endpoint-rest-de-entrada-reemplaza-al-formulario-del-erp) | Endpoint REST de entrada reemplaza al formulario del ERP | Aceptada |
| [ADR-008](#adr-008-restricciones-check-de-columnas-enum-alineadas-al-desplegar) | Restricciones CHECK de columnas enum alineadas al desplegar | Aceptada |
| [ADR-009](#adr-009-suscripciones-durables-y-selector-en-el-tópico-de-estados) | Suscripciones durables y selector en el tópico de estados | Aceptada |
| [ADR-010](#adr-010-cobro-por-soap-en-un-banco-legado-con-reversa-compensatoria) | Cobro por SOAP en un banco legado, con reversa compensatoria | Aceptada |
| [ADR-011](#adr-011-circuit-breaker-propio-frente-al-banco-legado) | Circuit breaker propio frente al banco legado | Aceptada |
| [ADR-012](#adr-012-sin-alta-pública-de-usuarios) | Sin alta pública de usuarios | Aceptada |
| [ADR-013](#adr-013-vistas-por-tipo-de-usuario-y-ruteo-mínimo) | Vistas por tipo de usuario y ruteo mínimo | Aceptada |
| [ADR-014](#adr-014-escalar-el-consumidor-de-la-cola-con-consumidores-competidores) | Escalar el consumidor de la cola con consumidores competidores | Aceptada |
| [ADR-015](#adr-015-banco-legado-en-nodejs-detrás-del-mismo-wsdl) | Banco legado en Node.js, detrás del mismo WSDL | Aceptada |
| [ADR-016](#adr-016-transportistas-externos-con-un-adapter-por-tecnología-y-seguimiento-por-polling) | Transportistas externos con un Adapter por tecnología y seguimiento por polling | Aceptada |
| [ADR-017](#adr-017-zonas-por-rango-de-código-postal-y-despacho-según-la-cobertura) | Zonas por rango de código postal y despacho según la cobertura | Aceptada |
| [ADR-018](#adr-018-api-rest-del-erp-versionada-idempotente-y-atada-al-comercio) | API REST del ERP versionada, idempotente y atada al comercio | Aceptada |
| [ADR-019](#adr-019-https-obligatorio-salvo-los-sistemas-externos-simulados) | HTTPS obligatorio, salvo los sistemas externos simulados | Aceptada |
| [ADR-020](#adr-020-cotización-con-los-transportistas-antes-de-derivar) | Cotización con los transportistas antes de derivar | Aceptada |
| [ADR-021](#adr-021-página-pública-de-seguimiento-y-tablas-como-tarjetas-en-el-celular) | Página pública de seguimiento y tablas como tarjetas en el celular | Aceptada |
| [ADR-022](#adr-022-webhook-de-novedades-y-transportista-moderno-como-servicio-aparte) | Webhook de novedades y transportista moderno como servicio aparte | Reemplazada |
| [ADR-023](#adr-023-las-contraseñas-solo-en-el-realm-límite-de-intentos-en-el-login) | Las contraseñas, solo en el realm; límite de intentos en el login | Aceptada |
| [ADR-024](#adr-024-sin-catálogo-de-productos-en-rabbit) | Sin catálogo de productos en Rabbit | Aceptada |
| [ADR-025](#adr-025-mismo-orden-de-bloqueo-pedido--envío-en-cancelación-y-novedades) | Mismo orden de bloqueo (pedido → envío) en cancelación y novedades | Aceptada |
| [ADR-026](#adr-026-cierre-de-una-reserva-de-stock-con-el-mismo-bloqueo-que-abrirla) | Cierre de una reserva de stock, con el mismo bloqueo que abrirla | Aceptada |
| [ADR-027](#adr-027-consultas-de-stock-en-un-ejb-stateless-separado-de-la-reserva) | Consultas de stock en un EJB @Stateless, separado de la reserva | Aceptada |
| [ADR-028](#adr-028-un-comercio-con-solo-pedidos-cancelados-se-puede-eliminar) | Un comercio con solo pedidos cancelados se puede eliminar | Aceptada |
| [ADR-029](#adr-029-seguimiento-de-envíos-solo-por-polling-sin-webhook) | Seguimiento de envíos solo por polling (sin webhook) | Aceptada |
| [ADR-030](#adr-030-la-heterogeneidad-tecnológica-en-una-rama-aparte) | La heterogeneidad tecnológica, en una rama aparte | Aceptada |
| [ADR-031](#adr-031-las-cuentas-en-la-base-compartida-realm-jdbc-con-bcrypt) | Las cuentas, en la base compartida (realm JDBC con bcrypt) | Aceptada |
| [ADR-032](#adr-032-peso-de-los-bultos-y-capacidad-del-vehículo-del-repartidor) | Peso de los bultos y capacidad del vehículo del repartidor | Aceptada |
| [ADR-033](#adr-033-la-entrega-se-registra-con-la-foto-del-remito-y-solo-quien-estuvo-ahí) | La entrega se registra con la foto del remito, y solo quien estuvo ahí | Aceptada |
| [ADR-034](#adr-034-liquidación-mensual-a-comercios) | Liquidación mensual a comercios | Aceptada |
| [ADR-035](#adr-035-ubicación-del-repartidor-en-vivo-y-mapa) | Ubicación del repartidor en vivo y mapa | Aceptada |
| [ADR-036](#adr-036-el-administrador-puede-eliminar-usuarios-repartidores-y-zonas) | El administrador puede eliminar usuarios, repartidores y zonas | Aceptada |
| [ADR-037](#adr-037-lista-de-espera-hora-de-corte-y-devolución-al-depósito) | Lista de espera, hora de corte y devolución al depósito | Aceptada |
| [ADR-038](#adr-038-hoja-de-ruta-recomendada-del-repartidor) | Hoja de ruta recomendada del repartidor | Aceptada |
| [ADR-039](#adr-039-datos-de-quien-recibe-el-pedido) | Datos de quien recibe el pedido | Aceptada |
| [ADR-040](#adr-040-avisos-a-quien-recibe-con-un-tercer-suscriptor-del-tópico) | Avisos a quien recibe, con un tercer suscriptor del tópico | Aceptada |
| [ADR-041](#adr-041-entrega-fallida-con-motivo-fijo-intentos-y-devolución-al-comercio) | Entrega fallida con motivo fijo, intentos y devolución al comercio | Aceptada |
| [ADR-042](#adr-042-fecha-prometida-con-la-hora-de-corte) | Fecha prometida con la hora de corte | Aceptada |
| [ADR-043](#adr-043-horario-estimado-en-el-seguimiento-parada-n-de-m) | Horario estimado en el seguimiento (parada N de M) | Aceptada |
| [ADR-044](#adr-044-tablero-de-indicadores-de-entrega) | Tablero de indicadores de entrega | Aceptada |
| [ADR-045](#adr-045-evidencia-de-la-visita-fallida-y-un-motivo-para-cada-caso) | Evidencia de la visita fallida y un motivo para cada caso | Aceptada |
| [ADR-046](#adr-046-hoja-de-ruta-por-distancia-con-openstreetmap) | Hoja de ruta por distancia, con OpenStreetMap | Aceptada |
| [ADR-047](#adr-047-mails-reales-por-smtp-con-login-y-tls-gmail) | Mails reales por SMTP con login y TLS (Gmail) | Aceptada |
| [ADR-048](#adr-048-un-pedido-que-salió-a-reparto-no-se-cancela) | Un pedido que salió a reparto no se cancela | Aceptada |
| [ADR-049](#adr-049-editar-y-eliminar-el-stock-de-un-depósito) | Editar y eliminar el stock de un depósito | Aceptada |
| [ADR-050](#adr-050-logs-sin-datos-personales-y-hsts) | Logs sin datos personales y HSTS | Aceptada |
| [ADR-051](#adr-051-los-avisos-se-ven-en-una-campana-de-notificaciones) | Los avisos se ven en una campana de notificaciones | Aceptada |
| [ADR-052](#adr-052-calificación-de-la-entrega-en-el-seguimiento-público) | Calificación de la entrega en el seguimiento público | Aceptada |
| [ADR-053](#adr-053-retiro-en-depósito) | Retiro en depósito | Aceptada |
| [ADR-054](#adr-054-mejoras-de-diseño-visual) | Mejoras de diseño visual | Aceptada |
| [ADR-055](#adr-055-prueba-de-entrega-reforzada-código-de-4-dígitos-y-ubicación) | Prueba de entrega reforzada: código de 4 dígitos y ubicación | Aceptada |
| [ADR-056](#adr-056-etiquetas-con-qr-por-bulto) | Etiquetas con QR por bulto | Aceptada |
| [ADR-057](#adr-057-opciones-de-quien-recibe-desde-el-seguimiento) | Opciones de quien recibe desde el seguimiento | Aceptada |
| [ADR-058](#adr-058-órdenes-de-recepción-de-mercadería) | Órdenes de recepción de mercadería | Aceptada |
| [ADR-059](#adr-059-cobro-por-almacenamiento) | Cobro por almacenamiento | Aceptada |

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
  calcularlo: no lleva el catálogo ni los precios de los comercios
  (ADR-024), y lo que se retira de un punto de picking es texto libre.
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
  `PedidoService.cambiarEstado`. En ese momento no se usó el patrón State
  completo (ver la actualización del 26/10/2026, más abajo).
- **Consecuencias:** una sola regla para validar y para disparar el
  evento de cambio de estado. `cancelarPedido` deja de aceptar pedidos
  `EN_CAMINO` o `ENTREGADO`.
- **Actualización (26/10/2026):** el ciclo de vida pasó al patrón State
  completo. `EstadoDelPedido` (interfaz sellada) tiene una clase por estado
  (`Pendiente`, `Confirmado`, `EnCamino`, `Entregado`, `Cancelado`,
  `DevueltoAlComercio`, en `pedidos/datos/model`): cada una decide qué
  operaciones permite y a qué estado lleva, y tiene una acción de entrada
  (`alEntrar`) con los efectos de antes (dejar la lista de espera, marcar que
  salió a reparto). `Pedido` delega (`confirmar()`, `despachar()`, …),
  `PedidoService.aplicar` es el único lugar que cambia el estado y
  `EstadoPedido.puedePasarA` consulta al estado. Una operación no permitida
  lanza `TransicionInvalidaException`, que el servicio traduce a
  `TransicionNoPermitidaException` (409 en la API del ERP al cancelar). La
  columna de la base sigue siendo el enum. Se descartó el enum con métodos por
  constante porque los estados consultan el pedido (retiro en depósito).
  Los mensajes de "por qué no se puede cancelar" también viven en cada estado
  (`motivoParaNoCancelar`, para el personal y el ERP, y `queLeQuedaAlErp`, para
  el detalle del 409 de la API); `ReglaDeCancelacion` solo le pregunta al
  estado y ya no tiene `switch`.
- **Nota de despliegue:** Hibernate crea un `CHECK` con los valores del
  enum que `hbm2ddl=update` no regenera; ver README (sección "Cómo
  levantar el sistema").

## ADR-006: Tópico para los cambios de estado del pedido

- **Estado:** Aceptada.
- **Contexto:** Notificaciones y Pagos necesitan enterarse de los cambios
  de estado, cada uno por su motivo.
- **Decisión:** tópico `topico.pedidos.estado` con el formato de
  `EstadoPedidoCambiado`; orden resuelto por `fechaCambio` en cada
  suscriptor.
- **Consecuencias:** Pedidos no conoce a sus suscriptores. Cada suscriptor
  tiene que ser idempotente y tolerar desorden.

## ADR-007: Endpoint REST de entrada reemplaza al formulario del ERP

- **Estado:** Aceptada. El partner se autentica con HTTP
  Basic y rol `ERP`; el formulario queda para la demo.
- **Contexto:** el formulario JSF no marca una frontera real entre el ERP
  y Rabbit.
- **Decisión:** `POST /api/v1/pedidos-externos` llama al mismo
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
  nunca se acredita. Además, Pagos solo necesita `ENTREGADO` (y, desde
  ADR-041, `DEVUELTO_AL_COMERCIO`, para anular el cobro).
- **Decisión:** las suscripciones son durables (`clientId` +
  `subscriptionName`, con `shareSubscriptions` para el pool del MDB).
  Hoy son tres: Pagos (`rabbit-pagos`), avisos al comercio
  (`rabbit-notificaciones`) y avisos a quien recibe
  (`rabbit-destinatarios`, ADR-040). Pagos usa
  `messageSelector = "estado IN ('ENTREGADO', 'DEVUELTO_AL_COMERCIO')"`
  sobre una propiedad JMS del mensaje, así el broker no le entrega el
  resto.
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
  para la Entrega Final. *(El agrupamiento por zona se hizo en ADR-017.)*

## ADR-014: Escalar el consumidor de la cola con consumidores competidores

- **Estado:** Aceptada.
- **Contexto:** un comercio que manda muchos pedidos juntos llena
  `cola.pedidos.externos` y los pedidos tardan en aparecer. Hacía falta
  poder escalar ese consumidor sin tocar código (desafío de
  escalabilidad, ver [DESAFIOS-OPCIONALES.md](../consigna/DESAFIOS-OPCIONALES.md)).
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

- **Estado:** Aceptada. Desde ADR-030, el banco en Node.js vive en la
  rama `desafio-heterogeneidad`.
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

## ADR-016: Transportistas externos con un Adapter por tecnología y seguimiento por polling

- **Estado:** Aceptada.
- **Contexto:** Rabbit reparte con repartidores propios, pero hay pedidos
  que conviene derivar a una empresa de envíos externa (fuera de zona, sin
  repartidores libres). Cada transportista tiene su propio sistema: unos
  exponen una API REST moderna y otros, sistemas legados por SOAP. Una vez
  derivado, Rabbit tiene que seguir el estado del envío para mover el
  pedido.
- **Decisión:** componente Transportistas con un **Adapter** por
  tecnología (`IAdaptadorTransportista`: REST y SOAP legado), elegido por
  el `TipoIntegracion` del transportista. La derivación la decide el
  personal (`derivarATransportista`) y va en una transacción con el cobro,
  con compensación si se deshace. El seguimiento es por **polling**
  (`SeguimientoDeEnvios`, cada 15 s), y los cambios llegan a Pedidos por un
  evento CDI (`EstadoEnvioCambiado`), así Transportistas no depende de
  Pedidos.
- **Alternativas descartadas:**
  - *Webhook del transportista hacia Rabbit:* menos consultas y más
    inmediato, pero un transportista legado no avisa; habría que mantener
    polling igual para esos. Queda como mejora para los que lo soporten.
  - *Derivación automática (por zona o falta de repartidores):* necesita
    zonas, que llegan con el Ruteo completo. *(Hecho en ADR-017: el Ruteo
    deriva solo según la zona.)*
  - *Un servicio por transportista sin interfaz común:* cada uno metería su
    tecnología en la lógica de negocio.
  - *Que el seguimiento llame directo a Pedidos:* Transportistas y Pedidos
    dependerían uno del otro.
- **Consecuencias:** sumar un transportista es darlo de alta con su
  endpoint; sumar una tecnología (por ejemplo EDI) es escribir un
  adaptador. El estado llega con hasta 15 s de demora, y si un
  transportista salta un estado entre consultas, el pedido pasa por los
  dos juntos. El timer mueve pedidos con `@RunAs("OPERADOR")`.

## ADR-017: Zonas por rango de código postal y despacho según la cobertura

- **Estado:** Aceptada.
- **Contexto:** el Ruteo mínimo (ADR-013) solo mostraba la hoja de ruta
  de cada pedido; qué repartidor o transportista lo llevaba lo decidía el
  personal a mano, pedido por pedido. Para la Entrega Final el ruteo tiene
  que agrupar los pedidos y decidir el despacho, y la derivación
  automática a transportistas (descartada en ADR-016) necesitaba zonas.
- **Decisión:** el componente Ruteo suma una capa de datos con **zonas**
  (tabla `zonas`): un rango de códigos postales argentinos de 4 dígitos
  (1000-9999, sin superponerse) y una **cobertura**: `PROPIA` (reparten
  repartidores de Rabbit, con un transportista de respaldo opcional) o
  `TRANSPORTISTA` (se deriva siempre a uno). El pedido suma
  `codigoPostalEntrega`: el ERP lo manda (opcional) o se toma de la
  dirección (CPA `C1414ABC`, "CP 1414", "(1414)"). Cada repartidor puede
  tener una zona. "Despachar" (`despacharPedido` / `despacharZona`)
  decide solo:
  - zona de transportista → `derivarATransportista`;
  - zona propia con un repartidor libre de la zona → se confirma con él;
  - zona propia sin repartidores libres y con respaldo → se deriva al
    respaldo;
  - zona propia sin respaldo → se confirma con un repartidor de otra zona;
  - sin zona → queda para el personal, a mano.
- **Alternativas descartadas:**
  - *Geocodificar la dirección y usar polígonos o distancias:* más
    preciso, pero necesita un servicio externo de mapas (costo, claves,
    otra dependencia caída posible) y el ERP ya conoce el código postal.
  - *Zona derivada de la localidad (texto libre):* las direcciones vienen
    escritas de mil formas; un rango numérico se valida y no es ambiguo.
  - *Viajes con varias paradas y orden óptimo del recorrido:* cambia el
    modelo del repartidor y ordenar por distancia necesita coordenadas.
    Desde ADR-032 un repartidor con capacidad lleva varios pedidos; el
    orden de las paradas sigue fuera del alcance.
  - *Despachar la zona en una sola transacción:* un pedido que falla (por
    ejemplo, el cobro) desharía los demás. Cada pedido va en su propia
    transacción y el resultado se informa pedido por pedido.
- **Consecuencias:** el personal despacha una zona entera con un botón y
  la derivación a transportistas pasa a ser automática donde conviene.
  Los pedidos sin código postal (anteriores o sin CP en la dirección)
  quedan "sin zona" y se despachan a mano como antes. Un rango mal
  cargado manda pedidos a otra zona: el alta rechaza superposiciones,
  rangos invertidos y zonas de transportista sin transportista activo.
  Para ver el recorrido, la hoja de ruta tiene un link a Google Maps
  (retiros como paradas, entrega como destino) que no necesita clave ni
  guarda coordenadas: el mapa lo resuelve Google cuando se toca el link.

## ADR-018: API REST del ERP versionada, idempotente y atada al comercio

- **Estado:** Aceptada.
- **Contexto:** la primera versión de la API (`/api/pedidos-externos`)
  funcionaba, pero contra lo visto en la Clase 10 (Servicios REST) tenía
  huecos: cualquier usuario `ERP` podía cargar y consultar pedidos de
  cualquier comercio (el `idComercio` venía en el cuerpo); si el `201` se
  perdía por un timeout, el reintento del ERP duplicaba el pedido; los
  errores eran `{"error": "..."}` y un error inesperado devolvía la
  página `error.html`; no había contrato, versión, cancelación ni
  seguimiento que no expusiera IDs secuenciales.
- **Decisión:**
  - **ERP atado a un comercio:** `ERP` pasa a ser un rol de la app; la
    cuenta la crea un administrador asociada a un comercio, como la
    `COMERCIO`. `PedidoService` toma el comercio de la cuenta (nunca del
    cuerpo) y un pedido ajeno responde `404`.
  - **Versión en la URI:** `/api/v1/...`.
  - **Idempotencia del alta:** header `Idempotency-Key` obligatorio,
    guardado con el pedido junto con una huella (SHA-256) del contenido,
    y restricción única `(idComercio, claveIdempotencia)`. Misma clave y
    mismo pedido → se devuelve el existente; otro pedido → `422`.
  - **Problem Details (RFC 9457)** para todos los errores, con
    `ProblemaMapper` como red para lo que no maneja un recurso.
  - **Bean Validation** en un DTO propio de la API (`PedidoExternoRequest`).
  - **Cancelación** como sub-recurso `POST .../{id}/cancelacion`,
    idempotente, permitida mientras el pedido está pendiente (`409`
    después).
  - **HATEOAS** con `_links` en la representación del pedido externo.
  - **Seguimiento público por código aleatorio** (`RB-XXXXXXXXXX`), no por ID.
  - **Contrato OpenAPI** en `docs/integraciones/openapi.yaml` (contract-first: se
    escribe a mano, no se genera del código).
- **Alternativas descartadas:**
  - *Versión en un header o en el media type:* más alineado con REST,
    pero más difícil de probar y de explicar al integrar un ERP.
  - *Validar en el recurso que el `idComercio` del cuerpo sea el del
    ERP:* el dato sobra; sacarlo del contrato es más simple y no deja
    lugar a error.
  - *Idempotency-Key opcional:* el ERP que no la mande vuelve a quedar
    expuesto a duplicados; obligatoria, el problema no existe.
  - *Deduplicar por el contenido del pedido, sin clave:* dos pedidos
    iguales legítimos (el mismo cliente compra dos veces lo mismo) se
    confundirían con un reintento.
  - *`@Valid` en el parámetro del recurso:* el runtime responde las
    violaciones con su propio formato, distinto de Problem Details.
  - *`DELETE /pedidos-externos/{id}` para cancelar:* el pedido no se
    borra; queda, cancelado.
  - *Generar el OpenAPI con MicroProfile OpenAPI:* el perfil
    `standalone-full` de WildFly no trae ese subsistema.
- **Consecuencias:** un reintento del ERP ya no duplica pedidos, y un
  ERP solo ve lo suyo. Rompe a los clientes de la versión anterior (sin
  `/v1` y sin clave), aceptable porque el único cliente es el script de
  carga, actualizado en el mismo cambio. Las cuentas ERP creadas con
  `add-user.sh` dejan de funcionar (`403`) hasta crearlas desde la app.
  Los pedidos anteriores no tienen código de seguimiento.

## ADR-019: HTTPS obligatorio, salvo los sistemas externos simulados

- **Estado:** Aceptada.
- **Contexto:** el login manda la contraseña y la API del ERP usa HTTP
  Basic, que manda usuario y clave en cada pedido. Por HTTP viajaban en
  claro (era una limitación conocida). WildFly ya trae un
  `https-listener` (8443) con certificado autofirmado.
- **Decisión:** `transport-guarantee CONFIDENTIAL` en `web.xml` sobre `/`
  (todo lo de Rabbit: pantallas, API `/api/v1`, seguimiento) y sobre la
  regla de la API del ERP (es más específica, así que hay que repetirlo).
  Un pedido por HTTP recibe un `302` a HTTPS. La cookie de sesión sale con
  `Secure` y `HttpOnly`. Quedan por HTTP (regla con `NONE`) los sistemas
  de otras empresas simulados en el mismo WAR: `/api/simulador/*`,
  `/BancoLegadoService` y `/TransportistaLegadoService`.
- **Alternativas descartadas:**
  - *HTTPS solo para la API del ERP:* el login de la web también manda una
    contraseña, y la cookie de sesión robada da el mismo acceso.
  - *HTTPS también para los simulados:* Rabbit los llama por HTTP como a
    cualquier sistema externo configurado así; la redirección rompía esas
    llamadas (el cliente JAX-RS y el JAX-WS no siguen un `302` en un `POST`).
  - *Rechazar HTTP en lugar de redirigir:* más estricto, pero el navegador
    no llegaría solo a la pantalla de login.
- **Consecuencias:** en local, el navegador advierte por el certificado
  autofirmado y `curl` necesita `-k`; en producción iría un certificado
  reconocido. Un ERP tiene que llamar directo a HTTPS: si llama por HTTP,
  sus credenciales viajan en claro antes de recibir el `302`. El script de
  carga usa HTTPS (`RABBIT_BASE`).

## ADR-020: Cotización con los transportistas antes de derivar

- **Estado:** Aceptada.
- **Contexto:** al derivar un pedido, el personal elegía el transportista
  sin saber cuánto cobraba ni cuánto tardaba. LogiRed pide consumir del
  transportista moderno la cotización además del despacho.
- **Decisión:** nueva operación `cotizarEnvio` en `IAdaptadorTransportista`.
  El REST la implementa con `POST {endpoint}/cotizaciones` (mismos datos
  que el envío, timeout de 5 s); el legado SOAP responde "no cotiza" sin
  llamar, porque su WSDL no la tiene. `IEnvios.cotizarEnvio` cotiza con
  todos los activos (`NOT_SUPPORTED`: no escribe nada) y ordena del más
  barato al más caro. En **Pedidos**, **Cotizar** muestra la tabla y cada
  fila tiene **Derivar con este**.
- **Alternativas descartadas:**
  - *Elegir solo el más barato al despachar por zona:* cambia el despacho
    automático (ADR-017), que hoy decide por la cobertura de la zona; queda
    como mejora.
  - *Guardar la cotización y exigirla al derivar:* el precio puede cambiar
    entre la cotización y el envío; hoy es informativa.
  - *Cotizar en paralelo:* más rápido con muchos transportistas, pero con
    pocos no hace falta; uno caído suma como máximo 5 s.
- **Consecuencias:** el personal compara antes de derivar, y un
  transportista caído aparece como "no respondió" sin frenar a los demás.
  Sumar la cotización a otra tecnología es implementarla en su adaptador.

## ADR-021: Página pública de seguimiento y tablas como tarjetas en el celular

- **Estado:** Aceptada.
- **Contexto:** el enlace "Ver lo que ve el cliente" mostraba el JSON de la
  API; el cliente final no tenía una página para seguir su pedido. En el
  celular (comercio y repartidor), las tablas había que correrlas de
  costado.
- **Decisión:** `seguimiento.xhtml` (pública, `SeguimientoBean`, misma
  operación que `GET /api/v1/seguimiento`): el código por la URL, el estado
  en palabras y una línea de tiempo. En pantallas de hasta 640 px, las
  tablas con clase `tabla-tarjetas` se muestran como tarjetas; `tablas.js`
  copia el título de cada columna a sus celdas (`data-label`), porque
  `h:dataTable` no deja ponerle atributos a cada celda. El CSS pasa a la
  versión `1_7`.
- **Alternativas descartadas:**
  - *Reescribir las tablas con `ui:repeat` y `<td data-label>`:* son 22
    tablas; el script resuelve todas sin tocar su estructura.
  - *Etiquetas por CSS (`nth-child`) en cada tabla:* frágil, se rompe al
    agregar o mover una columna.
  - *Ocultar columnas en el celular:* se pierde información.
- **Consecuencias:** sin JavaScript, las tarjetas se ven igual pero sin el
  nombre de cada dato. La página pública muestra solo el estado, igual que
  la API.

## ADR-022: Webhook de novedades y transportista moderno como servicio aparte

- **Estado:** Reemplazada en parte por ADR-029: el webhook se sacó; el
  transportista moderno como servicio aparte sigue, en la rama
  `desafio-heterogeneidad` (ADR-030). Completaba ADR-016 (polling).
- **Contexto:** el seguimiento por polling (cada 15 s) era lo único
  posible con un transportista legado, pero un transportista moderno puede
  avisar. Además, los dos transportistas vivían simulados dentro del WAR,
  y la rúbrica pide que la demo funcione "sin simulaciones falseadas".
- **Decisión:**
  - Webhook `POST /api/v1/transportistas/{id}/novedades`: el transportista
    avisa `{codigoSeguimiento, estado}` con `Authorization: Bearer <clave>`.
    La clave (32 bytes al azar) la genera el personal y se ve una sola vez;
    se compara en tiempo constante; un transportista solo toca sus envíos.
    Aplica la misma lógica que el polling (`aplicarNovedad`), así repetir
    un aviso, o que el polling llegue después, no cambia nada.
  - El polling sigue: para los legados y como respaldo de un aviso perdido.
  - `transportista-moderno/`: el transportista REST como proceso aparte
    (Python, biblioteca estándar), mismo contrato que el simulado, que
    usa el webhook.
  - Si el transportista de una zona no toma el envío, el despacho cotiza
    y deriva al más barato de los demás (completa ADR-020).
- **Alternativas descartadas:**
  - *Firmar el aviso con HMAC del cuerpo:* más robusto ante un intermediario,
    pero con HTTPS obligatorio una clave por transportista alcanza para el
    alcance del TP.
  - *Sacar el polling:* los legados no avisan, y un aviso perdido dejaría
    el pedido trabado.
  - *El transportista aparte en Java:* no suma heterogeneidad; Python sin
    dependencias se levanta en cualquier máquina.
- **Consecuencias:** los pedidos de un transportista moderno se mueven al
  instante. La clave queda guardada en claro (Rabbit tiene que compararla):
  si se filtra, se cambia desde la pantalla.

## ADR-023: Las contraseñas, solo en el realm; límite de intentos en el login

- **Estado:** Aceptada. Reemplaza el SHA-256 sin salt de la tabla
  `usuarios`. Desde ADR-031, las contraseñas vuelven a la base (con bcrypt
  y usadas por el login); el límite de intentos sigue igual.
- **Contexto:** la tabla `usuarios` guardaba un SHA-256 sin salt de cada
  contraseña, pero quien autentica es el ApplicationRealm de WildFly
  (`request.login`): ese hash no se usaba para nada. Tampoco había freno a
  la prueba de contraseñas en el login.
- **Decisión:** la tabla deja de guardar contraseñas; `LimpiezaDeCredenciales`
  borra la columna al desplegar (idempotente). `LimiteDeIntentos`: 5
  intentos fallidos seguidos bloquean ese usuario 15 minutos, con el mismo
  mensaje exista o no.
- **Alternativas descartadas:**
  - *Pasar el hash de la tabla a PBKDF2 o bcrypt:* seguiría siendo una
    credencial que nadie usa; fortalecerla no la hace necesaria.
  - *Autenticar contra la tabla con un realm JDBC de Elytron y bcrypt:* lo
    correcto en producción, pero cambia la configuración del servidor de
    todo el equipo (ver la nota en `LoginBean`).
  - *Bloquear por IP:* detrás de un proxy, todos comparten IP.
- **Consecuencias:** si la base se filtra, no hay contraseñas. El realm
  sigue con el formato MD5 que exige WildFly. El límite vive en memoria de
  cada servidor y no cubre el HTTP Basic de la API del ERP.

## ADR-024: Sin catálogo de productos en Rabbit

- **Estado:** Aceptada.
- **Contexto:** la entidad `Producto` (con atributos JSONB) estaba modelada
  desde la Entrega 1, pero nunca tuvo servicio ni pantalla.
- **Decisión:** quitarla, junto con su repositorio y la dependencia
  `hibernate-core` que solo existía para su columna JSONB. El catálogo vive
  en el ERP de cada comercio; Rabbit solo necesita saber qué retirar y
  entregar (las líneas del pedido y el stock consignado).
- **Consecuencias:** menos código muerto. La tabla `productos` queda en las
  bases existentes (hbm2ddl no borra tablas); se puede borrar a mano.

## ADR-025: Mismo orden de bloqueo (pedido → envío) en cancelación y novedades

- **Estado:** Aceptada.
- **Contexto:** cancelar un pedido derivado bloquea el pedido y después
  cancela su envío; una novedad del transportista (polling o webhook)
  bloqueaba primero el envío y, a través del evento sincrónico a Pedidos,
  terminaba bloqueando el pedido después. Si las dos coincidían sobre el
  mismo pedido, PostgreSQL detectaba el deadlock y abortaba una de las dos
  transacciones al azar — funcional (nada quedaba a medias) pero ruidoso:
  el webhook o el polling fallaban con una excepción cruda en vez de un
  conflicto entendible, y `TransportistaService.cancelarEnvioDePedido`
  tenía además una ventana real (sin deadlock de por medio) donde una
  novedad podía marcar el envío ENTREGADO entre la lectura sin bloqueo y
  el bloqueo posterior, dejando que la cancelación lo pisara igual.
- **Decisión:** las novedades ahora disparan el evento a Pedidos (que
  bloquea la fila del pedido) **antes** de bloquear y escribir el envío —
  mismo orden que la cancelación. `TransportistaRepository.buscarEnvioParaActualizar`
  usa el mismo esquema de `PedidoRepository.buscarPedidoParaActualizar`
  (`flush` + `detach` + `find` con bloqueo) para releer el estado real tras
  el bloqueo, no una copia de antes. Si el pedido no se pudo mover (regla
  de negocio) o el envío cambió mientras tanto, se deshace todo y se
  responde `409` (`PEDIDO_EN_CONFLICTO`): el webhook lo reintenta solo, el
  polling lo vuelve a tomar en la próxima pasada.
- **Alternativas descartadas:**
  - *Bloquear siempre el envío primero:* invierte el problema en vez de
    resolverlo (la cancelación sería la que corre el riesgo).
  - *Reintentar automáticamente el deadlock:* PostgreSQL ya lo resuelve
    abortando una transacción; agregar un reintento acá duplicaría esa
    lógica sin eliminar la causa (el orden cruzado).
- **Consecuencias:** sin deadlocks entre estos dos caminos. El contrato de
  `recibirNovedad`/`registrarNovedad` ahora incluye `409` como respuesta
  posible (documentado en `ISeguimientoEnvios` y el OpenAPI del webhook).

## ADR-026: Cierre de una reserva de stock, con el mismo bloqueo que abrirla

- **Estado:** Aceptada.
- **Contexto:** `reservarStock` ya manejaba `OptimisticLockException` sobre
  `ItemInventario` (flush explícito + mensaje entendible, ver el comentario
  de `@Version` en esa entidad), pero `confirmarReserva`, `liberarReserva`
  y `registrarDevolucion` no: un choque ahí salía como una excepción de
  persistencia cruda en vez de un mensaje de negocio. Además,
  `BarredorDeReservas` expiraba todas las reservas vencidas de una pasada
  en una sola transacción: un choque sobre una sola fila abortaba la
  pasada entera, dejando sin liberar también a las demás reservas vencidas
  de ese minuto.
- **Decisión:** las tres operaciones ahora pasan por el mismo
  `escribirStock` (flush + catch de `OptimisticLockException`) que ya
  usaba `reservarStock`, y leen la reserva con `PESSIMISTIC_WRITE`
  (`InventarioRepository.buscarReservaParaActualizar`) antes de cerrarla,
  para que dos caminos no puedan cerrar la misma reserva a la vez. El
  barredor expira cada reserva vencida en su propia transacción
  (`REQUIRES_NEW`, `BarredorDeReservas.expirarReserva`): un choque falla
  esa reserva sola y se reintenta en la próxima pasada, sin tocar a las
  demás. El conflicto se distingue con tipo propio
  (`StockModificadoException`, en Inventario) para que Pedidos lo traduzca
  a `ConflictoDeStockException` — pasajera, no una regla de negocio — y
  `SincronizadorDePedidos`/`PedidoExternoListener` la reintenten en vez de
  descartar el pedido del ERP como si el choque fuera permanente.
- **Consecuencias:** un choque de concurrencia en Inventario nunca más
  tira abajo un lote entero del barredor ni hace que el ERP pierda un
  pedido por un conflicto que se resolvía solo un segundo después.

## ADR-027: Consultas de stock en un EJB @Stateless, separado de la reserva

- **Estado:** Aceptada.
- **Contexto:** `InventarioService` (`@Stateful`) implementaba las dos
  interfaces del componente. `PedidoService` y `RuteoService` (`@Stateless`
  de pool) y los beans de la vista se inyectaban `IConsultaStock` como
  campo, y cada uno quedaba atado a una instancia stateful fija. Cuando esa
  instancia moría —por el `@StatefulTimeout` de 30 minutos o porque una
  excepción de sistema (por ejemplo, un corte de la base) hace que el
  contenedor la descarte— la referencia quedaba colgada y la próxima
  llamada fallaba con `NoSuchEJBException` (`WFLYEJB0168`). Se vio en la
  prueba manual: derivar un pedido y abrir "Entregas en curso" fallaban
  hasta reintentar con otra instancia del pool.
- **Decisión:** `IConsultaStock` pasa a `ConsultaStockService`
  (`@Stateless`), con las mismas operaciones, roles y transacciones.
  `InventarioService` queda solo con `IReservaStock`, que sí es una
  conversación y se sigue pidiendo por `Instance<IReservaStock>` (Provider).
- **Alternativas descartadas:**
  - *Pedir también `IConsultaStock` por `Instance<>` en cada llamada:*
    crea y destruye una instancia stateful para una consulta que no la
    necesita, y obliga a cada consumidor a recordar hacerlo.
  - *Subir o quitar el `@StatefulTimeout`:* no cubre el descarte por
    excepción de sistema y deja instancias vivas sin límite.
- **Consecuencias:** las consultas usan el pool stateless como el resto
  de los componentes y no pueden quedar con una referencia vencida. El
  componente sigue teniendo su parte stateful (la reserva), que es la que
  justifica el requisito de la consigna.

## ADR-028: Un comercio con solo pedidos cancelados se puede eliminar

- **Estado:** Aceptada.
- **Contexto:** la eliminación física de un comercio se bloqueaba ante
  cualquier pedido o pedido del ERP, aunque estuviera cancelado. Un
  comercio cargado para probar y cuyo único pedido se canceló no se podía
  borrar nunca, solo dar de baja.
- **Decisión:** Pedidos impide la eliminación solo por pedidos que no
  estén cancelados (en curso o entregados) y por pedidos del ERP que
  todavía esperan sincronizarse. Si nadie lo impide, `ComercioService`
  dispara `ComercioEliminado` (sincrónico, en la misma transacción) y cada
  componente borra lo que guardaba de ese comercio: Pedidos sus pedidos
  cancelados con sus líneas y sus pedidos del ERP; Pagos los cobros
  (anulados); Notificaciones los avisos; Transportistas los envíos
  (cancelados). Son dos eventos porque un componente no puede borrar lo
  suyo mientras otro todavía puede impedir la eliminación.
- **Alternativas descartadas:**
  - *Borrar solo el comercio:* los pedidos cancelados quedarían apuntando
    a un comercio inexistente.
  - *Que Comercios borre todo directamente:* tendría que depender de
    Pedidos, Pagos, Notificaciones y Transportistas, que ya dependen de él.
- **Consecuencias:** un pedido entregado sigue impidiendo la eliminación
  (es historial con cobro real). El historial cancelado de un comercio
  eliminado se pierde: la baja lógica sigue siendo la opción para
  conservarlo.

## ADR-029: Seguimiento de envíos solo por polling (sin webhook)

- **Estado:** Aceptada. Reemplaza la parte del webhook de ADR-022.
- **Contexto:** el seguimiento de envíos tenía dos caminos: el polling
  (`SeguimientoDeEnvios`, cada 15 s, para todos los transportistas) y un
  webhook para que los modernos avisaran (`POST
  /api/v1/transportistas/{id}/novedades`, con una clave Bearer por
  transportista). El webhook sumaba un endpoint público con su propia
  autenticación, la generación y el guardado de claves, una pantalla, el
  caso de conflicto `409` y la coordinación con el polling, para ahorrar
  como mucho 15 s de demora en ver un cambio de estado.
- **Decisión:** se saca el webhook (recurso REST, clave del transportista,
  pantalla y tests). El seguimiento queda solo por polling, igual para
  modernos y legados. `transportista-moderno/` sigue como servicio aparte
  en Python (heterogeneidad, hoy en la rama `desafio-heterogeneidad`), sin avisar: Rabbit lo consulta como a los
  demás.
- **Alternativas descartadas:**
  - *Mantener los dos caminos:* más superficie expuesta y más casos de
    concurrencia para un beneficio chico.
  - *Solo webhook:* los legados no avisan, y un aviso perdido dejaría el
    pedido trabado.
- **Consecuencias:** un pedido derivado a un transportista moderno tarda
  hasta 15 s más en pasar a En camino o Entregado. Rabbit expone un
  endpoint menos y no guarda claves de terceros. La columna
  `transportistas.claveWebhook` se borró a mano de la base
  (`ALTER TABLE transportistas DROP COLUMN clavewebhook`):
  `hbm2ddl=update` agrega columnas pero nunca las borra, así que en otra
  base que ya la tuviera hay que borrarla igual (o queda sin uso).

## ADR-030: La heterogeneidad tecnológica, en una rama aparte

- **Estado:** Aceptada (14/10/2026).
- **Contexto:** `main` tenía, además de Rabbit, dos sistemas externos en
  otros lenguajes: el banco legado en Node.js (ADR-015) y el transportista
  moderno en Python (ADR-022). Son solo para el desafío opcional de
  heterogeneidad: Rabbit funciona entero con los simulados del WAR. En
  `main` sumaban dos tecnologías más para instalar, documentar y probar,
  y mezclaban el sistema con sus demos.
- **Decisión:** `main` queda con el sistema de Rabbit solo. La rama
  `desafio-heterogeneidad` es `main` más un commit que vuelve a sumar
  `banco-legado/`, `transportista-moderno/` y su documentación (el revert
  del commit que los sacó de `main`). Lo que se cambie en `main` se lleva
  a la rama con `git merge main`.
- **Alternativas descartadas:**
  - *Dejar todo en `main`:* cualquiera que levante el sistema ve
    servicios que no necesita.
  - *Un repositorio aparte para los servicios:* separa el contrato del
    código que lo implementa del otro lado; con una rama, cada demo es un
    `git switch`.
  - *Sacar el código de Rabbit que permite usarlos:* la system property
    `rabbit.banco.wsdl` y el registro de transportistas por endpoint son
    configuración genérica; quedan en `main`.
- **Consecuencias:** para mostrar la heterogeneidad hay que cambiar de
  rama y redesplegar. La rama hay que mantenerla al día con `main`: al
  18/10/2026 quedó atrás (no tiene lo agregado desde el 14/10), así que
  antes de una demo hay que hacer `git merge main` en ella.

## ADR-031: Las cuentas, en la base compartida (realm JDBC con bcrypt)

- **Estado:** Aceptada (15/10/2026). Reemplaza la parte de ADR-023 que
  dejaba las contraseñas solo en el realm de archivos.
- **Contexto:** cada integrante levanta su propio WildFly contra la misma
  base de Supabase. Las contraseñas vivían en los archivos de add-user.sh
  de cada WildFly, así que una cuenta creada desde la app de una máquina
  aparecía en la base de todos pero solo entraba en esa máquina. Para
  probar entre todos había que repetir cada cuenta con `add-user.sh` en
  cada WildFly.
- **Decisión:**
  - La tabla `usuarios` guarda el hash bcrypt de la contraseña
    (`hash_clave`, costo 10, generado con jBCrypt; clase `Claves`).
  - WildFly valida el login con un `jdbc-realm` de Elytron
    (`RabbitRealmBase`) que lee esa tabla por el mismo `RabbitDS`. Una
    consulta trae el hash solo de cuentas activas; otra trae el rol. Una
    cuenta dada de baja devuelve rol sin contraseña: existe, así que no
    se sigue buscando, y no puede entrar.
  - Un `distributed-realm` (`RabbitRealmCompartido`) busca primero en la
    base y, si la cuenta no está, en los archivos de add-user.sh. Así
    siguen entrando el administrador inicial de cada instalación y las
    cuentas viejas mientras se migran.
  - `ApplicationDomain` (el dominio que ya usaba Rabbit) pasa a usar ese
    realm: la app no cambia su configuración de seguridad (`web.xml`,
    `@RolesAllowed`).
  - La configuración la deja `scripts/configurar-realm-compartido.cli`,
    que cada integrante corre una vez. No lleva contraseñas.
  - La pantalla Usuarios suma "Restablecer contraseña": pasa a la base las
    cuentas viejas y sirve para quien olvidó la suya.
- **Alternativas descartadas:**
  - *Copiar los archivos del realm entre máquinas:* hay que repetirlo con
    cada cuenta, y subirlos al repositorio (público) expone los hashes.
  - *Que Rabbit escriba los archivos del realm desde la base al
    desplegar:* casi el mismo trabajo, y una cuenta nueva no llega a las
    otras máquinas hasta su próximo despliegue.
  - *Un dominio de seguridad propio para Rabbit (`jboss-web.xml`,
    `jboss-ejb3.xml`):* más archivos para el mismo resultado; si alguien
    no corrió el script, el despliegue fallaría en vez de seguir
    funcionando con las cuentas locales.
  - *PBKDF2 (está en el JDK, sin dependencia nueva):* Elytron lo lee solo
    como SCRAM, con tres columnas (hash, sal, iteraciones); bcrypt entra
    en una sola y es el formato más conocido.
- **Consecuencias:** una cuenta creada, dada de baja o con contraseña
  nueva vale en el WildFly de todos al mismo tiempo. Las altas ya no
  escriben archivos fuera de la transacción, así que desaparece la
  compensación del Flujo 5 de TRANSACCIONES.md. Si la base no responde,
  nadie con cuenta en la base puede entrar (tampoco funcionaría el resto
  del sistema). El formato de los archivos (MD5) no se puede pasar a
  bcrypt: las cuentas viejas necesitan una contraseña nueva una vez. Una
  cuenta de la base con el mismo nombre que una de add-user.sh de alguna
  máquina le gana a esa.

## ADR-032: Peso de los bultos y capacidad del vehículo del repartidor

- **Estado:** Aceptada (15/10/2026). Mejora del tablero del equipo
  ("límite de pedidos por repartidor" y "peso de los bultos").
- **Contexto:** un repartidor llevaba un solo pedido a la vez, sin
  importar si era un sobre o una heladera. Rabbit no conocía el peso de
  nada.
- **Decisión:**
  - **Peso por unidad en el stock** (`ItemInventario.pesoKg`, obligatorio
    en las altas; lo ya cargado se corrige con "Editar", ADR-049). Un pedido de
    stock consignado calcula su peso al sincronizarse: peso por unidad ×
    cantidad. Si algún ítem no tiene peso, el del pedido queda
    desconocido (null): contarlo como 0 lo haría pasar por liviano. La
    sincronización corre sin usuario (JMS y timer), así que el peso se
    pide con `IConsultaStock.pesosPorItem`, sin rol y que solo devuelve el
    peso de cada ítem; el listado completo del stock sigue siendo del
    personal.
  - **Punto de picking:** Rabbit no conoce esa mercadería; el ERP puede
    informar `pesoKg` (opcional). Con stock consignado no se acepta (422):
    lo calcula Rabbit. La huella de idempotencia solo incluye el peso si
    vino, así las claves de antes siguen valiendo.
  - **Capacidad del vehículo** (`Repartidor.capacidadKg` y
    `capacidadBultos`, opcionales). Se mide por las dos cosas: el peso
    (lo que soporta) y los bultos (el espacio). La regla vive en
    `CapacidadRepartidor`:
    - con capacidad cargada, lleva varios pedidos mientras la suma no pase
      ninguno de los límites que tenga;
    - sin capacidad, un pedido a la vez, como antes (los repartidores que
      ya existían no cambian);
    - un pedido de peso desconocido solo se controla por bultos.
  - **Qué lleva cada uno:** una fila por pedido asignado
    (`CargaAsignada`, tabla `repartidores_cargas`), con el peso y los
    bultos del momento. Se borra al entregar o cancelar. Asignar bloquea
    a los candidatos (`SELECT ... FOR UPDATE`): dos confirmaciones a la
    vez no cargan de más al mismo repartidor.
  - El despacho por zona pregunta "¿a quién de la zona le entra este
    pedido?" en lugar de "¿quién está libre?", y si a nadie, usa el
    transportista de respaldo como antes.
- **Alternativas descartadas:**
  - *Solo cantidad de pedidos:* una moto y un camión tendrían el mismo
    límite.
  - *Solo kilos:* veinte cajas livianas no entran en una moto.
  - *Contadores en el repartidor (carga actual):* se desincronizan si una
    liberación falla o se repite; una fila por pedido se puede recalcular.
- **Consecuencias:** la hoja de ruta del repartidor muestra todas sus
  entregas en curso; el orden recomendado de las paradas llegó después,
  por código postal (ADR-038). Si no le entra a nadie, el pedido va a la
  lista de espera (ADR-037). Cada unidad cuenta como un bulto (3 yerbas son 3
  bultos). En pantalla los kilos se muestran como "1 kg" o "0,95 kg"
  (`infraestructura/Formatos`), no con la escala de la columna ("1.000").

## ADR-033: La entrega se registra con la foto del remito, y solo quien estuvo ahí

- **Estado:** Aceptada (15/10/2026). Mejora del tablero ("cambio de estado
  en camino / entregado").
- **Contexto:** cualquiera del personal podía marcar un pedido como
  entregado con un clic, sin prueba. ENTREGADO dispara el cobro contra
  entrega: es el cambio de estado que mueve plata.
- **Decisión:**
  - **ENTREGADO:** solo el `REPARTIDOR` que lleva el pedido, o un
    `ADMINISTRADOR` como contingencia (por ejemplo, el repartidor se quedó
    sin batería). Un `OPERADOR` ya no. Los dos tienen que subir la **foto
    del remito firmado**. Se guarda quién y cuándo (`entregadoPor`,
    `fechaEntrega`).
  - **EN_CAMINO:** lo sigue pudiendo marcar el repartidor o el personal
    (por ejemplo, al despachar desde el depósito): no mueve plata.
  - **Pedidos derivados a un transportista externo:** entran por otra
    operación (`registrarEntregaDeTransportista`), sin remito de Rabbit:
    la prueba es del transportista. Esa operación rechaza los pedidos con
    repartidor propio, así no sirve para saltear el remito.
  - **La foto:** JPEG, PNG o WEBP, reconocida por sus primeros bytes (no
    por el nombre ni por lo que dice el navegador), hasta 5 MB. El celular
    la achica antes de subirla (`remito.js`, ~300 KB). Se guarda en la
    base compartida, en su propia tabla (`remitos_entrega`), y se ve en
    `/remito?pedido=ID`: el personal, el comercio dueño y su repartidor;
    a cualquier otro le responde 404.
- **Alternativas descartadas:**
  - *Guardarla en el disco del WildFly:* cada integrante levanta el suyo;
    la foto solo la vería quien registró la entrega (el mismo problema que
    ADR-031 con las contraseñas).
  - *Supabase Storage:* sería lo prolijo en producción, pero suma claves
    de API y otro servicio que configurar en cada máquina. Para el volumen
    del TP, la base alcanza.
  - *Firma en pantalla en lugar de foto:* el remito en papel ya lo firma
    el cliente; la foto prueba también qué se entregó.
- **Consecuencias:** el aviso al comercio de ENTREGADO menciona el
  remito, y el comercio lo ve en "Mis pedidos". La base crece unos 300 KB
  por entrega.

## ADR-034: Liquidación mensual a comercios

- **Estado:** Aceptada (15/10/2026). Mejora del tablero ("cálculo de
  cobranzas a comercios"). La tarifa es una propuesta para que el equipo
  la ajuste.
- **Contexto:** Rabbit cobraba cada pedido (la venta del comercio a su
  cliente), pero no cobraba su propio servicio al comercio.
- **Decisión:**
  - Una vez por mes, a cada comercio activo: **abono fijo + un cargo por
    pedido entregado + un cargo por kilo entregado**. Tarifa por defecto:
    $15.000 de abono, $1.200 por envío y $80 por kilo. Se cambia con
    system properties (`rabbit.tarifa.abono`, `rabbit.tarifa.por-envio`,
    `rabbit.tarifa.por-kilo`) sin tocar el código (`TarifaMensual`).
  - Se liquida **solo un mes cerrado**. Los pedidos del mes se cuentan
    por su fecha de entrega. Cada liquidación queda guardada con los
    importes del momento (`liquidaciones`, una por comercio y mes) y se
    cobra por el **banco legado** (SOAP, referencia `LIQ-<id>`), con el
    mismo circuit breaker que los pedidos.
  - **Timer diario a las 6** (`LiquidacionMensual`): el día 1 liquida el
    mes anterior; los demás días solo completa lo que falte (si WildFly
    estaba apagado el 1) y reintenta los cobros PENDIENTE (el banco no
    respondió). Un rechazo del banco queda RECHAZADA y un administrador lo
    reintenta a mano.
  - Cada comercio va en sus propias transacciones (crear, después cobrar):
    el banco no se deshace con un rollback, así que la liquidación existe
    antes de llamarlo.
  - Pantalla "Liquidaciones" (personal): lo cobrado por mes, el
    botón para liquidar un mes cerrado (administrador) y una **vista
    previa del mes en curso**, que calcula sin guardar ni cobrar. El
    comercio ve las suyas en "Mis liquidaciones", también con la vista
    previa del mes en curso, calculada solo con sus entregas (el
    resumen de entregas de todos los comercios es solo del personal).
- **Alternativas descartadas:**
  - *Descontarlo de lo que se cobra contra entrega:* mezcla la plata del
    comercio con la de Rabbit y obliga a hacer rendiciones.
  - *Cobrar por pedido al confirmar:* más llamadas al banco y más
    reversas cuando un pedido se cancela.
- **Consecuencias:** un comercio sin entregas paga solo el abono. Si un
  pedido se entrega pero se informa después del cierre, entra en el mes
  de su fecha de entrega solo si ese mes todavía no se liquidó.

## ADR-035: Ubicación del repartidor en vivo y mapa

- **Estado:** Aceptada (15/10/2026). Mejora del tablero ("mapa para
  repartidores internos").
- **Decisión:**
  - Mientras el repartidor tiene entregas en curso y la pantalla "Mis
    entregas" abierta, su celular manda la ubicación cada 30 segundos
    (`ubicacion.js`, geolocalización del navegador, por el mismo
    formulario JSF). Se guarda **solo la última** (`Repartidor.latitud`,
    `longitud`, `fechaUbicacion`), no el recorrido. Sin entregas en curso
    no se acepta: es para seguir entregas, no a la persona.
  - **Personal:** "Mapa de repartidores" con los que repartieron en los
    últimos 15 minutos; se actualiza solo por ajax.
  - **Cliente:** la página pública de seguimiento muestra el mapa mientras
    el pedido está EN_CAMINO con un repartidor de Rabbit, y la API de
    seguimiento devuelve `ubicacionRepartidor`. Solo si ese repartidor
    tiene **ese** pedido en curso: el código de un pedido viejo no sirve
    para seguirlo en otro reparto.
  - **Aviso:** el de EN_CAMINO al comercio incluye el código para que su
    cliente lo siga en vivo.
  - Mapas con Leaflet (cdnjs, con hash de integridad) y teselas de
    OpenStreetMap, gratis y sin claves.
- **Alternativas descartadas:**
  - *Google Maps:* pide clave de API y facturación.
  - *WebSockets para tiempo real:* para posiciones cada 30 segundos, una
    consulta periódica alcanza y no suma infraestructura.
  - *Guardar el recorrido completo:* más datos personales de los que
    hacen falta.
- **Consecuencias:** el navegador solo da la ubicación por HTTPS y con
  permiso del repartidor; en el celular, con el certificado autofirmado
  hay que aceptar la advertencia. Sin coordenadas de las direcciones de
  entrega no se calcula cuánto falta.

## ADR-036: El administrador puede eliminar usuarios, repartidores y zonas

- **Estado:** Aceptada (15/10/2026). Pedido del equipo durante las
  pruebas.
- **Contexto:** usuarios, repartidores y zonas solo se podían dar de baja.
  Las cuentas y zonas de prueba se acumulaban en el padrón compartido y
  un repartidor cargado por error no se podía sacar.
- **Decisión:** baja física, **solo `ADMINISTRADOR`** (`@RolesAllowed` en
  el EJB; el botón "Eliminar" solo se le muestra a él). Mismo patrón que
  la eliminación de comercios (ADR-028): cada componente que guarda una
  referencia puede impedirlo o la suelta, en la misma transacción, por
  eventos CDI, así el componente dueño no depende de los demás.
  - **Usuario** (`eliminarUsuario`): nada apunta a una cuenta (quién
    entregó cada pedido queda como texto, ADR-033). No se puede eliminar
    la propia cuenta ni al último administrador activo. También se borra
    su línea del realm de archivos de esa máquina, si tenía.
  - **Repartidor** (`eliminarRepartidor`): no se puede con entregas en
    curso ni con una cuenta de usuario asociada, activa o dada de baja
    (`EliminacionDeRepartidor`; hay que eliminar la cuenta primero). Sus
    pedidos ya terminados quedan sin repartidor asignado
    (`RepartidorEliminado`).
  - **Zona** (`eliminarZona`): los pedidos no guardan su zona (se calcula
    por el código postal), así que no hay nada que impedir. Los
    repartidores de esa zona quedan sin zona fija (`ZonaEliminada`).
- **Alternativas descartadas:**
  - *Borrar también la cuenta del repartidor:* una cuenta es acceso al
    sistema; que desaparezca sin que nadie la elimine a propósito es
    peligroso.
  - *Solo baja lógica:* no resolvía el padrón lleno de pruebas.
- **Consecuencias:** eliminar no se deshace. Para que alguien deje de
  operar sin perder nada, sigue estando la baja.

## ADR-037: Lista de espera, hora de corte y devolución al depósito

- **Estado:** Aceptada (16/10/2026). Correcciones del equipo (Trello,
  "Testing NO OK"). La devolución al depósito sin límite la reemplaza
  ADR-041 (motivos fijos e intentos).
- **Contexto:** si ningún repartidor tenía lugar (ADR-032), confirmar
  fallaba y el pedido quedaba PENDIENTE hasta que alguien lo volviera a
  confirmar a mano. La asignación ya probaba con todos los repartidores,
  primero los de la zona. Además, no había un horario límite para armar
  la hoja de ruta del día, y el repartidor no tenía cómo devolver un
  pedido que no pudo entregar.
- **Decisión:**
  - **A otro repartidor:** se recorren los candidatos en orden y gana el
    primero al que le entra (`AsignacionPorCapacidad`). Nunca se espera a
    un repartidor puntual.
  - **Lista de espera:** si no le entra a nadie, el pedido **no se cobra**
    y queda PENDIENTE con la marca `enEsperaDesde` y el motivo. No es un
    estado nuevo: así no cambian la máquina de estados, el tópico ni el
    seguimiento. `AsignadorDeEspera` (`@Singleton`, `@RunAs("OPERADOR")`)
    los asigna por orden de llegada, con cobro y asignación juntos como
    siempre. Corre enseguida que un pedido se entrega, se cancela o se
    devuelve (lo dispara `AvisosDeEspera`) y, de respaldo, cada minuto.
    Un pedido grande que no entra no tapa la fila: se prueba con los
    siguientes. Si falla por otra cosa (el banco rechaza el pago), sale de
    la fila con el motivo a la vista del personal.
  - **Antes de cobrar se consulta si hay lugar**
    (`IAsignacionRepartidores.motivoParaEsperar`, sin bloqueo). Si otro se
    queda con el lugar entre esa consulta y la asignación, el banco ya
    cobró y se reversa como antes (`ReversasBancarias`).
  - **Hora de corte: 07:00**, definida por el equipo el 18/10/2026
    (`HoraDeCorte.POR_DEFECTO`; del 16 al 18/10 fue 21:00). A las 7 se
    cierra la hoja del día, así hay tiempo de preparar los pedidos y salir
    a la calle a las 9. Desde esa hora no se suman pedidos a la hoja del
    día aunque haya lugar: lo que se confirma después va a la lista de
    espera y se asigna solo al día siguiente, antes del corte. Se cambia
    con la system property `rabbit.ruteo.hora-corte` (`HH:mm`), leída en
    cada uso, y con el valor `ninguno` no hay corte. Un valor inválido se
    ignora y rige el de 07:00.
  - **Devolver a depósito:** el repartidor del pedido (o un
    administrador), desde CONFIRMADO o EN_CAMINO, con motivo obligatorio.
    El pedido vuelve a PENDIENTE en la lista de espera, el repartidor deja
    de llevarlo y **no se le vuelve a asignar ese pedido**
    (`idRepartidorExcluido`). Conserva su cobro (`cobroRegistrado`): al
    reasignarlo no se cobra de nuevo, y si se cancela se anula. El ERP ya
    no lo puede cancelar por la API (salió a reparto). El comercio recibe
    el aviso "volvió al depósito".
  - Despacho por zona (Ruteo): un pedido que va a la espera devuelve el
    resultado "En espera" en lugar de un error.
- **Alternativas descartadas:**
  - *Estado nuevo EN_ESPERA o DEVUELTO:* obligaba a tocar la máquina de
    estados, el tópico, los suscriptores y el seguimiento, para algo que
    sigue siendo "pendiente de asignar".
  - *Cobrar al confirmar aunque quede en espera:* cobra un servicio que
    todavía no tiene fecha, y obliga a reversar si se cancela antes.
  - *Corte a las 21:00* (el que rigió primero): seguía sumando pedidos a
    la hoja durante todo el día, y el repartidor no podía planificar su
    recorrido antes de salir.
  - *Corte a las 9:00:* deja sin margen para preparar los pedidos antes
    de salir a la calle a esa hora.
- **Consecuencias:** con el corte a las 07:00, durante el día todo lo
  que se confirma queda en espera para el día siguiente: para una demo o
  una prueba hay que configurar `ninguno` (README, paso 7b). Las columnas nuevas de `pedidos` admiten NULL y las
  crea Hibernate al desplegar (`update`); las versiones anteriores siguen
  funcionando sobre la misma base. La API del ERP agrega `enListaDeEspera`
  y no ofrece el link de cancelar un pedido devuelto.

## ADR-038: Hoja de ruta recomendada del repartidor

- **Estado:** Aceptada (16/10/2026). Correcciones del equipo (Trello,
  "Testing NO OK").
- **Contexto:** el repartidor tenía un link de Google Maps por pedido; con
  varios pedidos a la vez (ADR-032) no tenía un recorrido para el día.
- **Decisión:**
  - Arriba de "Mis entregas", la **hoja de ruta recomendada** con todas
    sus paradas: primero los retiros que le faltan (sin repetir lugares),
    después las entregas encadenadas por **cercanía de código postal**
    (vecino más cercano, desde el último retiro). Varios pedidos a la
    misma dirección son una sola parada (`OrdenDeRecorrido`).
  - El orden se calcula **en el back** y se abre en Google Maps con un
    link común: hasta 10 puntos por link (la partida y 9 paradas). Con más
    paradas se parte en tramos, y cada uno arranca donde terminó el
    anterior. El primero no lleva origen: arranca donde está el
    repartidor.
  - Se sacaron el botón "Ver recorrido en el mapa" de cada pedido y la
    sección "Registrar una entrega": "Entregar pedido" abre una ventana
    para subir ahí la foto del remito, con las mismas validaciones
    (ADR-033). "Devolver a depósito" abre otra para el motivo (ADR-037).
- **Alternativas descartadas:**
  - *Solo un link de Google Maps:* los links no reordenan las paradas;
    eso lo hace la Directions API, que pide clave y facturación.
  - *Geocodificar las direcciones para usar distancias reales:* contradice
    ADR-017 y suma un servicio externo. Queda para que lo decida el
    equipo.
- **Consecuencias:** el orden es una aproximación (los CP cercanos suelen
  ser barrios vecinos, no siempre). En el navegador de un celular sin la
  app de Google Maps, Google acepta menos paradas por link que en la app.

## ADR-039: Datos de quien recibe el pedido

- **Estado:** Aceptada (16/10/2026; en `main` desde el 18/10/2026, PR #8).
  El email pasó a ser obligatorio el 18/10 (ver "Actualización" al final).
  Sale de la comparación con los líderes de última milla: todos saben a
  quién le entregan.
- **Contexto:** el pedido solo tenía dirección y código postal. Rabbit no
  sabía por quién preguntar en la puerta, no tenía a quién llamar si no
  encontraba la dirección y no podía avisarle nada al cliente (ADR-040).
- **Decisión (original):** tres campos **opcionales** en el alta (API y pantalla),
  en `PedidoExterno` y en `Pedido`: `nombreDestinatario` (100),
  `telefonoDestinatario` (30) y `emailDestinatario` (120). Los normaliza y
  valida `Destinatarios`: vacío es "no vino", el teléfono se acepta como
  lo escribe la gente (`+54 11 4567-8901`, `(011) 4567-8901`) y el email
  tiene que tener forma de email porque es para avisar. El repartidor ve
  nombre y teléfono (con link para llamar); el personal y el comercio, en
  sus listados. El seguimiento público **no** los muestra.
- **Alternativas descartadas:**
  - *Obligatorios:* rompían a todo ERP que ya integra. Con opcionales, un
    ERP que no los manda sigue igual, solo sin avisos al cliente.
  - *Una entidad aparte (Destinatario):* para tres columnas que no se
    reusan entre pedidos, era una tabla y un join más sin beneficio.
- **Consecuencias:** las columnas nuevas admiten NULL (`update`). La huella
  de idempotencia suma estos datos solo si vienen: un reintento de antes
  de este cambio da la misma huella. Son datos personales: se borran con
  el pedido cuando se elimina su comercio.
- **Actualización (18/10/2026): el email pasa a ser obligatorio.** El
  equipo lo pidió porque es por donde se le avisa al cliente (ADR-040).
  - **Alta de pedidos nuevos:** la API responde **400** si falta
    (`datos-con-formato-invalido`, con `emailDestinatario` en `errores` y
    el motivo "es obligatorio porque Rabbit le avisa por mail…"); la
    pantalla "Simular un pedido del ERP" lo marca obligatorio; y el negocio
    lo vuelve a exigir (`Destinatarios.emailObligatorio`), así ningún
    camino lo saltea. Nombre y teléfono siguen opcionales.
  - **Lo que ya existía no se toca:** los pedidos y los pedidos del ERP
    dados de alta antes, sin email, siguen funcionando igual (se
    sincronizan, se reparten y se entregan) y solo no reciben avisos. La
    columna sigue admitiendo NULL y no hay migración de datos.
  - **ERP ya integrados:** tienen que empezar a mandar el email; hasta
    entonces sus altas nuevas reciben 400 con el campo indicado. Se
    descartó una perilla para seguir aceptando altas sin email: los únicos
    clientes de la API hoy son los nuestros (simulador, tests, Postman y
    la prueba de carga), y se actualizaron todos. Caso borde: reintentar
    con la misma clave un alta enviada antes del cambio, sin email, ahora
    da 400 (antes devolvía el pedido ya creado).

## ADR-040: Avisos a quien recibe, con un tercer suscriptor del tópico

- **Estado:** Aceptada (16/10/2026; en `main` desde el 18/10/2026, PR #8).
- **Contexto:** solo se le avisaba al comercio. Onfleet, Bringg, Andreani
  y DHL le avisan a quien recibe en cada cambio, con el link al
  seguimiento.
- **Decisión:** `SuscriptorDestinatarioEstadoPedido`, un **tercer MDB**
  sobre `topico.pedidos.estado`, con su propia suscripción durable
  (`rabbit-destinatarios`). Llama a `AvisoDestinatarioService`, que:
  descarta eventos viejos o repetidos con la `fechaCambio` (igual que los
  avisos al comercio), no hace nada si el pedido no tiene email, arma el
  texto (`TextosAlDestinatario`: "Recibimos tu pedido de X, llega hoy",
  "llega hoy", "no pudimos entregarlo", "entregado"...) con el link
  público (`rabbit.url-publica`), lo guarda en `avisos_destinatario` y lo
  manda por mail después del commit con el mismo `AvisosPorMail`.
- **Alternativas descartadas:**
  - *Que `NotificacionService` también le avise al cliente:* mezcla dos
    destinatarios con reglas distintas en el mismo método, y una falla de
    uno frenaría al otro.
  - *Llamarlo desde `PedidoService.cambiarEstado`:* acopla Pedidos a
    Notificaciones y hace esperar al usuario. El tópico existe justo para
    no tener que tocar Pedidos al sumar un interesado (ADR-006).
  - *SMS o WhatsApp:* exigen un proveedor pago; el mail ya estaba.
- **Consecuencias:** sumar el canal no tocó Pedidos ni a los otros dos
  suscriptores. Sin servidor de correo, los avisos quedan registrados y el
  personal los ve en la campana de la cabecera → "Avisos a quien recibe" (ADR-051). El texto usa el
  motivo de devolución vigente al momento del aviso.

## ADR-041: Entrega fallida con motivo fijo, intentos y devolución al comercio

- **Estado:** Aceptada (16/10/2026; en `main` desde el 18/10/2026, PR #8). Reemplaza en parte a
  ADR-037 (la devolución al depósito ya no es ilimitada).
- **Contexto:** "Devolver a depósito" mandaba el pedido a PENDIENTE con
  texto libre y sin límite: un cliente que nunca estaba hacía dar vueltas
  al pedido para siempre, y "entrega al primer intento" (el indicador que
  usa toda la industria) no se podía medir.
- **Decisión:**
  - **Motivos fijos** (`MotivoEntregaFallida`): cliente ausente, dirección
    incorrecta, rechazado, zona peligrosa, no entra en el vehículo y otro
    (con detalle obligatorio). El texto libre sigue como detalle.
  - **Visitas:** una devolución cuenta como intento si el pedido estaba
    EN_CAMINO y el motivo implica haber ido a la dirección ("no entra en
    el vehículo" no). Se cuentan en `intentosFallidos`.
  - **Estado final `DEVUELTO_AL_COMERCIO`** (EN_CAMINO → DEVUELTO) al
    llegar a **2 visitas** (`rabbit.entregas.max-intentos`, de 1 a 10) o
    enseguida si el cliente **lo rechazó**. El stock consignado vuelve al
    stock del comercio (como al cancelar), el repartidor queda libre y
    **Pagos anula el cobro** al recibir el evento por el tópico (su MDB
    ahora filtra `ENTREGADO` y `DEVUELTO_AL_COMERCIO`): un CONTRA_ENTREGA
    nunca se cobró, y un PREPAGO se reversa en el banco como al cancelar.
  - El pedido conserva el repartidor de la última visita, para saber
    quién lo devolvió.
- **Alternativas descartadas:**
  - *Anular el cobro en la misma transacción:* `anularCobro` es solo de
    ADMINISTRADOR y quien devuelve es el repartidor. Por el tópico, como
    el cobro contra entrega, el permiso sigue intacto y el cobro se anula
    aunque Pagos esté caído un rato (suscripción durable).
  - *3 visitas:* lo hace algún operador, pero en la demo obliga a tres
    vueltas completas. Con la propiedad se cambia sin tocar código.
  - *Que el personal decida a mano cuándo devolverlo:* es justo lo que no
    escala.
- **Consecuencias:** `EstadoPedido` suma un valor (el CHECK lo realinea
  `AlineadorDeRestriccionesEnum`) y una columna enum nueva
  (`motivoentregafallida`). Cambiar el selector del MDB de Pagos hace que
  Artemis recree esa suscripción durable al desplegar: conviene desplegar
  sin cobros contra entrega pendientes de acreditar. Los pedidos devueltos
  no entran en la liquidación mensual (solo cuenta ENTREGADO).

- **Textos de los avisos revisados (18/10/2026):** todos los avisos de
  entrega fallida siguen la misma forma, qué pasó (con el motivo), qué
  sigue con el pedido y qué conviene hacer, en frases cortas y sin
  mayúsculas ni "visita(s)" (`TextosAlComercio`, `TextosAlDestinatario`).
  Al comercio no se le dice que "el stock vuelve" si el pedido salió de un
  punto de picking (no hay stock en Rabbit). Ninguno pasa de 255
  caracteres, el largo de la columna del portal.
- **Cancelar (ADR-048):** un pedido que salió a reparto ya no se cancela;
  el único final si no se entrega es DEVUELTO_AL_COMERCIO.

## ADR-042: Fecha prometida con la hora de corte

- **Estado:** Aceptada (16/10/2026; en `main` desde el 18/10/2026, PR #8).
- **Contexto:** el ERP no sabía si el pedido llegaba hoy o mañana. Mercado
  Libre Flex promete "llega hoy" según la hora de corte.
- **Decisión:** al **recibir** el pedido (no al sincronizarlo), Rabbit
  calcula `fechaPrometida` con la misma hora de corte de la hoja de ruta
  (ADR-037): antes del corte, hoy; después, mañana
  (`HoraDeCorte.diaDeEntrega`, expuesta a Pedidos por
  `IAsignacionRepartidores.diaDeEntregaPara`). Sin código postal no se
  promete nada (no se sabe la zona). Va en la respuesta del alta y en el
  GET de la API, en el seguimiento ("Llega hoy") y en los listados.
- **Alternativas descartadas:**
  - *Calcularla al sincronizar:* el ERP recibe el 201 antes, sin fecha.
  - *Hora de corte por zona:* es mejor (una zona lejana no llega a la
    misma hora), pero exige un dato nuevo por zona. Queda como mejora; el
    cálculo ya está en un solo lugar.
  - *Saltear fines de semana y feriados:* Rabbit reparte todos los días;
    si el equipo define días hábiles, se agrega en `diaDeEntrega`.
- **Consecuencias:** la promesa no se mueve: es contra lo que se mide
  "a tiempo" (ADR-044). Si una visita falla, el seguimiento deja de
  mostrarla en lugar de mostrar una fecha vencida.

## ADR-043: Horario estimado en el seguimiento (parada N de M)

- **Estado:** Aceptada (16/10/2026; en `main` desde el 18/10/2026, PR #8).
- **Contexto:** el cliente veía el mapa pero no cuándo llegaba. Amazon
  muestra cuántas paradas faltan; Onfleet, una ETA.
- **Decisión:** con el pedido EN_CAMINO, el seguimiento (página y API)
  dice en qué parada de la **hoja de ruta recomendada** del repartidor
  está (ADR-038) y una franja: desde "ahora + paradas de antes x 20 min"
  (nunca antes de la hora actual) hasta dos paradas más de margen,
  redondeada a cuartos de hora; si es la próxima parada, "llega antes de
  las HH:mm"
  (`EstimacionDeLlegada`; `rabbit.ruteo.minutos-por-parada`). Armar el
  recorrido lee direcciones de otros pedidos y depósitos, que solo ve el
  personal: lo hace `LlegadaEstimadaService` con `@RunAs("OPERADOR")` y
  devuelve solo números. Si algo falla, el seguimiento se ve sin horario.
- **Alternativas descartadas:**
  - *Abrir sin rol `listarItemsPorIds` y la hoja de ruta:* expondría
    direcciones de terceros a cualquier llamada sin usuario.
  - *ETA real con tráfico:* exige geocodificar (ADR-017) y una API paga.
- **Consecuencias:** es una aproximación, igual que el orden por código
  postal. Coincide con lo que ve el repartidor, porque se arma con el
  mismo orden de asignación.

## ADR-044: Tablero de indicadores de entrega

- **Estado:** Aceptada (16/10/2026; en `main` desde el 18/10/2026, PR #8).
- **Contexto:** Rabbit no medía si entregaba a tiempo. ShipBob, Onfleet y
  Bringg tienen tableros con esos indicadores, para el operador y para
  el comercio.
- **Decisión:** "Indicadores" para el personal (toda la red o un comercio,
  con el detalle por repartidor) y "Mis indicadores" para el comercio,
  sobre los pedidos recibidos en los últimos 7, 30 o 90 días:
  - **a tiempo:** entregados el día prometido o antes, sobre los que
    tenían promesa;
  - **al primer intento:** entregados sin visitas fallidas, sobre los que
    terminaron el reparto (entregados + devueltos al comercio);
  - horas promedio de recibido a entregado, pedidos en espera ahora,
    motivos de las visitas fallidas;
  - **costo por envío:** la liquidación del mes en curso (abono + cargos)
    dividida por los envíos, que la vista toma de Pagos.
  Se calcula en memoria (`CalculoDeIndicadores`) sobre los pedidos del
  período: no hay tablas ni columnas de agregados.
  - **Visualización, estilo tablero de BI:** fila de KPIs (con medidor
    en los porcentajes) y gráficos en SVG propios (`indicadores.js`, sin
    librerías): entregas por día en columnas apiladas (a tiempo / fuera
    de término / sin fecha prometida), pedidos por estado, motivos de las
    visitas fallidas y, para el personal, barras agrupadas por
    repartidor. Cada gráfico tiene leyenda, tooltip (también con el
    teclado), "Ver como tabla" y "Descargar CSV" (separado por ";" y en
    UTF-8, como lo abre Excel en castellano); los filtros van en una fila
    arriba. La paleta (azul, naranja, aqua) se validó contra el fondo de
    cada tema, también daltonismo, y sigue el tema elegido sin redibujar.
  - **Comparación con el período anterior** del mismo largo en cada KPI
    ("▲ 6 pts vs. período anterior"), con flecha y texto además del
    color, que depende de si subir es bueno (más horas hasta la entrega
    es peor).
- **Alternativas descartadas:**
  - *Consultas agregadas en SQL:* más rápidas con millones de filas, pero
    reparten cada regla entre JPQL y Java y no se prueban sin base.
  - *Medir desde la confirmación:* no hay fecha de confirmación guardada;
    "desde que se recibió" es lo que ve el cliente.
  - *Una librería de gráficos (Chart.js) o Power BI embebido:* una
    librería suma una dependencia externa para cuatro gráficos simples, y
    Power BI exige licencias y sacar los datos de Rabbit.
- **Consecuencias:** los porcentajes salen como "—" si no hay base, en
  vez de 0 %. Los pedidos anteriores a ADR-042 no tienen promesa y no
  cuentan en "a tiempo".

## ADR-045: Evidencia de la visita fallida y un motivo para cada caso

- **Estado:** Aceptada (17/10/2026; en `main` desde el 18/10/2026, PR #8).
- **Contexto:** ADR-041 dejó motivos fijos e intentos, pero comparado con
  la tabla de casos típicos de una entrega fallida faltaban varios
  (paquete dañado, nadie habilitado para recibir, domicilio cerrado,
  problema del repartidor), no se guardaba ninguna prueba de la visita y
  un problema del repartidor ya en camino (moto rota, lluvia) solo podía
  cargarse como "Otro", que **le gastaba una visita al cliente**.
- **Decisión:**
  - **Motivos:** se suman `NADIE_HABILITADO`, `DOMICILIO_CERRADO`
    (cuentan como visita), `PAQUETE_DANADO` (vuelve al comercio sin
    reintentar, también desde Confirmado si lo detecta al retirarlo, y
    **sin devolver el stock**: la mercadería está dañada) y
    `PROBLEMA_DEL_REPARTIDOR` (no cuenta como visita, se reasigna). Cada
    motivo dice si cuenta como visita, si devuelve al comercio y qué
    evidencia pide (`MotivoEntregaFallida`).
  - **Evidencia** (`IntentoEntrega` y `FotoIntentoEntrega`, tablas
    `intentos_entrega` y `fotos_intento_entrega`): cada vez queda el
    motivo, un comentario de hasta 500 caracteres (obligatorio con un
    rechazo o "Otro"), hasta 3 fotos (al menos una con un paquete dañado;
    JPEG/PNG/WEBP de hasta 5 MB, que el celular achica antes de subir) y
    dónde estaba el repartidor. Las ven el personal y el comercio dueño en
    "Ver visitas" (`comercio/visitas.xhtml`, `/foto-visita`); el
    repartidor no, porque deja de tener el pedido.
  - **Ubicación:** al abrir la ventana, el celular da la posición del
    momento (`entrega-fallida.js`, `getCurrentPosition`). Si no la da (sin
    permiso, sin señal), se usa la última que compartió mientras repartía
    (ADR-035) si es de los últimos 10 minutos, marcada como aproximada. Si
    no hay ninguna, la visita se registra igual (`EvidenciaDeVisita`).
  - **Avisos:** al cliente, un texto por motivo con lo que puede hacer
    (dejar a alguien mayor con DNI, revisar la dirección con la tienda...),
    avisando cuando la próxima es la última visita; si el problema fue del
    reparto, "se demora" sin culparlo. Nunca el comentario del repartidor.
    Al comercio, el motivo con el comentario, la visita N de M, qué hacer y
    dónde ver las fotos; un paquete dañado es una INCIDENCIA. El aviso de
    la falla ya no se descarta si llega después del de la reasignación
    (pasa en menos de un segundo): se reconoce por `fechaUltimaFalla`
    (`AvisosDeFalla`). Antes, el comercio no se enteraba.
  - El botón del repartidor dice "Devolver pedido" (puede haber salido de
    un depósito o de un punto de picking).
- **Alternativas descartadas:**
  - *Fotos y ubicación obligatorias siempre:* sin señal o sin permiso el
    repartidor no podría cerrar la visita y el pedido quedaría trabado.
  - *Guardar las fotos en disco:* cada integrante tiene su WildFly; como
    el remito (ADR-033), van a la base.
  - *Pausar el pedido para que el comercio corrija la dirección, retiro
    en un punto y franjas horarias:* quedan para una etapa siguiente
    (necesitan pantallas y estados nuevos).
- **Consecuencias:** el aviso del portal al comercio se recorta a 255
  caracteres (largo de la columna); el mail lleva el texto completo. Un
  paquete dañado no devuelve stock: lo resuelve el comercio con Rabbit.

## ADR-046: Hoja de ruta por distancia, con OpenStreetMap

- **Estado:** Aceptada (17/10/2026; en `main` desde el 18/10/2026, PR #8).
- **Contexto:** la hoja de ruta recomendada (ADR-038) arrancaba por los
  retiros y seguía por la entrega de código postal más cercano: sin
  coordenadas, el número de CP era la única forma de aproximar la
  distancia, y a veces mandaba al repartidor lejos (CP vecinos que no son
  barrios vecinos).
- **Decisión:** ubicar las direcciones en el mapa con **Nominatim
  (OpenStreetMap)** y ordenar por distancia en línea recta:
  - **En segundo plano** (`Geocodificador`, timer cada minuto): toma las
    direcciones de entrega y de retiro (depósito o punto de picking) de
    los pedidos en curso que no estén en la caché y las consulta, como
    mucho una por segundo y 20 por pasada, con un User-Agent propio (las
    reglas de uso del servicio). Si falla, reintenta al minuto siguiente.
  - **Caché** (`CoordenadaDireccion`, tabla `coordenadas_direcciones`):
    cada dirección (normalizada, con su CP) se consulta una sola vez; las
    no encontradas se reintentan a los 7 días.
  - **Orden** (`OrdenDeRecorrido`): retiros pendientes primero; las
    entregas, desde el último retiro o, si ya retiró todo, desde el
    depósito o punto de picking de donde salieron, siempre a la más
    cercana (haversine). Las que todavía no se ubicaron van después, por
    CP como antes. La hoja de ruta solo lee la caché: nunca espera a un
    servicio externo.
  - `rabbit.geocodificacion=ninguna` lo apaga (todo por CP);
    `rabbit.geocodificacion.url` usa otro servidor Nominatim.
- **Alternativas descartadas:**
  - *Google Maps (Geocoding y Directions):* más preciso y con calles
    reales, pero exige una clave con facturación.
  - *Geocodificar al dar de alta el pedido:* el alta llegaría a depender
    de un servicio externo lento o caído.
  - *Optimizar el recorrido completo (problema del viajante) o por
    calles:* para 5 a 15 paradas, el vecino más cercano en línea recta
    alcanza; ruteo por calles necesitaría un servicio de rutas.
- **Consecuencias:** las direcciones de entrega y retiro (solo dirección y
  CP, nunca quién recibe) salen hacia OpenStreetMap. Un pedido recién
  confirmado puede ordenarse por CP hasta la pasada siguiente (menos de un
  minuto). Una dirección mal cargada puede ubicarse en otro lugar: el
  orden es una recomendación, el repartidor puede seguir otro.

## ADR-047: Mails reales por SMTP con login y TLS (Gmail)

- **Estado:** Aceptada (17/10/2026; en `main` desde el 18/10/2026, PR #8).
- **Contexto:** los avisos por mail (al comercio y a quien recibe, ADR-040)
  solo sabían hablar con un servidor SMTP sin login ni cifrado: servían
  para un servidor de prueba local, no para que lleguen a casillas reales.
  Gmail y cualquier proveedor real exigen usuario, contraseña y TLS.
- **Decisión:** `ConfiguracionSmtp` arma la conexión con system
  properties leídas en cada envío: host, puerto, usuario, contraseña,
  seguridad (`starttls` por defecto con usuario; `ssl` para el 465;
  `ninguna`) y remitente (con login, por defecto la misma cuenta, que es
  lo que exige Gmail). Sin usuario se comporta como antes.
- **Por qué system properties y no un recurso `mail-session` de
  WildFly:** es el mismo mecanismo que el resto de la configuración de
  Rabbit (banco, hora de corte, tarifa) y se cambia sin redesplegar. Las
  credenciales quedan en la configuración de cada WildFly, como las de la
  base, nunca en el repositorio público.
- **Consecuencias:** hace falta una cuenta de correo para Rabbit (en
  Gmail, con verificación en dos pasos y una contraseña de aplicación).
  Gmail limita a unos 500 envíos por día. El envío sigue siendo después
  del commit: un correo caído no frena ni deshace nada.

## ADR-048: Un pedido que salió a reparto no se cancela

- **Estado:** Aceptada (18/10/2026). Pedido del equipo (Trello).
- **Contexto:** la máquina de estados ya impedía cancelar un pedido
  EN_CAMINO, pero un pedido que salió, falló la visita y volvió al
  depósito para otra (PENDIENTE en la lista de espera, ADR-041) se podía
  cancelar desde Pedidos. Además, si alguien forzaba la cancelación de uno
  en camino, el mensaje era el técnico ("está EN_CAMINO y no puede pasar a
  CANCELADO").
- **Decisión:** una vez que el pedido **salió a reparto**, Rabbit no lo
  cancela: solo queda entregarlo. Si no se puede, el repartidor registra
  la entrega fallida ("Devolver a depósito") y Rabbit lo reintenta; al
  agotar las visitas, con un rechazo o con el paquete dañado, termina
  DEVUELTO_AL_COMERCIO, y la devolución con el cliente la gestiona el
  comercio.
  - `Pedido.salioAReparto` se prende la primera vez que pasa a EN_CAMINO
    (al entrar a EN_CAMINO) y no se apaga. Para las filas anteriores, un
    pedido con visitas fallidas cuenta como salido (solo se cuentan si
    estaba EN_CAMINO).
  - `ReglaDeCancelacion` decide, igual para el personal (web) y para el
    ERP (API, 409 `cancelacion-no-permitida`), con un mensaje claro: "ya
    salió a reparto y no se puede cancelar: solo queda entregarlo. Si no se
    puede entregar, vuelve al comercio…".
  - En Pedidos no se muestra el botón Cancelar en esos pedidos, y en su
    lugar dice "Ya salió a reparto: no se puede cancelar".
  - Un pedido devuelto **sin haber salido** (por ejemplo, no entraba en el
    vehículo, desde CONFIRMADO) se sigue pudiendo cancelar.
- **Cómo encaja con la entrega fallida:** "Devolver a depósito" no es una
  cancelación: es registrar una visita que no se pudo completar. El único
  final para un pedido que salió y no se entrega es DEVUELTO_AL_COMERCIO.
- **Consecuencias:** la columna nueva admite NULL (`update`). El personal
  ya no puede "cortar por lo sano" un pedido que salió: tiene que pasar
  por la entrega fallida, que deja la visita, el motivo y la evidencia.

## ADR-049: Editar y eliminar el stock de un depósito

- **Estado:** Aceptada (18/10/2026). Pedido del equipo.
- **Contexto:** en "Depósitos y stock" solo se podía cargar stock y
  corregir el peso. Un producto mal escrito, una cantidad que no coincidía
  con lo que había en el galpón o un producto que ya no se guarda no
  tenían arreglo desde la aplicación.
- **Decisión:**
  - **Editar** (personal: administrador y operador): cada fila tiene un
    botón "Editar" que abre un formulario con el producto, la cantidad en
    el depósito y el peso por unidad (reemplaza a "Corregir peso"). El
    comercio dueño no se cambia: sería pasarle mercadería a otro. La
    cantidad no puede quedar por debajo de lo **reservado** (ya está
    comprometido con un pedido que se está armando), y el nombre no puede
    repetir otro producto del mismo comercio en ese depósito. Si alguien
    reserva del mismo ítem mientras tanto, el bloqueo optimista (`@Version`)
    rechaza el cambio con un mensaje para volver a abrirlo.
  - **Eliminar** (solo administrador, como el resto de las eliminaciones,
    ADR-036): borra el ítem. No se puede si tiene unidades reservadas, si
    lo usa un pedido **en curso** (ni entregado, ni cancelado, ni devuelto
    al comercio) o un pedido del ERP sin procesar: si se cancela, el stock
    tiene que volver a ese ítem. Lo comprueba Pedidos con el evento
    `EliminacionDeItem`, igual que `EliminacionDeComercio`, así Inventario
    no depende de Pedidos.
  - Las reservas del ítem **quedan en el historial**: dejan de apuntarle
    (`item_id` en NULL) y conservan el nombre del producto, la cantidad y
    el estado. Los pedidos terminados guardan el nombre en cada línea.
  - Las reglas de cantidad están en `ReglasDeStock` (con tests).
- **Alternativa descartada:** dar de baja en vez de borrar (un ítem
  oculto con stock 0). Obligaba a filtrar los dados de baja en todas las
  consultas de stock y a decidir qué hacer si volvía mercadería a un ítem
  oculto. Con las condiciones de arriba, borrar no deja nada colgado.
- **Consecuencias:** en el Historial, el filtro por depósito no encuentra
  las reservas de un ítem eliminado (ya no se sabe en qué depósito
  estaba); con "Todos" siguen apareciendo.

## ADR-050: Logs sin datos personales y HSTS

- **Estado:** Aceptada (21/10/2026). Pendientes de seguridad del tablero
  (SEC-05 y SEC-09).
- **Contexto:** dos logs escribían datos personales: `AvisosPorMail` el
  email completo de a quien se le avisa (en el aviso enviado y en el error) y
  `SimuladorDeEnvios` la dirección de entrega. El log del servidor lo puede
  leer más gente que la que ve el pedido y se conserva mucho tiempo. Además,
  Rabbit exigía HTTPS con `CONFIDENTIAL`, pero esa redirección ocurre después
  de que el primer pedido por HTTP ya viajó en claro, y no había HSTS.
- **Decisión:**
  - **Logs:** se loguea el ID del pedido. El email se enmascara con
    `DatosPersonales.emailEnmascarado` (primera letra, seis asteriscos fijos y
    el dominio, para no filtrar el largo del nombre), y los mensajes de error de
    JavaMail pasan por `DatosPersonales.sinEmails` porque a veces traen la
    dirección. La dirección de entrega no se escribe.
  - **HSTS:** `FiltroHsts` manda `Strict-Transport-Security: max-age=31536000`
    solo en respuestas por HTTPS, configurable con `rabbit.hsts.max.age` (0 la
    apaga). Sin `includeSubDomains` ni `preload`: Rabbit no controla otros
    subdominios del dominio donde se publique.
  - **JMS (SEC-08):** los publicadores arman el cuerpo en `construirPayload`, y
    un test verifica que solo lleve IDs, estado y fecha.
- **Consecuencias:** investigar un problema de mail con el log sigue siendo
  posible (se ve el ID del pedido y el dominio y la inicial del email), pero ya
  no se puede reconstruir a quién se le avisó. HSTS no tiene efecto con el
  certificado autofirmado de desarrollo (el navegador la ignora si el
  certificado no es válido): hay que verificarla en un entorno con certificado
  reconocido. Las pruebas automáticas de SEC-03 y SEC-04 necesitan dos cuentas
  extra (un ERP de otro comercio y un usuario con rol `COMERCIO`) y la app
  desplegada; sin ellas esos tests se saltean.

## ADR-051: Los avisos se ven en una campana de notificaciones

- **Estado:** Aceptada (21/10/2026). Pedido del equipo.
- **Contexto:** los avisos de Notificaciones se listaban en tablas al final
  de Mis pedidos (comercio) y de Pedidos (personal): había que bajar hasta el
  final de la pantalla para verlos, y no se distinguía lo nuevo de lo ya visto.
- **Decisión:** una **campana en la cabecera** de todas las pantallas
  (`template.xhtml`, `CampanaBean`). Al tocarla se abre un panel con los
  últimos 15 avisos; sobre la campana, un contador de los nuevos.
  - **Quién la ve:** el comercio (los avisos de sus pedidos) y el personal
    (avisos a comercios y avisos a quien recibe, ADR-040, que tienen el email
    del cliente final). Repartidor y ERP no tienen campana. Lo que cada rol
    puede leer lo sigue decidiendo el EJB con `@RolesAllowed`; el bean solo
    elige qué pedir según el rol.
  - **Sin JavaScript también anda:** es un `<details>` que abre y cierra solo.
    `campana.js` suma el contador, cerrar con Escape o al tocar afuera y marcar
    como vistos los avisos al abrirla.
  - **"Nuevo" se recuerda en el navegador** (`localStorage`, un valor por
    usuario y por tipo de aviso): un aviso es nuevo si su id es mayor al último
    que ese usuario vio. No se agregó ninguna tabla ni columna: la base no cambia.
  - Se sacaron las tablas viejas y los campos que las alimentaban en
    `PedidoBean` y `PortalComercioBean`. Los recursos pasaron a `1_18`
    (convención de versionado del CSS y JS).
- **Consecuencias:** "visto" es por navegador, no por cuenta: quien entra desde
  otra computadora vuelve a ver los últimos avisos como nuevos. Si más adelante
  se necesita un "leído" por cuenta (o una pantalla con todo el historial, hoy
  limitado a los últimos 15), hace falta guardarlo en la base. La campana
  consulta los avisos en cada pantalla que se dibuja (dos consultas chicas para
  el personal, una para el comercio).

## ADR-052: Calificación de la entrega en el seguimiento público

- **Estado:** Aceptada (21/10/2026). Pedido del equipo (Trello).
- **Contexto:** Rabbit sabía cuándo se entregaba un pedido pero no cómo había
  sido la entrega para quien lo recibió. El seguimiento público ya es lo único
  que ve esa persona (sin cuenta), así que es el lugar natural para preguntarlo.
- **Decisión:** cuando el pedido está ENTREGADO, `seguimiento.xhtml` pregunta
  "¿Cómo fue la entrega?": de 1 a 5 estrellas y un comentario opcional. Después
  de enviarla, muestra lo que se calificó y ya no ofrece el formulario.
  - **Dónde vive:** entidad nueva `CalificacionEntrega` (tabla
    `calificaciones_entrega`, con `idPedido` único), por ID de pedido y no por
    relación JPA, igual que el remito y las visitas fallidas. Operaciones públicas
    `consultarCalificacion` y `calificarEntrega` en `ISeguimientoPedido`; la tabla
    se agrega sola con `hibernate.hbm2ddl.auto=update`. Al eliminar un comercio se
    borran también las calificaciones de sus pedidos.
  - **Regla (`ReglaDeCalificacion`):** solo un pedido ENTREGADO; 1 a 5 estrellas;
    comentario de hasta 500 caracteres, recortado y sin caracteres de control; un
    comentario vacío no se guarda. Una sola calificación por pedido: se chequea y,
    además, `idPedido` único cubre dos envíos a la vez.
  - **Seguridad:** es la única escritura pública, así que es mínima: la autoriza el
    código aleatorio, no se guarda quién calificó, el comentario se escapa siempre
    al mostrarlo y no va al log (ver SEGURIDAD.md).
  - **Estrellas sin JavaScript:** cinco radios HTML comunes con la etiqueta
    dibujada como estrella (CSS), accesibles con teclado y con el nombre de cada
    opción para el lector de pantalla. El código del pedido viaja en un campo oculto
    porque en el envío del formulario el parámetro de la URL no se vuelve a leer.
  - Recursos a `1_19`.
- **Consecuencias:** no hay forma de corregir una calificación una vez enviada
  (es a propósito: es una opinión sobre una entrega ya hecha). El promedio se ve en
  Indicadores (21/10/2026): una tarjeta "Calificación de las entregas" para el personal y el
  comercio, y dos columnas por repartidor (promedio y cantidad de calificadas). Solo cuentan
  los pedidos entregados del período; sin calificaciones se muestra "—", no 0. Todavía no se
  ve el comentario en las pantallas del comercio ni del personal. Un enlace de seguimiento compartido
  permite calificar a quien lo tenga; es el mismo alcance que tiene ver el estado.

## ADR-053: Retiro en depósito

- **Estado:** Aceptada (21/10/2026). Pedido del equipo (Trello).
- **Contexto:** todos los pedidos salían a reparto. Hay compradores que prefieren
  pasar a buscar el pedido, y si el stock ya está en un depósito de Rabbit no tiene
  sentido asignarle un repartidor ni armarle una ruta.
- **Decisión:** el ERP manda `tipoEntrega = RETIRO_EN_DEPOSITO` (por defecto sigue
  siendo `DOMICILIO`). El pedido no lleva dirección, ni repartidor, ni lista de
  espera, ni pasa por Ruteo; queda "listo para retirar" al confirmarlo y se entrega
  mostrando el código de seguimiento.
  - **Modelo:** enum `TipoEntrega` y columna `tipoEntrega` en `Pedido` y `PedidoExterno`
    (null = domicilio, para no tocar los pedidos existentes), más `Pedido.idDepositoRetiro`.
  - **Reglas (`ReglaDeRetiro`):** solo con stock consignado (con punto de picking la
    mercadería está en el comercio); todos los productos en el mismo depósito (si no,
    422 explicando por qué); el código se compara sin importar mayúsculas ni espacios.
    El depósito se determina al sincronizar, antes de reservar, con
    `IConsultaStock.depositosPorItem`.
  - **Estados:** `EstadoPedido.puedePasarA(destino, tipo)`: un retiro pasa de CONFIRMADO
    a ENTREGADO directo y nunca por EN_CAMINO. `confirmarPedido` de un retiro solo cobra
    (si es prepago) y confirma. `entregarEnDeposito(idPedido, código)` (ADMINISTRADOR u
    OPERADOR) es la prueba de la entrega; al pasar a ENTREGADO se efectiviza el cobro
    contra entrega como siempre. Despachar, registrar entrega con remito, derivar,
    cotizar y devolver al depósito se rechazan con un mensaje claro, y `listarPendientes`
    (lo que ve Ruteo) excluye los retiros.
  - **Contrato:** `tipoEntrega` en la solicitud y en la respuesta; `direccionEntrega` pasa a
    ser obligatoria solo a domicilio (restricción de clase que reporta el error en ese campo).
    La huella de idempotencia distingue un retiro de un domicilio.
  - **Quien compró lo ve:** seguimiento con tres pasos (Recibido → Listo para retirar →
    Retirado), con el depósito y el código; los mails dicen "listo para retirar" en vez de
    "ya tiene quién lo lleve".
- **Consecuencias:** un retiro que nadie busca queda CONFIRMADO hasta que se cancele (no
  hay vencimiento ni cobro por almacenamiento todavía). La prueba de entrega es solo el
  código, más débil que la foto del remito; el código lo conoce quien tenga el enlace.

## ADR-054: Mejoras de diseño visual

- **Estado:** Aceptada (22/10/2026). Surge de una revisión del diseño de toda la aplicación.
- **Contexto:** la revisión encontró contraste insuficiente (texto blanco sobre el naranja de la
  marca: 2,8:1; el menú, 4,4:1), tablas muy anchas sin búsqueda, filtro ni orden, páginas
  públicas con anchos y botones distintos, estados que dependían solo del color, y una
  dependencia de Google Fonts en cada visita.
- **Decisión** (todo es presentación; no cambia ninguna regla de negocio):
  - **Contraste:** el texto de los botones naranjas pasa a azul marino (6,4:1; token
    `--sobre-naranja`), los enlaces naranja usan `--acento-texto` (5,2:1) y el texto del menú
    se aclara. Los temas claro y oscuro definen sus propios valores.
  - **Páginas públicas:** login y seguimiento comparten ancho, botón y color de ayuda, y se
    centran verticalmente.
  - **Fuentes propias:** Inter y Barlow Condensed (latin y latin-ext) en `/fuentes`, sin pedir
    nada a Google. Se sirven fuera de `resources` a propósito: la URL del CSS de JSF es
    relativa a `/jakarta.faces.resource/`.
  - **Tablas (`mejoras.js`):** encabezados con `scope`, nombre accesible, orden al hacer clic
    (o con teclado), y en tablas de 6 filas o más, buscador, filtro por estado y vista
    compacta que se recuerda. Todo se arma sobre lo que JSF ya dibuja y se repite tras un
    AJAX; sin JavaScript la tabla se ve igual.
  - **Accesibilidad:** los mensajes de error se anuncian (`role=alert`); los estados llevan un
    símbolo además del color.
  - **Ayuda plegable** ("Cómo funciona") en las pantallas con texto largo, y estados vacíos
    con ícono.
  - **Inicio del personal** (`personal/inicio.xhtml`, `InicioBean`): "Requiere atención"
    (lista de espera, liquidaciones sin cobrar, devueltos, por confirmar, retiros) y un resumen
    de la operación. Es la página a la que entra el personal.
  - **Seguimiento:** tilde en los pasos hechos y, en un retiro listo, un recuadro con el
    depósito y el código en grande.
  - **Menú lateral sin scroll propio** (23/10/2026): al sumar opciones (Inicio, Recepción de mercadería) el menú del
    administrador pasó a necesitar unos 820 px de alto y, en ventanas más bajas, la barra mostraba su propio
    scroll. Ahora se compacta por escalones según la altura (hasta 840 px y hasta 740 px) y, por debajo de 640 px,
    la barra se desplaza con la página en vez de tener scroll propio. Medido con el menú completo en alturas de
    600 a 900 px: antes había scroll entre 660 y 800 px, ahora en ninguna. Recursos a `1_22`.
  - Recursos a `1_20`.
- **Consecuencias:** quedó afuera dividir el CSS (2.900 líneas) en archivos y unificar los pares
  de colores de estado en una sola familia de tokens: se hace mejor con las pantallas a la vista.
  La búsqueda y el filtro actúan sobre la página cargada, no sobre la base: con miles de
  pedidos habrá que paginar en el servidor.

## ADR-055: Prueba de entrega reforzada: código de 4 dígitos y ubicación

- **Estado:** Aceptada (22/10/2026). Pedido del equipo (Trello). Referencias: Mercado Libre Flex
  (código) y Onfleet (firma y ubicación).
- **Contexto:** la única prueba de una entrega era la foto del remito. Una foto no demuestra
  que quien recibió sea quien compró, ni dónde estaba el repartidor.
- **Decisión:**
  - **Código:** al salir a reparto con un repartidor de Rabbit (`despacharPedido`) se genera un
    código de 4 dígitos (`CodigoDeEntrega`, `SecureRandom`) en `Pedido.codigoEntrega`. Viaja en el
    mail de "en camino" a quien recibe. Para marcar la entrega el repartidor tiene que ingresarlo:
    sin el código correcto no se puede. Un pedido sin código (anterior a este cambio, o de un
    transportista) no lo pide.
  - **Intentos:** son solo 10.000 combinaciones, así que se limitan a 5 por pedido
    (`Pedido.intentosDeCodigo`). Para que el intento fallido quede contado, el error es una
    `CodigoIncorrectoException` con `rollback = false` (el resto de las `ValidacionException` sí
    deshacen la transacción). Agotados los intentos, ni el código correcto sirve: lo entrega un
    administrador.
  - **Contingencia:** un ADMINISTRADOR puede registrar la entrega sin código (es la pantalla
    de Pedidos, para cuando el repartidor no pudo). El repartidor, no.
  - **Dónde se ve el código:** solo en el mail. No va en `PedidoDTO` (lo ven el repartidor y el
    seguimiento público), no se guarda en el aviso que ve el personal (se guarda la versión sin
    código) y no está en ninguna pantalla ni en la API. Sale del servicio por un único camino,
    `consultarCodigoDeEntrega`, que usa el aviso al destinatario.
  - **Ubicación:** al abrir "Entregar pedido", el celular toma la posición y se guarda junto al
    remito (`RemitoEntrega.latitud/longitud/precisionMetros`). Es opcional: sin permiso del
    navegador la entrega se registra igual, sin ubicación (bloquearla dejaría pedidos trabados).
    Se valida igual que la de una visita fallida (`EvidenciaDeVisita`). El personal la ve en
    Pedidos, con un enlace a un mapa.
- **Consecuencias:** el código es una prueba de presencia, no un secreto fuerte. Si el cliente
  perdió el mail, el repartidor no puede entregar y el pedido lo cierra un administrador. La
  ubicación se toma al abrir la ventana, no al enviar el formulario, y el navegador puede
  darla imprecisa: por eso se guarda con su precisión. No se compara contra la dirección de
  entrega (queda como mejora: marcar entregas lejos del domicilio).

## ADR-056: Etiquetas con QR por bulto

- **Estado:** Aceptada (22/10/2026). Pedido del equipo (Trello). Referencias: Andreani y Onfleet
  (escaneo al retirar y al entregar).
- **Contexto:** el repartidor retiraba marcando el pedido a mano en la pantalla. Una etiqueta pegada
  en cada caja permite identificar el bulto y marcar el retiro con la cámara.
- **Decisión:**
  - **Bultos:** nuevo dato opcional `bultos` en el alta del pedido (1 a 99; por defecto 1), guardado
    como `cantidadDeBultos`. No es `Pedido.getBultos()`, que ya existía y cuenta *unidades* para el
    límite de carga del repartidor: 40 remeras pueden ir en 2 cajas.
  - **Etiquetas:** `personal/etiquetas.xhtml?pedido=N` (botón "Etiquetas" en Pedidos, solo personal)
    arma una etiqueta de 100×60 mm por bulto con QR, "BULTO 2 DE 3", código de seguimiento, dirección,
    CP y quien recibe. El personal puede pedir otra cantidad (`&bultos=`) sin cambiar el pedido, por
    ejemplo para reimprimir una. Se imprime desde el navegador (`@page` de 100×60 mm, una por hoja).
  - **QR:** se dibuja en el navegador con `qrcode.js` (MIT, dentro del proyecto: no se pide nada a
    internet). Lleva `…/escaneo.xhtml?codigo=RB-…`, armado con `rabbit.url-publica` si está definida
    (lo impreso tiene que servir desde la calle). Verificado: un lector (jsQR) decodifica el link exacto.
  - **Escaneo:** `escaneo.xhtml` no muestra nada, solo reparte: un repartidor con sesión va a
    `repartidor/escaneo.xhtml` y cualquier otra persona, al seguimiento público. Esa pantalla busca el
    código **solo entre las entregas del propio repartidor**: si el pedido no es suyo, no se muestra
    nada de él. Si está por retirar, un botón marca el retiro (el mismo `despacharPedido` de siempre,
    con sus controles).
- **Consecuencias:** el QR contiene el código de seguimiento, que ya viaja en los mails y que da acceso
  solo a lo que muestra el seguimiento público. Escanear un bulto marca el retiro de todo el pedido,
  no de ese bulto: no se controla que estén todos. Un repartidor sin sesión que escanea llega al
  seguimiento público, no al login; tiene que tener abierta su sesión. El escaneo al entregar sigue
  siendo por Mis entregas (código de 4 dígitos y foto, ADR-055).

## ADR-057: Opciones de quien recibe desde el seguimiento

- **Estado:** Aceptada (22/10/2026). Pedido del equipo (Trello). Referencia: DHL On Demand Delivery.
- **Contexto:** quien recibe el pedido solo podía mirarlo. No podía pedir otro día, avisar cómo
  entrar al edificio ni autorizar a un vecino, y eso termina en visitas fallidas (ADR-041).
- **Decisión:** un formulario en `seguimiento.xhtml` ("¿Querés cambiar algo de la entrega?") con tres
  opciones, solo mientras el pedido no salió:
  - **Reprogramar** el día, hasta 7 días desde hoy y nunca antes de lo ya prometido (adelantar no se
    puede garantizar). Cambia `Pedido.fechaPrometida` y guarda la anterior en `fechaPrometidaOriginal`:
    la métrica "a tiempo" (ADR-044) se mide contra el día que se reprogramó, y el personal ve que fue
    reprogramado. No bloquea la confirmación antes de esa fecha: es un aviso visible (Pedidos y
    la hoja de ruta), no una regla.
  - **Instrucciones** ("dejar en portería", "tocar timbre 3B"): hasta 200 caracteres.
  - **Persona autorizada:** solo un nombre (hasta 100).
  El repartidor las ve en Mis entregas (`HojaDeRutaDTO`); el personal, en Pedidos.
- **Cuándo se puede** (`ReglaDeOpcionesDeEntrega`): pedido a domicilio PENDIENTE, o CONFIRMADO con un
  repartidor de Rabbit. No en un retiro en depósito, ni EN_CAMINO o resuelto, ni confirmado sin
  repartidor (lo lleva una empresa de logística, a la que no se le pasan estos datos). Un PENDIENTE
  después de una visita fallida sí puede: es justo cuando más sirve reprogramar.
- **Seguridad:** es una escritura pública (la autoriza el código de seguimiento, como la calificación
  del ADR-052), así que es mínima y acotada: tres datos cortos, texto de un tercero sin caracteres de
  control y siempre escapado al mostrarlo, tope de 5 cambios por pedido, y nada de lo escrito va al log
  (solo qué se cambió). Con el pedido bloqueado y el estado releído, si justo salió a reparto no se
  cambia nada. **Autorizar a otra persona no reemplaza el código de entrega** (ADR-055): la persona
  autorizada tiene que conocerlo para recibir el pedido, así que alguien que solo tenga el enlace no
  se puede hacer pasar por quien recibe.
- **Consecuencias:** quien tenga el enlace del seguimiento puede reprogramar o dejar instrucciones
  (no puede recibir el pedido). No se le avisa por mail a quien recibe que cambió algo: queda como
  mejora, y sería la mitigación natural contra un cambio hecho por un tercero. Reprogramar no
  retiene el pedido hasta esa fecha: lo decide el personal al confirmar y asignar.

## ADR-058: Órdenes de recepción de mercadería

- **Estado:** Aceptada (22/10/2026). Pedido del equipo (Trello). Referencia: ShipBob (orden de
  recepción con cantidades esperadas, recibidas y guardadas).
- **Contexto:** el stock consignado lo cargaba el personal al verlo entrar, sin que el depósito supiera qué
  esperar ni quedara registro de lo que faltó o sobró.
- **Decisión:** el comercio anuncia la mercadería desde Mi stock (producto, cantidad y peso de una unidad, a
  un depósito) y el personal registra lo que llegó, línea por línea, en `personal/recepciones.xhtml`.
  - **Estados:** `ANUNCIADA` → `RECIBIDA` (no faltó nada) o `PARCIAL` (en alguna línea llegó menos). Agregué
    `CANCELADA` (el comercio, o el personal si no llegó nada): sin ella una orden que nunca llega quedaría
    anunciada para siempre. Todos menos ANUNCIADA son finales.
  - **Stock:** al registrar la recepción se suma SOLO lo que llegó. Si el comercio ya tenía ese producto en
    ese depósito se le suma a esa fila (una por producto, depósito y comercio); si no, se crea con el peso
    del anuncio. Va en la misma transacción que el cambio de estado.
  - **Diferencias:** cada línea guarda lo esperado y lo recibido; se ven en las dos pantallas ("Faltaron 3",
    "Llegaron 2 de más"). Llegar de más no vuelve parcial la orden: se anota como diferencia.
  - **Reglas (`ReglaDeRecepcion`):** de 1 a 50 productos sin repetir (sin importar mayúsculas), cantidad de 1
    a 100.000, peso como en el alta de stock; al recibir hay que informar cada línea y no se registra una
    recepción en la que no llegó nada (eso es cancelar).
  - **Seguridad:** `anunciar` y `listarDelComercioActual` son del COMERCIO y siempre del comercio que llama (el
    ID sale de la sesión, no del formulario); `cancelar` del comercio solo sirve para lo suyo; registrar la
    recepción es del ADMINISTRADOR y el OPERADOR. La orden se lee con bloqueo, así dos registros a la vez no
    suman el stock dos veces. No se puede eliminar un comercio con órdenes anunciadas.
  - Tablas nuevas `ordenes_recepcion` y `lineas_recepcion` (las crea Hibernate al desplegar). El inicio del
    personal avisa cuántas órdenes esperan mercadería. Recursos a `1_21`.
- **Consecuencias:** la recepción se registra una sola vez: si lo que faltó llega después, el comercio
  anuncia otra orden. Un comercio puede anunciar cualquier cosa (no hay catálogo): el nombre lo escribe él y
  tiene que coincidir con el del stock para sumarse a la misma fila; si difiere en una letra se crea otra.
  No se avisa por mail al personal ni al comercio de cada cambio. No se controlan lotes ni vencimientos.

## ADR-059: Cobro por almacenamiento

- **Estado:** Aceptada (22/10/2026). Precios por defecto fijados el 26/10/2026 ($10, $30 y $90). Pedido del equipo
  (Trello). Referencia: Mercado Libre Full.
- **Contexto:** Rabbit cobraba solo el reparto (ADR-034). El lugar que ocupa la mercadería en el depósito es otro
  servicio, con otro costo, y un comercio con mucho stock que rota poco no pagaba nada por él.
- **Decisión:** se **suma** al modelo actual (abono + envíos + kilos): el comercio paga por unidad y por día guardada,
  según el tamaño del producto. Se hizo en dos entregas:
  1. **Medidas y categoría (no cobra nada).** `ItemInventario` suma alto, ancho y largo de una unidad (cm), obligatorias
     en el alta y opcionales al editar (las tres o ninguna; vacías = se conservan). La categoría (`CategoriaDeTamano`:
     pequeño, mediano, grande o fuera de rango) **no se carga**: se deduce del peso y las medidas y manda la más grande
     que le toque por cualquiera de sus datos. Límites "hasta" (inclusivos), configurables por system property. Un
     producto sin medidas cuenta como **mediano** (o más, si lo pesa): no sale gratis por un dato faltante, y se marca
     "medidas sin cargar". Lo fuera de rango no se acepta en consignación (ni al cargar stock ni al anunciar mercadería).
  2. **Foto diaria y cargo.** Un timer (`FotoDiariaDeOcupacion`, 23:55) guarda en `ocupacion_diaria` cuántas unidades de
     cada categoría tenía cada comercio ese día. Un día sin foto (WildFly apagado) se completa con la anterior
     (`UnidadesDia`); el día de hoy, con lo que hay ahora, así la vista previa llega hasta hoy. Cuentan las unidades
     disponibles, que ya incluyen las reservadas: una reservada sigue ocupando lugar hasta que sale. La liquidación guarda
     las unidades-día de cada categoría y el cargo por almacenamiento, con los importes del momento, y el total lo suma.
     Aparece como línea nueva en las dos liquidaciones (personal y comercio), también en la vista previa del mes en curso.
- **Precios:** `rabbit.tarifa.almacenamiento.pequeno|mediano|grande`, **por defecto $10, $30 y $90** por unidad y por día
  (ejemplo: 100 unidades pequeñas todo un mes de 30 días = 100 × 30 × $10 = $30.000). Se cambian sin redesplegar; con los tres
  en 0 el almacenamiento no se cobra y la liquidación lo muestra en $0 junto con las unidades-día. Rangos por defecto: pequeño
  hasta 1 kg, 25 cm y 3 L; mediano hasta 10 kg, 60 cm y 50 L; grande hasta 50 kg, 150 cm y 500 L; más que eso es fuera de rango.
- **Sin implementar (faltan sus valores):** días gratis al ingresar el stock y recargo por stock antiguo (la referencia de
  Mercado Libre Full: más de N días sin venderse). Quedan como ideas; hace falta definir cuántos días y cuánto.
  Tampoco se decidió desde qué mes se cobra (se recomendó: el siguiente a cargar las medidas).
- **Consecuencias:** al desplegar, todo el stock existente no tiene medidas y cuenta como mediano: conviene cargarlas antes de
  poner precios. La mercadería que entra por una orden de recepción (ADR-058) llega sin medidas (el anuncio no las pide) y
  también cuenta como mediana hasta que el personal las cargue. La primera foto es la del día del despliegue: lo anterior no
  se reconstruye (los meses ya liquidados no cambian). La foto es del día y no del instante: una unidad que entra y sale entre
  dos fotos no se cobra. Cobrar un mes con datos a medias sería injusto: como los precios ya vienen activos, conviene cargar las
  medidas del stock existente antes de que se liquide el mes.
