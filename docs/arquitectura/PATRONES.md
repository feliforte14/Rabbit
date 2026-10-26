# Patrones de diseño

Para cada patrón: qué problema concreto resuelve en Rabbit, dónde está y
qué alternativa se descartó.

## Catálogo

Todos los patrones que se aplican, de un vistazo. Los que tienen sección propia más abajo llevan enlace; el resto se
explican en una línea.

### GoF

| Patrón | Dónde está | Detalle |
|---|---|---|
| **State** (comportamiento) | `EstadoDelPedido` y sus seis estados, en `pedidos/datos/model` | [Sección](#máquina-de-estados-state) |
| **Observer** (comportamiento) | Eventos CDI (`EstadoPedidoCambiado`, `PedidoExternoRegistrado`, `EstadoEnvioCambiado`) y eventos síncronos de eliminación | [Sección](#observer-eventos-cdi) |
| **Adapter** (estructural) | `IAdaptadorTransportista` con su adaptador REST y su adaptador SOAP legado; el cliente del banco legado | [Sección](#adapter) |
| **Facade** (estructural) | `IGestionPedidos`, `IConsultaStock`, `IRegistroComercios`, `IRegistroUsuarios` | [Sección](#facade) |
| **Singleton** (creacional) | `@Singleton @Startup` del contenedor: timers, `CircuitBreakerBanco`, `AsignadorDeEspera` | [Sección](#singleton) |
| **Factory / Provider** (creacional) | `Instance<IReservaStock>` en `PedidoService`; métodos de fábrica estáticos como `EstadoDelPedido.de(...)` y `TarifaMensual.vigente()` | [Sección](#provider-instancet-como-fábrica) |
| **Chain of Responsibility** (comportamiento) | `FiltroHsts`, un filtro de servlet dentro de la cadena de filtros | Una línea: agrega la cabecera HSTS y deja seguir la cadena |
| **Proxy** (estructural) | Lo provee el contenedor (EJB y CDI); no hay código propio | — |

### GRASP y principios

| Patrón | Dónde está |
|---|---|
| **Fabricación pura** | `ReglaDeCancelacion`, `ReglaDeCalificacion`, `ReglaDeOpcionesDeEntrega`, `ReglaDeRetiro`: clases sin estado ni contenedor que se prueban solas |
| **Controlador** | `PedidoService` coordina el caso de uso; los beans de JSF y los recursos REST solo traducen |
| **Indirección y Variaciones protegidas** | Las interfaces `I*` entre componentes, `IAdaptadorTransportista`, `IBancoClient` y los eventos |
| **Bajo acoplamiento** | Los componentes se hablan por interfaces inyectadas, sin `new` de servicios concretos |

### Arquitectura (PoEAA)

| Patrón | Dónde está | Detalle |
|---|---|---|
| **Repository / DAO** | Un repositorio por componente (`PedidoRepository`, …) | [Sección](#dao-repository) |
| **DTO** | Las clases `*DTO` entre capas y hacia la API | [Sección](#dto) |
| **Service Layer** | Los servicios `@Stateless` (`PedidoService`, `PagoService`, …) | — |
| **MVC / Backing Bean** | Vistas JSF (`.xhtml`) y beans de presentación | — |
| **Data Mapper y Unit of Work** | JPA: las entidades y el `EntityManager` | — |
| **Value Object** | `record` inmutables, por ejemplo `TarifaMensual` | — |
| **Exception Mapper** | `ProblemaMapper`: todos los errores de la API salen como Problem Details (RFC 9457) | [Sección](#exception-mapper-traductor-de-errores) |

### Integración y mensajería (EIP)

| Patrón | Dónde está | Detalle |
|---|---|---|
| **Publish/Subscribe** | El tópico `topico.pedidos.estado` y sus suscriptores (Notificaciones, Pagos, Destinatario) | [Observer](#observer-eventos-cdi) |
| **Point-to-Point Channel y Message-Driven Bean** | La cola `cola.pedidos.externos` y `PedidoExternoListener` | — |
| **Idempotent Receiver** | `Idempotency-Key` más una huella del pedido y una restricción única | [Sección](#idempotency-key) |
| **Polling de respaldo** | `SincronizadorDePedidos`, por timer, por si falla la cola | — |
| **Gateway** | `BancoClient` oculta el SOAP del banco legado | — |

### Resiliencia y concurrencia

| Patrón | Dónde está | Detalle |
|---|---|---|
| **Circuit Breaker** | `CircuitBreakerBanco` | [Sección](#circuit-breaker) |
| **Timeout y degradación controlada** | Corte a los 5 s; la falta de respuesta viaja como resultado (`NO_DISPONIBLE`) | — |
| **Transacción compensatoria** | `ReversasBancarias` y `CancelacionesDeEnvios` | [Sección](#transacción-compensatoria) |
| **Bloqueo pesimista** | Stock y estado del pedido (`PESSIMISTIC`) | — |

### Lo que no se aplica

- **Strategy real:** no hay clases intercambiables (ver la aclaración en la sección Strategy).
- **Builder, Decorator, Command, Template Method y Transactional Outbox:** no aparecen en el código.

## DAO (Repository)

- **Problema:** que las reglas de negocio no dependan de JPQL ni del
  `EntityManager`.
- **Dónde:** un repositorio por componente: `ComercioRepository`,
  `InventarioRepository`, `PedidoRepository`, `CobroRepository`,
  `RepartidorRepository`, `NotificacionRepository`, `UsuarioRepository`,
  `TransportistaRepository` y `ZonaRepository` (Ruteo).
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
- **En el borde de la API REST:** `PedidoExternoRequest` es el contrato
  público del alta, separado de `DatosPedidoExternoDTO` (el del formulario
  JSF): lleva las reglas de Bean Validation y no tiene `idComercio`. Se
  convierte con `aDatos()` antes de llegar al negocio.

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

- **Aclaración:** no hay clases Strategy intercambiables. Los tres
  casos de abajo se resuelven con un `if`, porque cada uno tiene solo dos
  variantes. El único punto donde se elige una implementación en tiempo
  de ejecución es `AdaptadoresTransportista.para(TipoIntegracion)` (ver
  Adapter), que es lo más cercano a una Strategy real.
- **Hoy:** el comportamiento según `OrigenPedido` (`STOCK_CONSIGNADO`
  reserva stock; `PUNTO_PICKING` solo valida el punto de picking).
- **Descartado por ahora:** el cobro según `MedioPago` (`PREPAGO` se
  cobra en el banco legado, `CONTRA_ENTREGA` queda pendiente hasta la
  entrega) está resuelto con un `if` en `PagoService.registrarCobro`: con
  dos medios alcanza, y pasaría a una estrategia por medio de pago si se
  suman más.
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
  `AsignadorDeEspera` (`@Singleton @Startup`, ADR-037) recorre la lista de
  espera de a una pasada; con concurrencia `BEAN`, un aviso que llega
  durante una pasada no espera: pide otra vuelta al terminar.

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
    pedidos vivos, stock consignado o cuentas de ese comercio. Si nadie
    lo impide, `ComercioEliminado` (también sincrónico) hace que cada
    componente borre el historial terminado del comercio (pedidos
    cancelados, avisos, cobros anulados, envíos cancelados). Comercios no
    depende de ellos (ya dependen de él), y nada queda apuntando a un
    comercio inexistente. Implementado.
  - `EliminacionDeRepartidor` / `RepartidorEliminado` y `ZonaEliminada`
    (sincrónicos, ADR-036): el mismo esquema para eliminar un repartidor
    (Pedidos y Seguridad lo impiden si tiene entregas en curso o cuenta, y
    después Pedidos le suelta sus pedidos terminados) y una zona
    (Repartidores deja sin zona fija a los suyos). Implementado.
  - `EstadoPedidoCambiado` → `AvisosDeEspera` (`AFTER_SUCCESS`,
    ADR-037): una entrega, cancelación o devolución puede haber liberado
    lugar, así que programa una pasada de la lista de espera. Implementado.
  - `EstadoEnvioCambiado` (sincrónico): Transportistas avisa que un
    transportista informó un estado nuevo; Pedidos
    (`ActualizacionDePedidosPorEnvio`) mueve el pedido en la misma
    transacción. Transportistas no depende de Pedidos. Implementado.
  - `AvisoDestinatarioRegistrado` → `AvisosPorMail` (`AFTER_SUCCESS`,
    ADR-040): el aviso a quien recibe sale por mail recién cuando quedó
    guardado. Implementado.
- **Y entre componentes, publicación/suscripción:** del otro lado del
  tópico, `SuscriptorPagosEstadoPedido`,
  `SuscriptorNotificacionesEstadoPedido` y
  `SuscriptorDestinatarioEstadoPedido` (ADR-040) reaccionan al mismo
  evento sin que Pedidos los conozca. El tercero se sumó sin tocar a
  Pedidos ni a los otros dos.
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
  dice `EN_VIAJE`; Rabbit, en el envío, `EN_TRANSITO`, y el pedido pasa a
  `EN_CAMINO`), e incluso si una operación
  existe: el legado no cotiza, y su adaptador responde "no cotiza" sin
  llamarlo. `AdaptadoresTransportista` elige
  según el `TipoIntegracion` del transportista; `TransportistaService` no
  sabe con cuál habla. Sumar un transportista EDI sería una implementación
  más.

## Máquina de estados (State)

- **Problema:** las reglas de "desde qué estado se puede pasar a cuál"
  estaban repartidas en `if` dentro de cada operación y, después, en un
  `switch` del enum que decía qué se puede pero no qué provoca cada estado.
- **Dónde:** `EstadoDelPedido` (interfaz sellada) y una clase por estado en
  `pedidos/datos/model`. `Pedido` delega (`despachar()`, `cancelar()`, …) y
  `PedidoService.aplicar(...)` es el único lugar que cambia el estado de un
  pedido existente.
- **Cómo funciona:** cada estado implementa solo las operaciones que permite;
  el resto lanza `TransicionInvalidaException`, que el servicio traduce a
  `TransicionNoPermitidaException` (409 en la API del ERP al cancelar). Los
  efectos de entrar a un estado (dejar la lista de espera, marcar que salió a
  reparto) están en `alEntrar`. `EstadoPedido.puedePasarA` consulta al
  estado. La columna de la base sigue siendo el enum.
- **Descartado:** un enum con métodos por constante (mezcla persistencia con
  reglas y los estados consultan el pedido, por el retiro en depósito) y una
  librería de máquinas de estados (una dependencia para seis estados).
- **Mensajes de cancelación:** el motivo por el que un estado no se puede
  cancelar también lo da el estado (`motivoParaNoCancelar` y
  `queLeQuedaAlErp`); `ReglaDeCancelacion` le pregunta y ya no decide según el
  estado.

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
  prueba. Ver [MENSAJERIA-SINCRONICA.md](../integraciones/MENSAJERIA-SINCRONICA.md).
- **Descartado:** `@CircuitBreaker` de MicroProfile Fault Tolerance: no
  viene en `standalone-full` de WildFly (ADR-011).

## Exception Mapper (traductor de errores)

- **Problema:** que cada error de la API REST salga en un único formato
  (Problem Details, RFC 9457), incluso los que no maneja ningún recurso:
  URL inexistente, JSON roto, acceso denegado o un error inesperado.
- **Dónde:** `ProblemaMapper` (`ExceptionMapper<Throwable>`, `@Provider`)
  arma la respuesta con `Problema`; los recursos usan el mismo `Problema`
  para sus errores de negocio (400, 404, 409, 422). Ver
  [MENSAJERIA-SINCRONICA.md](../integraciones/MENSAJERIA-SINCRONICA.md).
- **Descartado:** dejar que el contenedor responda (sale la página
  `error.html` o el formato propio del runtime) o un `try/catch` en cada
  método del recurso.

## Idempotency Key

- **Problema:** si el `201` de un alta se pierde por un timeout, el ERP
  reintenta y, sin más, el pedido se duplica.
- **Dónde:** `PedidoService.registrarPedidoExterno` guarda el header
  `Idempotency-Key` y una huella (SHA-256) del pedido; la misma clave con
  el mismo pedido devuelve el existente, con otro responde `422`. La
  restricción única `(idComercio, claveIdempotencia)` cubre los reintentos
  simultáneos (ADR-018).
- **Descartado:** deduplicar por el contenido del pedido: dos compras
  iguales legítimas se confundirían con un reintento.
