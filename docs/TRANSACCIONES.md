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

| Atributo | Dónde | Por qué |
|---|---|---|
| `REQUIRED` (default) | Casi todas las operaciones | Se suman a la transacción del llamador, o abren una |
| `REQUIRES_NEW` | `sincronizarPedidoExterno`, `descartarPedidoExterno` | El sincronizador procesa varias filas por pasada: si comparten transacción, una fila fallida la deja condenada y arrastra a las siguientes |
| `NOT_SUPPORTED` | `PublicadorPedidosExternos.publicarPedidoExternoRegistrado` | El envío JMS corre fuera de la transacción: si el broker falla, no deshace el pedido ya guardado |

## Flujo 1: sincronizar un pedido externo (implementado)

`PedidoService.sincronizarPedidoExterno`, `REQUIRES_NEW`:

| Paso | Si falla… |
|---|---|
| 1. Leer la fila externa con `PESSIMISTIC_WRITE` | Si ya está sincronizada: `PedidoYaSincronizadoException`, no cambia nada |
| 2. Validar comercio activo | Rollback; el MDB o el timer la descartan con motivo (`descartarPedidoExterno`, en su propia transacción) |
| 3. Por cada línea: reservar y confirmar stock (`IReservaStock`, se suma a la transacción) | Rollback de **todas** las reservas de las líneas anteriores: el pedido es todo o nada |
| 4. Guardar el `Pedido` (PENDIENTE, con importe y medio de pago) y disparar `EstadoPedidoCambiado` | Rollback de todo; el evento no se publica (observers `AFTER_SUCCESS`) |
| 5. Marcar la fila externa como sincronizada | Rollback de todo |

## Flujo 2: cambios de estado (implementado)

`confirmarPedido`, `despacharPedido`, `registrarEntrega` y
`cancelarPedido` (`REQUIRED`) validan la transición en
`EstadoPedido.puedePasarA` **antes** de escribir. `cancelarPedido`
valida primero y después devuelve el stock línea por línea: si falla
una devolución, se deshacen también las anteriores.

## Flujo 3: confirmar con cobro (implementado)

`confirmarPedido`, `REQUIRED`, en una única transacción:

| Paso | Si falla… |
|---|---|
| 1. Validar que el pedido esté PENDIENTE | No cambia nada |
| 2. `IAsignacionRepartidores.asignarRepartidor` | Sin repartidor disponible: rollback, el pedido sigue PENDIENTE |
| 3. `IRegistroCobros.registrarCobro` | Pago rechazado: rollback **también de la asignación del paso 2**; el repartidor vuelve a quedar libre sin código de compensación |
| 4. Estado CONFIRMADO + `EstadoPedidoCambiado` | Rollback de todo |
| 5. Publicación en el tópico (`AFTER_SUCCESS` + `NOT_SUPPORTED`) | Se loguea; no deshace la confirmación (misma decisión que ADR-002) |

**Por qué no hace falta XA:** las tres escrituras (repartidor, cobro,
pedido) van a la misma base; el mensaje JMS queda fuera de la
transacción a propósito.

Probado: sin repartidores, confirmar deja el pedido PENDIENTE y sin
cobro; con un PREPAGO de más de $500.000 (límite de la pasarela
simulada), la pasarela lo rechaza y el repartidor que se había asignado
vuelve a DISPONIBLE solo por el rollback.

Las excepciones de Repartidores y Pagos son `@ApplicationException(rollback
= true)` propias de cada componente; `PedidoService` las traduce a la
`ValidacionException` de Pedidos para que la vista muestre el motivo.

## Flujo 4: entregar y cancelar un pedido confirmado (implementado)

| Operación | Qué hace, en la misma transacción |
|---|---|
| `registrarEntrega` | Estado ENTREGADO + libera al repartidor. El cobro CONTRA_ENTREGA **no** se hace acá: lo acredita Pagos al recibir el evento por el tópico, en su propia transacción |
| `cancelarPedido` (desde CONFIRMADO) | Anula el cobro (`ADMINISTRADOR`), libera al repartidor, devuelve el stock y pasa a CANCELADO. Si un OPERADOR lo intenta, `EJBAccessException` y no se cancela nada |
