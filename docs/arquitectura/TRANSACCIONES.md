# Transacciones

## Reglas generales

- Los EJB usan **`@TransactionAttribute`** (`jakarta.ejb`), nunca
  `@Transactional` (`jakarta.transaction`): esa es la anotación de los
  beans CDI y sobre un EJB se ignora sin avisar (un
  `@Transactional(REQUIRES_NEW)` seguiría en la transacción del llamador).
  Las operaciones con `REQUIRED`, que es el default, lo llevan igual
  explícito para que se lea en el código. Hasta el 28/09/2026 había
  `@Transactional` en todos los servicios; se reemplazó sin cambiar el
  comportamiento.
- Las excepciones de negocio (`ValidacionException`,
  `PedidoYaSincronizadoException`) son
  `@ApplicationException(rollback = true)`: se propagan tal cual al
  llamador **y** deshacen la transacción.

## Atributos usados

- `REQUIRED` (default)
  - Dónde: Casi todas las operaciones
  - Por qué: Se suman a la transacción del llamador, o abren una
- `REQUIRES_NEW`
  - Dónde: `sincronizarPedidoExterno`, `descartarPedidoExterno`
  - Por qué: El sincronizador procesa varias filas por pasada: si comparten transacción, una fila fallida la deja condenada y arrastra a las siguientes
- `NOT_SUPPORTED`
  - Dónde: `PublicadorPedidosExternos` y `PublicadorEstadosPedido`
  - Por qué: El envío JMS corre fuera de la transacción: si el broker falla, no deshace lo ya guardado
- `NOT_SUPPORTED`
  - Dónde: `ReversasBancarias`
  - Por qué: La reversa en el banco corre después de que la transacción de Rabbit terminó (se deshizo o se confirmó)
- `NOT_SUPPORTED`
  - Dónde: `SincronizadorDePedidos.sincronizarPendientes`
  - Por qué: La pasada del timer no abre transacción: cada fila se sincroniza en la suya (`REQUIRES_NEW`), así una fila fallida no arrastra a las demás
- `AFTER_SUCCESS` (observer)
  - Dónde: `AvisosPorMail`
  - Por qué: El mail del aviso sale recién después del commit, y fuera de la transacción: un servidor de correo caído no deshace el aviso
- `NOT_SUPPORTED`
  - Dónde: `PedidoService.cotizarDerivacion`
  - Por qué: Lee el pedido (con sus líneas, en una sola consulta) y le pide precios a los transportistas sin abrir transacción: no retiene una conexión a la base mientras espera hasta 5 s por transportista
- `NOT_SUPPORTED`
  - Dónde: `TransportistaService.cotizarEnvio`
  - Por qué: Solo pregunta precios a los transportistas: no escribe nada y no retiene una transacción mientras espera respuestas de afuera
- `NOT_SUPPORTED`
  - Dónde: `CancelacionesDeEnvios`, `SeguimientoDeEnvios`
  - Por qué: Las llamadas a los transportistas corren fuera de la transacción de Rabbit (compensaciones y consultas de estado)
- `REQUIRES_NEW`
  - Dónde: `TransportistaService.registrarNovedad`
  - Por qué: Cada novedad de un envío (y el cambio de estado del pedido que dispara) en su propia transacción: una que falla no arrastra a las demás de la pasada
- `NOT_SUPPORTED`
  - Dónde: `RuteoService.despacharPedido` / `despacharZona`
  - Por qué: El despacho no abre transacción: cada pedido se confirma o deriva en la suya (la de `confirmarPedidoEnZona` o `derivarATransportista`), así un pedido que falla (por ejemplo, el cobro) no deshace los demás de la zona. Ver ADR-017
- `NOT_SUPPORTED`
  - Dónde: `PedidosExternosResource`, `SeguimientoResource` (API REST)
  - Por qué: El recurso no abre transacción: cada operación de negocio confirma la suya, así un error al confirmar (dos reintentos simultáneos con la misma `Idempotency-Key` chocan contra la restricción única) llega al recurso, que reintenta y responde bien, en vez de explotar después de que el método terminó. Ver ADR-018
