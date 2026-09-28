# Arquitectura

## Capas

Cada componente se organiza en tres capas con dependencia en un solo
sentido: **Presentación → Negocio → Datos**.

| Capa | Tecnología | Responsabilidad | Paquete |
|---|---|---|---|
| Presentación | JSF (`@Named` + `@ViewScoped`), Facelets | Mostrar y capturar datos. Sin reglas de negocio. | `presentacion/` |
| Negocio | EJB (`@Stateless`, `@Stateful`, `@Singleton`, `@MessageDriven`) | Reglas del dominio, transacciones, seguridad, orquestación entre componentes | `negocio/` |
| Datos | JPA / Hibernate | Persistencia. Única capa que toca el `EntityManager` | `datos/`, `datos/model/` |
| (transversal) | POJOs | Objetos que cruzan capas y componentes | `dto/` |

**Por qué así:** la vista nunca ve entidades JPA (evita
`LazyInitializationException` y acopla la vista al esquema), y las reglas
viven en un solo lugar (el EJB), sin importar si las invoca una pantalla,
un timer o un MDB.

## Componentes

Cada componente expone interfaces `@Local` separadas por uso (lectura vs.
escritura) y nunca expone sus entidades ni sus excepciones internas a
otro componente. Las referencias entre componentes se guardan como IDs
(`Long idComercio`), no como relaciones JPA.

| Componente | Estado | Interfaces | Tipo de EJB | Por qué ese tipo |
|---|---|---|---|---|
| Comercios | Implementado | `IRegistroComercios`, `IConsultaComercios` | `@Stateless` | Cada operación recibe todo lo que necesita; el estado vive en la base |
| Inventario | Implementado | `IConsultaStock` (`@Stateless`), `IReservaStock` | `@Stateful` | La reserva es una conversación: `reservarStock` y `confirmarReserva` operan sobre la misma instancia |
| Pedidos | Implementado | `IGestionPedidos`, `ISeguimientoPedido` | `@Stateless` (Facade) | El estado del pedido vive en la base; ninguna operación depende de una llamada anterior |
| Seguridad | Implementado | `IRegistroUsuarios`, `IConsultaUsuarios` | `@Stateless` | Idem Comercios |
| Integración banco legado | Implementado | `IBancoClient` | `@Stateless` | Cliente SOAP sin estado de conversación |
| Pagos y Cobranzas | Implementado | `IRegistroCobros`, `IConsultaCobros` | `@Stateless` + `@MessageDriven` (suscriptor) | El cobro queda registrado en la base; se suma a la transacción del llamador |
| Repartidores | Implementado | `IAsignacionRepartidores`, `IGestionRepartidores` | `@Stateless` | La disponibilidad del repartidor es un dato persistido, no de sesión |
| Notificaciones | Implementado | `INotificaciones` | `@Stateless` + `@MessageDriven` (suscriptor) | Reacciona a eventos del tópico; nadie la llama para avisar |
| Ruteo, Transportistas | Entrega Final | — | — | — |

### Operaciones por componente (implementado)

| Componente | Interfaz | Operaciones | Quién la usa |
|---|---|---|---|
| Comercios | `IRegistroComercios` | `registrarComercio`, `actualizarDatosFiscales`, `darDeBajaComercio`, `reactivarComercio`, `eliminarComercio`, `registrarPuntoPicking`, `darDeBajaPuntoPicking`, `reactivarPuntoPicking` | `ComercioBean`, `PuntoPickingBean` |
| Comercios | `IConsultaComercios` | `obtenerComercio`, `listarTodos`, `listarPuntosPicking`, `listarPuntosPickingDeComercio`, `validarComercioActivo` | Vistas, Inventario, Pedidos |
| Inventario | `IConsultaStock` | `registrarDeposito`, `listarDepositos`, `obtenerDeposito`, `listarDepositosConStock`, `registrarItem`, `listarItemsPorDeposito` / `PorComercio` / `PorComercioYDeposito`, `consultarDisponibilidad`, `listarHistorialReservas` | `DepositoBean`, `ItemInventarioBean`, `HistorialReservasBean`, `PedidoBean` |
| Inventario | `IReservaStock` | `reservarStock`, `confirmarReserva`, `liberarReserva`, `extenderReserva`, `obtenerReservaActual`, `hayReservaVigente`, `registrarDevolucion` | `ReservaBean`, Pedidos |
| Pedidos | `IGestionPedidos` | `registrarPedidoExterno`, `sincronizarPedidoExterno`, `descartarPedidoExterno`, `confirmarPedido`, `despacharPedido`, `registrarEntrega`, `cancelarPedido` | `PedidoBean`, MDB, timer |
| Pedidos | `ISeguimientoPedido` | `listarPedidosExternos`, `consultarEstadoPedido`, `listarTodos`, `listarPedidosDeComercio` | `PedidoBean` |
| Seguridad | `IRegistroUsuarios` | `registrarUsuario`, `darDeBaja`, `puedeElegirRol` | `UsuarioBean` |
| Seguridad | `IConsultaUsuarios` | `listarTodos`, `obtenerUsuario` | `UsuarioBean` |
| Pagos | `IRegistroCobros` | `registrarCobro`, `registrarCobroContraEntrega`, `anularCobro` (`ADMINISTRADOR`) | Pedidos, `SuscriptorPagosEstadoPedido` |
| Pagos | `IConsultaCobros` | `obtenerCobroDePedido`, `listarTodos` | `PedidoBean` |
| Repartidores | `IAsignacionRepartidores` | `asignarRepartidor`, `liberarRepartidor` | Pedidos |
| Repartidores | `IGestionRepartidores` | `registrarRepartidor`, `listarTodos` | `RepartidorBean`, `PedidoBean` |
| Notificaciones | `INotificaciones` | `avisarCambioDeEstado`, `listarRecientes` | `SuscriptorNotificacionesEstadoPedido`, `PedidoBean` |

