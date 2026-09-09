# Rabbit — Gestión de Comercios, Inventario y Pedidos

## Qué hace el sistema

Rabbit es una plataforma de logística para comercios: centraliza la
consignación de mercadería en depósitos propios y coordina su reserva y
despacho contra los pedidos que llegan del ERP de cada comercio.

Hasta este momento permite:

- Dar de alta comercios y sus puntos de picking.
- Registrar depósitos propios de Rabbit y cargar en ellos stock consignado
  por un comercio puntual (el depósito es de Rabbit, la mercadería tiene
  dueño).
- Reservar stock con vencimiento — la reserva queda como una conversación
  en curso mientras el usuario decide confirmarla o liberarla, y vence sola
  si no se resuelve a tiempo.
- Consultar el historial de todas las reservas ya cerradas, con su
  desenlace (confirmada, liberada, vencida o devuelta).
- Simular pedidos que "llegan" del ERP de un comercio y sincronizarlos
  automáticamente contra pedidos reales de Rabbit.
- Gestionar usuarios del sistema con rol `ADMINISTRADOR` u `OPERADOR`, y
  autenticarlos contra el `ApplicationRealm` de WildFly.

## Arquitectura

Tres capas, con comunicación estrictamente unidireccional: **Presentación
→ Negocio → Datos**. Ninguna capa accede directo a una capa no adyacente
(la Presentación nunca toca la base de datos, la capa de Datos nunca
decide reglas de negocio).

| Capa | Tecnología | Responsabilidad |
|---|---|---|
| **Presentación** | JSF (`@Named` + `@ViewScoped`) | Renderiza las vistas Facelets (`.xhtml`) y captura la entrada del usuario. No contiene reglas de negocio propias. |
| **Negocio** | EJB (`@Stateless` / `@Stateful`) | Aplica las validaciones y reglas del dominio, orquesta las operaciones (`@Transactional`). No conoce detalles de la vista ni del motor de base de datos. |
| **Datos** | JPA / Hibernate | Persiste y recupera información. Traduce entre objetos Java y filas de la tabla. |

Cada componente replica el mismo esqueleto de paquetes, por ejemplo
`comercios`:

```
com.rabbit.comercios/
├── presentacion/     ← Managed Beans JSF (ComercioBean, PuntoPickingBean)
├── negocio/          ← EJB + interfaces (ComercioService, IConsultaComercios, IRegistroComercios)
├── datos/
│   ├── model/         ← Entidades JPA (Comercio, PuntoPicking, Producto)
│   └── ComercioRepository.java, ProductoRepository.java
└── dto/               ← DTOs que viajan entre capas (ComercioDTO, PuntoPickingDTO, ...)
```

Las entidades JPA (`datos/model/`) nunca se exponen directo a la vista:
`presentacion` siempre habla con `negocio` en términos de `dto/`, así la
capa de Datos puede cambiar (agregar una columna, una relación) sin tocar
las vistas `.xhtml`.

## Componentes

El sistema tiene cuatro componentes de negocio. Cada uno expone su
funcionalidad a través de una o más interfaces Jakarta EE (`local`,
implementadas por un único EJB), que es lo único que la capa de
Presentación conoce — nunca la clase concreta.

### Comercios (`com.rabbit.comercios`)

Alta/baja/consulta de comercios, sus puntos de picking y productos.

- **`IRegistroComercios`** — altas y cambios de estado: `registrarComercio`,
  `actualizarDatosFiscales`, `darDeBajaComercio` / `reactivarComercio` /
  `eliminarComercio`, y su equivalente para puntos de picking
  (`registrarPuntoPicking`, `darDeBajaPuntoPicking`,
  `reactivarPuntoPicking`).
- **`IConsultaComercios`** — lectura: `obtenerComercio`, `listarTodos`,
  `listarPuntosPicking`, `validarComercioActivo`.

Las implementa `ComercioService` (`@Stateless`). La usan `ComercioBean` y
`PuntoPickingBean` (`comercios.xhtml`, `puntos-picking.xhtml`), y también
otros componentes que necesitan validar un comercio o resolver su nombre
(por ejemplo Inventario y Pedidos, vía `IConsultaComercios`).

### Inventario (`com.rabbit.inventario`)

Depósitos, ítems de inventario y reservas de stock.

- **`IConsultaStock`** — depósitos e ítems: `registrarDeposito`,
  `listarDepositos`, `registrarItem`, `listarItemsPorDeposito` /
  `PorComercio` / `PorComercioYDeposito`, `consultarDisponibilidad`,
  `listarHistorialReservas`.
- **`IReservaStock`** — el ciclo de vida de una reserva:
  `reservarStock`, `confirmarReserva`, `liberarReserva`, `extenderReserva`,
  `obtenerReservaActual`, `hayReservaVigente`, `registrarDevolucion`.

`IConsultaStock` la implementa un EJB `@Stateless`. `IReservaStock` la
implementa `InventarioService`, el único componente **`@Stateful`** del
sistema: mantiene la reserva abierta como conversación del usuario entre
requests (con `@StatefulTimeout`), en vez de recibir todos los datos en
una sola llamada. Un `BarredorDeReservas` (`@Schedule`) recorre
periódicamente y libera las reservas vencidas que el usuario no resolvió.
Las usan `DepositoBean`, `ItemInventarioBean`, `ReservaBean` y
`HistorialReservasBean` (`depositos.xhtml`, `items.xhtml`,
`reservas.xhtml`, `historial.xhtml`).

### Pedidos (`com.rabbit.pedidos`)

Gestión de pedidos y su sincronización con el "ERP" de cada comercio
(simulado).