- `NOT_SUPPORTED` + `REQUIRES_NEW`
  - Dónde: `AsignadorDeEspera` → `PedidoService.asignarDesdeEspera` / `sacarDeEspera`
  - Por qué: La pasada por la lista de espera no abre transacción; cada pedido se asigna (cobro + repartidor) o sale de la fila en la suya, así uno que falla no frena a los demás (ADR-037)
- `AFTER_SUCCESS` (observer)
  - Dónde: `AvisosDeEspera`
  - Por qué: Después del commit de una entrega, cancelación o devolución programa una pasada de la lista de espera en un timer: no demora la pantalla de quien hizo el cambio ni corre con su usuario
- `NOT_SUPPORTED`
  - Dónde: `CircuitBreakerBanco`
  - Por qué: Solo cambia estado en memoria: no tiene nada que hacer en la transacción del llamador

## Flujo 1: sincronizar un pedido externo (implementado)

`PedidoService.sincronizarPedidoExterno`, `REQUIRES_NEW`:

- **1. Leer la fila externa con `PESSIMISTIC_WRITE`**: Si ya está sincronizada: `PedidoYaSincronizadoException`, no cambia nada
- **2. Validar comercio activo**: Rollback; el MDB o el timer la descartan con motivo (`descartarPedidoExterno`, en su propia transacción)
- **3. Por cada línea: reservar y confirmar stock (`IReservaStock`, se suma a la transacción)**: Rollback de **todas** las reservas de las líneas anteriores: el pedido es todo o nada
- **4. Guardar el `Pedido` (PENDIENTE, con importe, medio de pago y dirección de entrega) y disparar `EstadoPedidoCambiado`**: Rollback de todo; el evento no se publica (observers `AFTER_SUCCESS`)
- **5. Marcar la fila externa como sincronizada**: Rollback de todo

## Flujo 2: cambios de estado (implementado)

`confirmarPedido`, `despacharPedido`, `registrarEntrega` y
`cancelarPedido` (`REQUIRED`) validan la transición en
`EstadoPedido.puedePasarA` **antes** de escribir. `cancelarPedido`
valida primero y después devuelve el stock línea por línea: si falla
una devolución, se deshacen también las anteriores.

## Flujo 3: confirmar con cobro (implementado)

`confirmarPedido`, `REQUIRED`, en una única transacción:

- **1. Validar que el pedido esté PENDIENTE**: No cambia nada
- **1b. `IAsignacionRepartidores.motivoParaEsperar` (sin bloqueo, ADR-037)**: Nadie tiene lugar o pasó la hora de corte: el pedido queda en la lista de espera, **sin cobrar**, y la transacción confirma solo esa marca
- **2. `IRegistroCobros.registrarCobro` (PREPAGO: `autorizarPago` en el banco por SOAP), salvo que ya tenga cobro (devuelto al depósito)**: Banco rechaza o no responde: rollback, el pedido sigue PENDIENTE
- **3. `IAsignacionRepartidores.asignarRepartidor`**: Otro se quedó con el lugar justo después del paso 1b: rollback en Rabbit **y reversa en el banco** (el rollback no alcanza al sistema externo)
- **4. Estado CONFIRMADO + `EstadoPedidoCambiado`**: Rollback de todo
- **5. Publicación en el tópico (`AFTER_SUCCESS` + `NOT_SUPPORTED`)**: Se loguea; no deshace la confirmación (misma decisión que ADR-002)

**Por qué no hace falta XA:** las tres escrituras (repartidor, cobro,
pedido) van a la misma base; el mensaje JMS queda fuera de la
transacción a propósito. El banco tampoco participa de la transacción
(un sistema legado por SOAP no puede): lo que hace se compensa con una
reversa.

Probado: sin repartidores con lugar, el pedido va a la lista de espera
sin llamar al banco (antes de ADR-037 se cobraba, se hacía rollback y se
pedía la reversa; esa reversa sigue cubriendo la carrera del paso 3 y la
cancelación de un prepago); con un PREPAGO de más de $500.000 el
banco lo rechaza y no hay nada que reversar; con el banco caído el pedido
sigue PENDIENTE (tras 3 fallas seguidas el circuit breaker corta al
instante, sin esperar el timeout). Ver [MENSAJERIA-SINCRONICA.md](../integraciones/MENSAJERIA-SINCRONICA.md).

