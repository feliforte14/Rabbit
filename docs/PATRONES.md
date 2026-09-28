# Patrones de diseño

Para cada patrón: qué problema concreto resuelve en Rabbit, dónde está y
qué alternativa se descartó.

## DAO (Repository)

- **Problema:** que las reglas de negocio no dependan de JPQL ni del
  `EntityManager`.
- **Dónde:** `ComercioRepository`, `ProductoRepository`,
  `InventarioRepository`, `PedidoRepository`, `UsuarioRepository`.
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
- **Beneficio visible:** la cola (MDB), el polling (timer) y la pantalla
  llaman al mismo `sincronizarPedidoExterno`; la regla no se duplica.

## Strategy

- **Hoy:** el comportamiento según `OrigenPedido` (`STOCK_CONSIGNADO`
  reserva stock; `PUNTO_PICKING` solo valida el punto de picking).
- **Planificado:** el cobro según `MedioPago` en ServicioDePagosYCobranzas
  (`PREPAGO` autoriza en la pasarela simulada, `CONTRA_ENTREGA` queda
  pendiente hasta la entrega), con una estrategia por medio de pago.

## Singleton

- **Problema:** una tarea periódica corriendo en varias instancias a la
  vez procesaría las mismas filas dos veces.
- **Dónde:** `BarredorDeReservas` y `SincronizadorDePedidos`
  (`@Singleton @Startup` + `@Schedule`).

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
  - `EstadoPedidoCambiado`: se dispara en cada cambio de estado del
    pedido. El observer que lo publique en el tópico está planificado.
- **Descartado:** llamar al publicador directo desde el servicio (ver
  ADR-002 en [DECISIONES.md](DECISIONES.md)).

## Adapter

- **Problema:** que `ComercioService` no dependa de JAX-WS (checked
  exceptions SOAP, `BindingProvider`, timeouts).
- **Dónde:** `PadronFiscalClient` implementa `IPadronFiscalClient`
  (`consultar(cuit) → ResultadoConsultaCuit`). Si el padrón pasara a
  REST, solo cambia el Adapter.
- **Planificado:** ServicioDeIntegracionTransportistas (Entrega Final),
  un Adapter por tipo de transportista (SOAP/EDI legado, REST moderno).

## Máquina de estados (State simplificado)

- **Problema:** las reglas de "desde qué estado se puede pasar a cuál"
  estaban repartidas en `if` dentro de cada operación.
- **Dónde:** `EstadoPedido.puedePasarA(...)` +
  `PedidoService.cambiarEstado(...)`.
- **Por qué no el patrón State completo** (una clase por estado): con
  cinco estados sin comportamiento propio, un `switch` en el enum es más
  simple de explicar y de mantener.