Otros detalles de cada componente:

- **Inventario:** `IReservaStock` mantiene la reserva abierta entre
  requests con `@StatefulTimeout`; `BarredorDeReservas` (`@Schedule`)
  libera las reservas vencidas que el usuario no resolvió.
- **Pedidos:** un pedido es multi-línea (`LineaPedido`). Con origen
  `STOCK_CONSIGNADO` cada línea reserva y confirma stock en su propia
  instancia de `IReservaStock`; con `PUNTO_PICKING` solo se valida que el
  punto de picking esté activo. Al cancelar, solo se devuelve el stock de
  las líneas con `idReservaStock`.
- **Seguridad:** `LoginBean` y `SesionBean` no pasan por una interfaz de
  negocio: hablan directo con el `SecurityContext` de WildFly.
- **Pagos:** PREPAGO se cobra por SOAP en el banco legado al confirmar
  (rechaza por encima de $500.000); si la confirmación falla después, se
  pide la reversa al banco;
  CONTRA_ENTREGA queda PENDIENTE y se acredita al recibir `ENTREGADO` por
  el tópico.
- **Repartidores:** `asignarRepartidor` toma el primero DISPONIBLE con
  bloqueo pesimista, para que dos confirmaciones simultáneas no se lleven
  al mismo repartidor. Se libera al entregar o cancelar el pedido.
- **Notificaciones:** el aviso es simulado (queda guardado y se ve en
  `pedidos.xhtml`). Descarta eventos más viejos que el último avisado del
  mismo pedido.

### Dependencias entre componentes (implementadas)

```mermaid
flowchart LR
    subgraph Rabbit["Rabbit (WildFly)"]
        Pedidos -->|IConsultaComercios| Comercios
        Pedidos -->|"Instance&lt;IReservaStock&gt;"| Inventario
        Pedidos -->|IAsignacionRepartidores| Repartidores
        Pedidos -->|IRegistroCobros| Pagos
        Pedidos -. topico.pedidos.estado .-> Pagos
        Pedidos -. topico.pedidos.estado .-> Notificaciones
        Inventario -->|IConsultaComercios| Comercios
        Pagos -->|IBancoClient| Banco[Integración banco]
    end
    Banco -->|SOAP/HTTP| Legado[(Banco legado<br/>simulado)]
```

## Mapa de integración

| Tramo | Mecanismo | Estado | Detalle |
|---|---|---|---|
| ERP del comercio → Rabbit | Formulario JSF (simulación) | Implementado | Se reemplaza por REST |
| ERP del comercio → Rabbit | REST `POST /api/pedidos-externos` | Planificado | [MENSAJERIA-SINCRONICA.md](MENSAJERIA-SINCRONICA.md) |
| Alta de pedido externo → sincronización | Cola JMS `cola.pedidos.externos` | Implementado | [MENSAJERIA-ASINCRONICA.md](MENSAJERIA-ASINCRONICA.md) |
| Pagos → Banco legado | SOAP | Implementado | [MENSAJERIA-SINCRONICA.md](MENSAJERIA-SINCRONICA.md) |
| Cambio de estado del pedido → Notificaciones, Pagos | Tópico JMS `topico.pedidos.estado` | Implementado | [MENSAJERIA-ASINCRONICA.md](MENSAJERIA-ASINCRONICA.md) |
| Pedidos → Comercios, Inventario, Pagos, Repartidores | Llamada local EJB | Implementado | No es integración entre sistemas: mismo proceso |

### Criterio sincrónico vs. asincrónico

| | Asincrónico (JMS) | Sincrónico (SOAP / REST) |
|---|---|---|
| Caso en Rabbit | Sincronizar pedido externo; avisar cambios de estado | Cobrar en el banco; recibir un pedido del ERP |
| ¿El proceso puede seguir sin la respuesta? | Sí | No |
| Si el otro lado no contesta | El mensaje espera o se reintenta (y hay polling de respaldo) | Hay que decidirlo explícitamente (timeout + degradación) |
| Acoplamiento | Solo al formato del mensaje | Temporal + contrato |

## Máquina de estados del pedido

Implementada en `EstadoPedido.puedePasarA(...)`. Todo cambio de estado de
un pedido existente pasa por `PedidoService.cambiarEstado(...)`, que
valida la transición y dispara el evento `EstadoPedidoCambiado`.

```mermaid
stateDiagram-v2
    [*] --> PENDIENTE: sincronizarPedidoExterno
    PENDIENTE --> CONFIRMADO: confirmarPedido
    PENDIENTE --> CANCELADO: cancelarPedido
    CONFIRMADO --> EN_CAMINO: despacharPedido
    CONFIRMADO --> CANCELADO: cancelarPedido
    EN_CAMINO --> ENTREGADO: registrarEntrega
    ENTREGADO --> [*]
    CANCELADO --> [*]
```

- **EN_CAMINO no se puede cancelar:** la mercadería ya salió con el
  repartidor, no está en el depósito para devolver el stock.
- **Por qué centralizar las transiciones:** la regla está en un solo
  lugar, y protege contra acciones fuera de orden (un "entregar" sobre un
  pedido cancelado, o eventos que llegan desordenados).

## Datos del pedido que vienen del ERP

`importe` y `medioPago` (`PREPAGO` / `CONTRA_ENTREGA`) llegan en el
pedido externo y pasan tal cual al `Pedido` al sincronizar. Rabbit no
calcula precios: ver ADR-004 en [DECISIONES.md](DECISIONES.md).