Las excepciones de Repartidores y Pagos son `@ApplicationException(rollback
= true)` propias de cada componente; `PedidoService` las traduce a la
`ValidacionException` de Pedidos para que la vista muestre el motivo.

## Flujo 4: entregar y cancelar un pedido confirmado (implementado)

- `registrarEntrega`: Guarda la foto del remito (ADR-033), estado ENTREGADO + le saca el pedido al repartidor (borra su `CargaAsignada`; si era el último, queda DISPONIBLE). El cobro CONTRA_ENTREGA **no** se hace acá: lo acredita Pagos al recibir el evento por el tópico, en su propia transacción
- `cancelarPedido` (desde CONFIRMADO): Anula el cobro (`ADMINISTRADOR`), libera al repartidor, devuelve el stock y pasa a CANCELADO. Si era PREPAGO, después del commit se pide la reversa al banco. Si un OPERADOR lo intenta, `EJBAccessException` y no se cancela nada
- `devolverADeposito` sin visitas restantes (ADR-041): Libera al repartidor, devuelve el stock consignado y pasa a DEVUELTO_AL_COMERCIO. El cobro **no** se anula acá (lo pide un repartidor, que no puede anular cobros): lo anula Pagos al recibir el evento por el tópico, en su propia transacción, y si era PREPAGO pide la reversa después de ese commit. Si Pagos está caído, la suscripción durable guarda el evento

## Flujo 5: alta de usuario (implementado)

`UsuarioService.registrarUsuario`, `REQUIRED`. Desde ADR-031 la cuenta
vive solo en la tabla `usuarios` (perfil y hash bcrypt de la contraseña),
que es lo que lee WildFly al loguear: todo queda dentro de una
transacción.

- **1. Validar username, contraseña y a quién representa la cuenta**: No cambia nada
- **2. Guardar la fila en `usuarios`, con el hash**: Rollback: la cuenta no existe en ningún lado

Antes de ADR-031 la contraseña iba a los archivos del realm de WildFly,
fuera de la transacción, y hacía falta una `Synchronization` que la
quitara si la transacción se deshacía. Ya no: `darDeBaja`,
`reactivarUsuario` y `restablecerClave` también son una sola escritura en
la base. La baja además borra la línea vieja de los archivos de esa
máquina, si la había; si eso falla, la transacción se deshace y la cuenta
sigue activa.

`eliminarComercio` también es una sola transacción: el evento
`EliminacionDeComercio` corre adentro (observers sincrónicos) y, si algún
componente anota un impedimento, no se borra nada. Si no, `ComercioEliminado`
borra el historial terminado del comercio en cada componente y después se
borra el comercio: si cualquier paso falla, se deshace todo.
`devolverADeposito` (ADR-037), `REQUIRED`: libera al repartidor, marca el
pedido como devuelto (repartidor excluido, cobro registrado, en espera) y
lo pasa a PENDIENTE en una sola transacción. La reasignación corre después
del commit, en otra (la de `asignarDesdeEspera`), sin volver a cobrar.

`eliminarRepartidor` y `eliminarZona` (ADR-036) siguen el mismo esquema:
el repartidor se bloquea (`SELECT ... FOR UPDATE`) para que no se le
asigne un pedido mientras se lo elimina, y soltar sus pedidos terminados o
dejar sin zona a los repartidores pasa en la misma transacción que el
borrado.

## Flujo 6: derivar un pedido a un transportista (implementado)

`PedidoService.derivarATransportista`, `REQUIRED`, en una única
transacción. Es la alternativa a `confirmarPedido` cuando el pedido lo
lleva una empresa de envíos externa:

- **1. Validar que el pedido esté PENDIENTE**: No cambia nada
- **2. `IRegistroCobros.registrarCobro` (PREPAGO: se cobra en el banco)**: Banco rechaza o no responde: rollback, el pedido sigue PENDIENTE
- **3. `IEnvios.solicitarEnvio`: el transportista toma el envío y se guarda con su código de seguimiento**: Transportista rechaza, no responde o está de baja: rollback **y reversa en el banco** si ya se había cobrado
- **4. Estado CONFIRMADO + `EstadoPedidoCambiado`**: Rollback de todo **y cancelación del envío en el transportista** (`EnvioSolicitado` + `AFTER_FAILURE`)