- **`IGestionPedidos`** — `registrarPedidoExterno`,
  `sincronizarPedidoExterno`, `descartarPedidoExterno`, `confirmarPedido`,
  `cancelarPedido`.
- **`ISeguimientoPedido`** — `listarPedidosExternos`,
  `consultarEstadoPedido`, `listarTodos`, `listarPedidosDeComercio`.

Las implementa `PedidoService` (`@Stateless`); cada operación es
autocontenida, sin estado entre llamadas. Un `SincronizadorDePedidos`
(`@Schedule`, cada 1 minuto) revisa los pedidos externos sin sincronizar y
genera el pedido real correspondiente, tomando stock libre disponible vía
Inventario. Las usa `PedidoBean` (`pedidos.xhtml`).

### Seguridad (`com.rabbit.seguridad`)

Usuarios del sistema y su sincronización de roles contra WildFly.

- **`IRegistroUsuarios`** — `registrarUsuario`, `darDeBaja`.
- **`IConsultaUsuarios`** — `listarTodos`, `obtenerUsuario`.

Las implementa `UsuarioService` (`@Stateless`). Las usa `UsuarioBean`
(`usuarios.xhtml`) para el alta y listado, y `LoginBean` /  `SesionBean`
(`login.xhtml`, y el gatekeeper `exigirSesion` que protege el resto de las
vistas) para autenticación y consulta de sesión — estos dos no pasan por
una interfaz de negocio: hablan directo contra el `SecurityContext` /
`ApplicationRealm` de WildFly.

## Patrones de diseño implementados

- **DAO (Data Access Object)** — cada componente tiene un `*Repository`
  (`ComercioRepository`, `InventarioRepository`, `PedidoRepository`,
  `UsuarioRepository`) que aísla el acceso a datos: es la única clase que
  toca el `EntityManager` y JPQL. La capa de Negocio nunca escribe una
  consulta ni conoce el motor de persistencia — si mañana cambia de
  Hibernate a otra cosa, solo se toca esta capa.

- **DTO (Data Transfer Object)** — `ComercioDTO`, `ReservaStockDTO`,
  `PedidoDTO`, etc. Son objetos planos, sin anotaciones JPA, que viajan
  entre Negocio y Presentación. Evitan exponer la entidad JPA directo a la
  vista (que rompería con `LazyInitializationException` fuera de una
  transacción) y desacoplan la vista de cambios en el modelo de datos.

- **Facade** — cada componente expone su negocio a través de una interfaz
  reducida que esconde la coordinación interna entre repositorios y otros
  componentes. El caso más explícito es `IGestionPedidos` /
  `PedidoService`: quien llama a `sincronizarPedidoExterno()` no necesita
  saber que por dentro valida el comercio contra Comercios y compromete
  stock contra Inventario — esa orquestación es exactamente lo que el
  Facade oculta. El mismo rol lo cumplen `IRegistroComercios`,
  `IConsultaStock` e `IRegistroUsuarios` frente a sus repositorios.

- **Singleton** — `BarredorDeReservas` y `SincronizadorDePedidos` son EJB
  `@Singleton` con `@Startup`: tiene que existir una única instancia
  corriendo la limpieza/sincronización periódica, porque si hubiera varias
  compitiendo sobre las mismas filas podrían descontar stock dos veces. El
  contenedor garantiza instancia única y serializa el acceso concurrente.

- **Provider (`Instance<T>` de CDI, usado como fábrica)** —
  `PedidoService` (`@Stateless`) necesita invocar `IReservaStock`
  (`@Stateful`) sin compartir esa conversación entre pedidos procesados en
  paralelo. En vez de inyectar `IReservaStock` como campo fijo — que
  crearía una única instancia stateful compartida por todo el pool de
  `PedidoService` — inyecta `Instance<IReservaStock>` y llama a `.get()`
  en cada operación: cada llamada obtiene una instancia stateful nueva y
  aislada, que se usa y se descarta (`.destroy(...)`) para ese pedido
  puntual.

## Seguridad declarativa

Los roles del sistema (`ADMINISTRADOR`, `OPERADOR`, ver
[`Rol`](src/main/java/com/rabbit/seguridad/datos/model/Rol.java)) viven en
el `ApplicationRealm` nativo de WildFly — no hay un `IdentityStore` propio
de la aplicación. Cuando se registra un usuario, `UsuarioService` lo
sincroniza contra ese realm.

La autorización se resuelve en la capa de Negocio, no en la de
Presentación, con anotaciones Jakarta Authorization sobre los EJB:

- `@DeclareRoles({"ADMINISTRADOR", "OPERADOR"})` documenta a nivel de
  clase qué roles existen para el componente.
- `@PermitAll` a nivel de clase es explícito y necesario: este WildFly
  tiene `default-missing-method-permissions-deny-access=true`, así que en
  cuanto un bean usa cualquier anotación de seguridad, todo método sin
  permiso declarado queda denegado por default.
- `@RolesAllowed("ADMINISTRADOR")` puntual sobre el método más sensible de
  cada componente — hoy, `ComercioService.eliminarComercio` (borra
  físicamente un comercio y sus puntos de picking en cascada) — es la
  restricción real. El contenedor rechaza la llamada con
  `EJBAccessException` si el caller no autenticó con ese rol; la vista
  atrapa esa excepción y la traduce a un mensaje de negocio.
- En la vista, `SesionBean.exigirSesion()` actúa como gatekeeper de
  página completa (vía `<f:viewAction>` en cada `.xhtml` protegido):
  redirige a `login.xhtml` antes de renderizar nada si no hay sesión
  iniciada. Es un control de UX, no de seguridad — la autorización real
  siempre la impone el `@RolesAllowed` del lado del EJB.
