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
  rama y redesplegar. La rama hay que mantenerla al día con `main`.

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
    en las altas, con "Corregir peso" para lo ya cargado). Un pedido de
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
  - Pantalla "Liquidaciones mensuales" (personal): lo cobrado por mes, el
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
  "Testing NO OK").
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
  - **Hora de corte:** system property `rabbit.ruteo.hora-corte` (por
    ejemplo `09:00`), leída en cada uso (`HoraDeCorte`). Desde esa hora no
    se suman pedidos a la hoja del día aunque haya lugar: lo que se
    confirma después va a la lista de espera y se asigna solo al día
    siguiente, antes del corte. **Sin la propiedad no hay corte**, para no
    romper demos ni pruebas a la tarde.
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
  - *Corte por defecto a las 9:00:* después de esa hora ninguna prueba
    asignaría pedidos.
- **Consecuencias:** las columnas nuevas de `pedidos` admiten NULL y las
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