Después, cada novedad que informa el transportista se registra con
`registrarNovedad` (`REQUIRES_NEW`): primero Pedidos mueve el pedido
(`EstadoEnvioCambiado`, que bloquea su fila) y recién después se bloquea,
relee y escribe el envío, en la misma transacción. Si mover el pedido
falla, o el envío cambió mientras tanto (lo cancelaron), se deshace todo
y el seguimiento la reintenta en la próxima pasada.

Cancelar un pedido derivado (desde CONFIRMADO) cancela también el envío;
al transportista se le avisa recién cuando la cancelación queda
confirmada (`EnvioCancelado` + `AFTER_SUCCESS`).

Probado en la app: derivación a los dos transportistas simulados (REST y
SOAP), seguimiento hasta ENTREGADO con el cobro contra entrega acreditado
por el tópico, y cancelación de un pedido derivado con la anulación en el
transportista. No se forzó una falla en el paso 4 para ver la
compensación.

## Bloqueo del pedido al cambiar su estado

Todas las operaciones que cambian el estado de un pedido (confirmar,
derivar, despachar, entregar, cancelar, y la cancelación del ERP) lo leen
con `PESSIMISTIC_WRITE` (`PedidoRepository.buscarPedidoParaActualizar`):
`SELECT ... FOR UPDATE` hasta el fin de la transacción. Si dos llegan a la
vez (el ERP cancela mientras el personal confirma, o se despacha la zona),
la segunda espera y después ve el estado real: o se confirma y el ERP
recibe `409`, o se cancela y la confirmación falla por la transición
inválida. Antes, las dos leían `PENDIENTE` y la última en confirmar pisaba
a la otra (por ejemplo, un pedido `CONFIRMADO` con el stock ya devuelto).

El bloqueo hace `flush` y vuelve a leer el pedido bloqueado: si la misma
transacción ya lo había cambiado (el seguimiento de envíos lo pasa a
`EN_CAMINO` y enseguida a `ENTREGADO`), ese cambio no se pierde, y si ya
estaba cargado se trabaja con el estado real de la base. (Con `refresh`
con bloqueo, Hibernate 7.4.5 falla sobre un pedido con sus líneas
cargadas; por eso se lo saca del contexto y se lo vuelve a leer.)

**Orden de bloqueo:** cancelar un pedido derivado bloquea el pedido y
después el envío; las novedades del transportista (polling) siguen
el mismo orden — Pedidos mueve el pedido antes de que se bloquee
el envío. Antes el seguimiento bloqueaba al revés y, si coincidían sobre
el mismo pedido en el mismo instante, PostgreSQL abortaba una de las dos
por deadlock. Con el mismo orden en los dos caminos, la segunda espera y
ve el estado real: un envío ya entregado no se cancela, y una novedad
sobre un pedido recién cancelado se rechaza (`409`, reintento).

Lo mismo en Inventario: cerrar una reserva (confirmar, liberar, devolver
o expirar) la lee con `PESSIMISTIC_WRITE`, y cada escritura del item hace
`flush` para atrapar la `OptimisticLockException` del `@Version` dentro
del método y traducirla a un mensaje ("otro usuario modificó el stock,
volvé a intentar"). El barredor expira cada reserva vencida en su propia
transacción: un choque sobre un item no deshace la pasada entera.

## Flujo 6: liquidación mensual (implementado, ADR-034)

`LiquidacionService.liquidarMes`, `NOT_SUPPORTED`: no abre una
transacción propia y recorre los comercios activos. Por cada uno:

- **1. Calcular entregas y kilos del mes (Pedidos)**
  - Transacción: Sin transacción
  - Si falla…: No se crea nada
- **2. `CobroDeLiquidaciones.crear`: la liquidación, PENDIENTE**
  - Transacción: `REQUIRES_NEW`
  - Si falla…: Rollback de ese comercio; los demás siguen
- **3. `CobroDeLiquidaciones.cobrar`: banco (SOAP) y resultado**
  - Transacción: `REQUIRES_NEW`, con la fila bloqueada
  - Si falla…: El banco no se deshace: por eso la liquidación ya existe (paso 2) antes de llamarlo. Si no responde, queda PENDIENTE y el timer la reintenta

Correrlo dos veces no duplica: `crear` no hace nada si el comercio ya
tiene la de ese mes (y hay una restricción única por comercio y período).

