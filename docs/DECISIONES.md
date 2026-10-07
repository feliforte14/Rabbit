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
  `PedidoService.cambiarEstado`. No se usa el patrón State completo.
- **Consecuencias:** una sola regla para validar y para disparar el
  evento de cambio de estado. `cancelarPedido` deja de aceptar pedidos
  `EN_CAMINO` o `ENTREGADO`.
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
  para la Entrega Final. *(El agrupamiento por zona se hizo en ADR-017.)*

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
    modelo del repartidor (hoy lleva un pedido por viaje) y ordenar por
    distancia necesita coordenadas. Queda fuera del alcance.
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
  - **Contrato OpenAPI** en `docs/openapi.yaml` (contract-first: se
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

- **Estado:** Aceptada. Completa ADR-016 (polling).
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

- **Estado:** Aceptada. Reemplaza el SHA-256 sin salt de la tabla `usuarios`.
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
