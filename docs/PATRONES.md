# Patrones de diseño

Para cada patrón: qué problema concreto resuelve en Rabbit, dónde está y
qué alternativa se descartó.

## DAO (Repository)

- **Problema:** que las reglas de negocio no dependan de JPQL ni del
  `EntityManager`.
- **Dónde:** un repositorio por componente: `ComercioRepository`,
  `InventarioRepository`, `PedidoRepository`, `CobroRepository`,
  `RepartidorRepository`, `NotificacionRepository`, `UsuarioRepository`,
  `TransportistaRepository` y `ZonaRepository` (Ruteo).
  `ProductoRepository` también existe, pero el catálogo de productos
  todavía no tiene servicio que lo use.
- **Cómo:** el repositorio es la única clase que toca el `EntityManager`.
  También encapsula decisiones de acceso, como el bloqueo
  `PESSIMISTIC_WRITE` de `buscarPedidoExternoParaActualizar`.
- **Descartado:** usar el `EntityManager` directo en los EJB (mezcla
  consultas con reglas).

## DTO

- **Problema:** exponer entidades JPA a la vista o a otro componente
  acopla todo al esquema y rompe con carga perezosa fuera de la
  transacción.
- **Dónde:** `dto/` de cada componente (`ComercioDTO`, `PedidoDTO`,
  `DatosPedidoExternoDTO`, `ReservaStockDTO`…).
- **Regla:** entrada `Datos*DTO`, salida `*DTO` con `desde(entidad)`.

## Facade

- **Problema:** sincronizar un pedido coordina tres componentes (validar
  comercio, reservar stock, crear el pedido); quien la dispara no debería
  conocer esa coordinación.
- **Dónde:** `IGestionPedidos` / `PedidoService`. Con la misma idea,
  `IRegistroComercios`, `IConsultaStock`, `IRegistroUsuarios`.
- **Beneficio visible:** la cola (MDB) y el polling (timer) llaman al
  mismo `sincronizarPedidoExterno`, y la pantalla y la API REST al mismo
  `registrarPedidoExterno`; la regla no se duplica.

## Strategy

- **Hoy:** el comportamiento según `OrigenPedido` (`STOCK_CONSIGNADO`
  reserva stock; `PUNTO_PICKING` solo valida el punto de picking).
- **Planificado:** el cobro según `MedioPago` en ServicioDePagosYCobranzas
  (`PREPAGO` se cobra en el banco legado, `CONTRA_ENTREGA` queda
  pendiente hasta la entrega). Hoy está implementado con un `if` en
  `PagoService.registrarCobro`; con solo dos medios alcanza, y pasaría a
  una estrategia por medio de pago si se suman más.
- **Despacho por zona:** `RuteoService.despacharPedido` elige cómo sale
  el pedido según la `CoberturaZona` (`PROPIA`: repartidor de la zona o
  respaldo; `TRANSPORTISTA`: derivar). También es un `if`, por la
  misma razón: dos casos.

## Singleton

- **Problema:** una tarea periódica corriendo en varias instancias a la
  vez procesaría las mismas filas dos veces.
- **Dónde:** `BarredorDeReservas` y `SincronizadorDePedidos`
  (`@Singleton @Startup` + `@Schedule`). También `CircuitBreakerBanco`,
  cuyo estado tiene que ser uno solo para todas las llamadas al banco, y
  `AlineadorDeRestriccionesEnum` (`@Singleton @Startup`), que corre una
  sola vez al desplegar. `SeguimientoDeEnvios` (`@Singleton @Startup` +
  `@Schedule`) consulta a los transportistas en una sola pasada a la vez.

## Provider (`Instance<T>` como fábrica)

- **Problema:** `PedidoService` es stateless y usa `IReservaStock`, que es
  stateful. Inyectarlo como campo compartiría una misma conversación
  entre pedidos procesados en paralelo.
- **Dónde:** `PedidoService.reservaProvider`: `get()` crea una instancia
  nueva por línea y `destroy()` la descarta.

## Observer (eventos CDI)

- **Problema:** `PedidoService` no debería conocer a quien publica
  mensajes JMS, y el mensaje tiene que salir solo si la transacción se
  confirmó.
- **Dónde:**
  - `PedidoExternoRegistrado` → `PublicadorPedidosExternos`
    (`@Observes(during = AFTER_SUCCESS)`). Implementado.
  - `EstadoPedidoCambiado` → `PublicadorEstadosPedido`, que lo publica
    en `topico.pedidos.estado`. Implementado.
  - `EliminacionDeComercio` (sincrónico): antes de eliminar un comercio,
    `ComercioService` pregunta si algo todavía lo referencia. Pedidos,
    Inventario y Seguridad lo observan y anotan un impedimento si tienen
    pedidos, stock consignado o cuentas de ese comercio. Comercios no
    depende de ellos (ya dependen de él), y nada queda apuntando a un
    comercio inexistente. Implementado.
  - `EstadoEnvioCambiado` (sincrónico): Transportistas avisa que un
    transportista informó un estado nuevo; Pedidos
    (`ActualizacionDePedidosPorEnvio`) mueve el pedido en la misma
    transacción. Transportistas no depende de Pedidos. Implementado.
- **Y entre componentes, publicación/suscripción:** del otro lado del
  tópico, `SuscriptorPagosEstadoPedido` y
  `SuscriptorNotificacionesEstadoPedido` reaccionan al mismo evento sin
  que Pedidos los conozca.
- **Descartado:** llamar al publicador directo desde el servicio (ver
  ADR-002 en [DECISIONES.md](DECISIONES.md)).

## Adapter

- **Problema:** que la lógica de negocio no dependa de la tecnología con
  la que habla un sistema externo (SOAP, EDI, REST).
- **Dónde:** `BancoClient` implementa `IBancoClient`
  (`autorizar → ResultadoAutorizacion`, `reversar`). `PagoService` no ve
  JAX-WS, `BindingProvider` ni los Faults; si el banco pasara a REST, solo
  cambia el Adapter.
- **Y con varias tecnologías a la vez:** `IAdaptadorTransportista` tiene
  dos implementaciones, `AdaptadorRestTransportista` (API REST con JSON) y
  `AdaptadorSoapTransportista` (SOAP legado). Cada una traduce protocolo,
  formato y hasta el vocabulario de estados de su transportista (el legado
  dice `EN_VIAJE`, Rabbit `EN_TRANSITO`). `AdaptadoresTransportista` elige
  según el `TipoIntegracion` del transportista; `TransportistaService` no
  sabe con cuál habla. Sumar un transportista EDI sería una implementación
  más.

## Máquina de estados (State simplificado)

- **Problema:** las reglas de "desde qué estado se puede pasar a cuál"
  estaban repartidas en `if` dentro de cada operación.
- **Dónde:** `EstadoPedido.puedePasarA(...)` +
  `PedidoService.cambiarEstado(...)`.
- **Por qué no el patrón State completo** (una clase por estado): con
  cinco estados sin comportamiento propio, un `switch` en el enum es más
  simple de explicar y de mantener.

## Transacción compensatoria

- **Problema:** un rollback deshace lo que Rabbit escribió en su base,
  pero no lo que ya hizo un sistema externo: si el banco cobró y después
  la confirmación falla, el cliente quedaría cobrado.
- **Dónde:** `ReversasBancarias` observa `PagoAutorizado` con
  `AFTER_FAILURE` y le pide al banco `reversarPago`. Con el mismo
  mecanismo, `CancelacionesDeEnvios` cancela en el transportista un envío
  que ya había tomado si la derivación se deshace (`EnvioSolicitado` +
  `AFTER_FAILURE`), y avisa las cancelaciones confirmadas (`EnvioCancelado`
  + `AFTER_SUCCESS`).
- **Descartado:** meter al banco en una transacción distribuida (XA/2PC):
  un sistema legado por SOAP no participa de la transacción de Rabbit.

## Circuit Breaker

- **Problema:** con el banco legado colgado, cada confirmación PREPAGO
  espera los 5 s del timeout para fallar igual; con carga, los hilos
  bloqueados agotan el pool y la caída del banco tira al resto de Rabbit.
- **Dónde:** `CircuitBreakerBanco` (`@Singleton`), consultado por
  `BancoClient` antes de cada llamada SOAP. Tras 3 fallas seguidas se
  abre y las llamadas fallan al instante; a los 30 s deja pasar una de
  prueba. Ver [MENSAJERIA-SINCRONICA.md](MENSAJERIA-SINCRONICA.md).
- **Descartado:** `@CircuitBreaker` de MicroProfile Fault Tolerance: no
  viene en `standalone-full` de WildFly (ADR-011).
