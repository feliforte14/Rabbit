# Rabbit — Dossier de contexto para la documentación (Entrega Parcial N.º 2)

Relevamiento del estado **real** del sistema, tomado del código. No es la
documentación final: es la base verificada para escribirla. Cada dato
lleva su fuente (`ruta:línea`). Las rutas Java son relativas a
`src/main/java/com/rabbit/` salvo que se indique otra cosa.

Convenciones: **NO ENCONTRADO** = se buscó y no existe. "Fuera del repo" =
documento que no está en Git (consigna y documentación funcional, tomados
de la carpeta Descargas del equipo).

---

## 0. Metadatos

| Dato | Valor |
|---|---|
| Rama relevada | `main` (`origin/main`). El código de `main` es idéntico al de `feature/mejoras-lideres-ultima-milla` (el PR #8 se mergeó; las dos ramas solo difieren en 4 archivos de `docs/entregas/`) |
| Último commit de `main` | `cdab3c3996e828bc49196b444b8aa47b46a142e4` · 2026-10-18 20:33:04 -0300 · "Docu actualizada" |
| Último commit con código | `aa881dd` · 2026-10-18 · "Editar y eliminar el stock de un depósito (ADR-049)", mergeado en `7faa0c9` (PR #8) |
| Fecha del relevamiento | 18/10/2026 |
| Tamaño | 275 clases Java, 26 páginas `.xhtml`, 49 ADR |
| Commits desde 2026-09-01 en `main` | 133 |
| Tests | 151 unitarios (último `mvn test`: 151 run, 0 fallas, 18/10 15:12, `target/surefire-reports`) + 17 de integración (`ApiRestIT` 10, `MejorasUltimaMillaIT` 7) |

### Funcionalidades agregadas desde 2026-09-01 (git log agrupado)

La entrega anterior (1.º Parcial) fue el 14/09. Todo lo de abajo del 26/09
en adelante es posterior a ella.

| Tema | Commits (hash · fecha) | Qué se agregó |
|---|---|---|
| Inventario stateful y reservas | `befcfa6`, `78a3f93`…`3f3969b` (05/09), `cde7ab1`, `6f39cb8` (08/09), `f4d4f18` (12/10), `aa881dd` (18/10) | Reserva con hold de 5 min, barredor de reservas, historial con filtros, stock con dueño, consultas en EJB @Stateless aparte (ADR-027), editar/eliminar stock (ADR-049) |
| Pedidos y seguridad declarativa | `9b4fda1` (08/09), `5e34cc9`, `8d8bbaa` | Componente Pedidos, @RolesAllowed, sincronizador por polling |
| Frontend inicial | `2aeacf7` (09/09) … `05a1b3a` (13/09) | JSF inicial, correcciones Trello |
| Mensajería asincrónica (cola) | `0bf1918` (26/09), `acc600c` (28/09) | `cola.pedidos.externos` + MDB, evento CDI `PedidoExternoRegistrado` |
| Estados, Pagos, Repartidores, Notificaciones, tópico | `767052c`, `bedc31c`, `a54c0d1` (28/09) | EN_CAMINO/ENTREGADO, importe y medio de pago, `topico.pedidos.estado` con suscriptores, alineación de CHECK de enums |
| SOAP (banco legado) y resiliencia | `2821b81`→`8c44d62` (28/09), `783fbad`, `1568802` | Se reemplazó el padrón fiscal SOAP por el cobro en banco legado; circuit breaker |
| API REST | `5da8e06` (28/09), `0e1a69d` (05/10), `2c01429` (07/10) | API del ERP y seguimiento público; `/v1`, idempotencia, Problem Details, cancelación, cotización, HTTPS |
| Vistas por rol | `e451039`, `d8b7064` (28-29/09) | Portales de comercio y repartidor |
| Desafíos opcionales | `49c0bfd`, `5014feb`, `eb7011c` (29/09), `ccaf503` (14/10) | Escalabilidad medida, banco en Node.js (movido a rama aparte) |
| Transportistas y ruteo | `e08ecc4`, `888bf8c` (29/09), `35294a4` (13/10) | Adaptadores REST/SOAP, zonas por CP, seguimiento solo por polling |
| Interfaz | `9e2d5e6`, `0bc6527`, `12f1c81` (13/10), `b8022a1` (14/10), `78ff676` (18/10) | Temas normal/claro/oscuro, responsive |
| Mejoras del tablero (Trello) | `8d93b0e`, `67544f5`, `c5bf672`, `7feece5`, `3791111` (15/10) | Login contra la base (bcrypt), peso y capacidad, remito con foto, ubicación en vivo, liquidación mensual, eliminar por admin |
| Correcciones del equipo | `439c2ee`, `2bb2594` (16/10) | Lista de espera, hora de corte, devolución al depósito, hoja de ruta recomendada |
| Mejoras frente a líderes de última milla | `44020b9`, `88a2669`, `72ceae4`, `80d4b76`, `fa73dec` (16/10), `1c09523` (17/10), `b0387ae` (18/10) | Datos de quien recibe, avisos al destinatario (3.er suscriptor), visitas fallidas con evidencia, fecha prometida, franja horaria, indicadores con CSV, OSM, SMTP real, corte 07:00, email obligatorio, no cancelar lo que salió |

Autores en ese período (por nombre de commit): Felipe Forte / felipeforte / feliforte14 (107), Juanse (18), Facular03 (5), Flor Ayala (3).

---

## 1. Stack y estructura

### Versiones (`pom.xml`)

| Elemento | Versión | Fuente |
|---|---|---|
| Java | `maven.compiler.release` 17 (probado con JDK 21 y 24 según README) | `pom.xml:13` |
| Jakarta EE | `jakarta.jakartaee-api` 10.0.0 (`provided`) | `pom.xml:18-22` |
| jBCrypt | `org.mindrot:jbcrypt` 0.4 (va en el WAR) | `pom.xml:25-29` |
| JUnit | 5.11.3 (test) | `pom.xml:32-36` |
| Hibernate Validator | 8.0.1.Final (solo test; en el servidor lo pone WildFly) | `pom.xml:39-44` |
| Expressly | 5.0.0 (test) | `pom.xml:45-49` |
| REST Assured | 5.5.0 (test, perfil `integracion`) | `pom.xml:52-56` |
| Jackson databind | 2.18.2 (test) | `pom.xml:58-62` |
| maven-surefire-plugin | 3.5.2 | `pom.xml` (build) |
| wildfly-maven-plugin | 5.0.1.Final (host 127.0.0.1, puerto 9990, server id `rabbit-wildfly`) | `pom.xml` (build) |
| maven-failsafe-plugin | 3.5.2 (perfil `integracion`) | `pom.xml` (profiles) |
| Hibernate ORM | No está en el pom: lo provee WildFly 41 (los comentarios del código hablan de Hibernate 7) | `infraestructura/AlineadorDeRestriccionesEnum.java:13` |
| Driver PostgreSQL | No está en el pom: se registra como módulo de WildFly (versión según la instalación) | README "Registrar el driver" |
| Servidor | WildFly 41 (41.0.0.Final / 41.0.1.Final), perfil **`standalone-full.xml`** (lo exige JMS) | README "Requisitos" |
| Broker | ActiveMQ Artemis embebido en WildFly, connection factory `in-vm` | `pedidos/negocio/PublicadorPedidosExternos.java:62-71` |
| Persistencia | Unidad `comerciosPU`, JTA, `java:jboss/datasources/RabbitDS`, `hibernate.hbm2ddl.auto=update` | `src/main/resources/META-INF/persistence.xml` |
| Packaging | WAR `Rabbit.war` | `pom.xml:10`, `finalName` |

### Árbol de paquetes (hasta la capa)

```
com.rabbit
├── comercios        datos (+model) · dto · negocio · presentacion
├── inventario       datos (+model) · dto · negocio · presentacion
├── pedidos          datos (+model) · dto · negocio · presentacion (+rest)
├── pagos            datos (+model) · dto · negocio · presentacion
├── repartidores     datos (+model) · dto · negocio · presentacion
├── ruteo            datos (+model) · dto · negocio · presentacion
├── notificaciones   datos (+model) · dto · negocio          (sin presentacion propia)
├── seguridad        datos (+model) · dto · negocio · presentacion
├── transportistas   datos (+model) · dto · negocio · presentacion
├── integracion      banco · transportistas (+simulador)       (sin capas)
└── infraestructura  ApiRest, ProblemaMapper, Problema, Alineador…, LimpiezaDeCredenciales, Mensajes, Formatos, EtiquetasBean, ArchivosSubidos
```

### Cantidad de componentes

- **9 componentes de negocio**, cada uno con capas y con al menos una
  interfaz `@Local`: Comercios, Inventario, Pedidos, Pagos y Cobranzas,
  Repartidores, Ruteo, Notificaciones, Seguridad, Transportistas.
  Notificaciones no tiene capa de presentación propia (sus avisos se
  muestran en `PedidoBean` y `PortalComercioBean`).
- **`integracion`** no es un componente de negocio: es una capa técnica de
  adaptadores (cliente SOAP del banco + circuit breaker; adaptadores REST y
  SOAP de transportistas) y de los sistemas externos **simulados** que
  viven en el mismo WAR. Expone una interfaz `@Local`
  (`integracion/banco/IBancoClient.java:11`), por eso hay documentos que lo
  cuentan como 10.º componente (ver contradicción 4).

---

## 2. Componentes

Notación de tipos EJB: `@Stateless` (SLSB), `@Stateful` (SFSB),
`@Singleton`, `@MessageDriven` (MDB). "Usada por" sale de los `@Inject`
reales (no hay `@EJB` en el proyecto).

### 2.1 Comercios

- **Responsabilidad:** clientes de Rabbit (datos fiscales) y sus puntos de picking; baja lógica en cascada, reactivación y eliminación física con veto de otros componentes (ADR-028).
- **EJB:** `ComercioService` `@Stateless` `@PermitAll` + `@DeclareRoles` (`comercios/negocio/ComercioService.java:71`). Stateless porque cada operación recibe el ID; el estado vive en la base.
- **Interfaces:**

| Interfaz | Operaciones (firma) | Usada por |
|---|---|---|
| `IRegistroComercios` (`comercios/negocio/IRegistroComercios.java`) | `Long registrarComercio(DatosComercioDTO)` · `void actualizarDatosFiscales(Long, DatosFiscalesDTO)` · `void darDeBajaComercio(Long)` · `void reactivarComercio(Long)` · `void eliminarComercio(Long)` · `Long registrarPuntoPicking(Long, DatosPuntoPickingDTO)` · `void darDeBajaPuntoPicking(Long)` · `void reactivarPuntoPicking(Long)` | `ComercioBean`, `PuntoPickingBean` |
| `IConsultaComercios` (`comercios/negocio/IConsultaComercios.java`) | `ComercioDTO obtenerComercio(Long)` · `List<ComercioDTO> listarTodos()` · `List<PuntoPickingDTO> listarPuntosPicking(Long)` · `List<PuntoPickingDTO> listarPuntosPickingDeComercio(Long)` · `boolean validarComercioActivo(Long)` | `PedidoService`, `InventarioService`, `ConsultaStockService`, `NotificacionService`, `AvisoDestinatarioService`, `LiquidacionService`, `RuteoService`, `UsuarioService` + beans de presentación |

- **Depende de:** Seguridad (`IContextoUsuario`, `ComercioService.java:76`). Presentación (`PortalComercioBean`) usa Pedidos, Inventario, Pagos, Notificaciones, Repartidores y Transportistas para armar el portal.
- **Entidades:** `Comercio` (`comercios`), `PuntoPicking` (`puntos_picking`).
- **Eventos CDI que publica:** `EliminacionDeComercio` (veto, sincrónico) y `ComercioEliminado` (`ComercioService.java:84,89`). Los observan Pedidos, Inventario, Seguridad (veto) y Pedidos, Pagos, Notificaciones, Transportistas (limpieza).

### 2.2 Inventario

- **Responsabilidad:** depósitos de Rabbit, stock consignado por comercio (con peso por unidad) y reservas con vencimiento.
- **EJB:**
  - `InventarioService` **`@Stateful`** `@StatefulTimeout(30, MINUTES)` (`inventario/negocio/InventarioService.java:71-72`): la reserva es una conversación (`reservarStock` → `confirmarReserva` sobre la misma instancia). Hold de 5 min (`MINUTOS_DE_HOLD = 5`, `:80`).
  - `ConsultaStockService` `@Stateless` (`inventario/negocio/ConsultaStockService.java:49`): consultas y ABM de stock (ADR-027).
  - `BarredorDeReservas` `@Singleton @Startup` con timer (`inventario/negocio/BarredorDeReservas.java:71`).
- **Interfaces:**

| Interfaz | Operaciones | Usada por |
|---|---|---|
| `IReservaStock` (`inventario/negocio/IReservaStock.java`) | `ReservaStockDTO reservarStock(Long idComercio, Long idItem, int cantidad)` · `void confirmarReserva()` · `void liberarReserva()` · `void extenderReserva()` · `ReservaStockDTO obtenerReservaActual()` · `boolean hayReservaVigente()` · `void registrarDevolucion(Long idReserva)` | `ReservaBean` (sesión), `PedidoService` vía `Instance<IReservaStock>` (`PedidoService.java:155`) |
| `IConsultaStock` (`inventario/negocio/IConsultaStock.java`) | `Long registrarDeposito(DatosDepositoDTO)` · `DepositoDTO obtenerDeposito(Long)` · `List<DepositoDTO> listarDepositos()` · `Long registrarItem(Long, DatosItemInventarioDTO)` · `void actualizarItem(Long, DatosItemInventarioDTO)` · `void eliminarItem(Long)` · `List<ItemInventarioDTO> listarItemsPorDeposito(Long)` · `listarItemsPorComercio(Long)` · `listarItemsPorIds(Collection<Long>)` · `Map<Long,BigDecimal> pesosPorItem(Collection<Long>)` · `listarStockDelComercioActual()` · `listarItemsPorComercioYDeposito(Long, Long)` · `int consultarDisponibilidad(Long)` · `List<DepositoDTO> listarDepositosConStock(String)` · `List<ReservaStockDTO> listarHistorialReservas(FiltroHistorialDTO)` | `PedidoService`, `RuteoService`, `PedidoBean`, `PortalComercioBean`, `ItemInventarioBean`, `DepositoBean`, `HistorialReservasBean`, `ReservaBean` |

- **Depende de:** Comercios (`IConsultaComercios`), Seguridad (`IContextoUsuario`).
- **Entidades:** `Deposito` (`depositos`), `ItemInventario` (`items_inventario`, con `@Version`), `ReservaStock` (`reservas_stock`).
- **Eventos:** publica `EliminacionDeItem` (`ConsultaStockService.java:66`, lo observa `ReferenciasAItemEnPedidos`); observa `EliminacionDeComercio`.

### 2.3 Pedidos

- **Responsabilidad:** ciclo de vida del pedido: alta desde el ERP (idempotente), sincronización a pedido real, confirmación (cobro + repartidor), lista de espera, despacho, entrega con remito, visitas fallidas, derivación, cancelación, seguimiento e indicadores.
- **EJB:**
  - `PedidoService` `@Stateless` Facade (`pedidos/negocio/PedidoService.java:135-138`).
  - `IndicadoresService` `@Stateless` (`pedidos/negocio/IndicadoresService.java:26`).
  - `PedidoExternoListener` `@MessageDriven` (consumidor de la cola, `:65`).
  - `PublicadorPedidosExternos` y `PublicadorEstadosPedido` `@Stateless` (productores JMS).
  - `SincronizadorDePedidos` `@Singleton @Startup` (timer de respaldo).
  - `AsignadorDeEspera` `@Singleton @Startup` `@RunAs("OPERADOR")` (timer de la lista de espera).
  - `AvisosDeEspera` `@Singleton` (timer programático de 500 ms).
  - `PedidosExternosResource` y `SeguimientoResource` `@Stateless` (JAX-RS).
- **Interfaces:**

| Interfaz | Operaciones | Usada por |
|---|---|---|
| `IGestionPedidos` (`pedidos/negocio/IGestionPedidos.java`) | `Long registrarPedidoExterno(DatosPedidoExternoDTO, String clave)` · `PedidoExternoDTO cancelarPedidoExterno(Long)` · `Long sincronizarPedidoExterno(Long)` · `void descartarPedidoExterno(Long, String)` · `Long confirmarPedido(Long)` · `Long confirmarPedidoEnZona(Long, Long)` · `Long asignarDesdeEspera(Long)` · `void sacarDeEspera(Long, String)` · `void devolverADeposito(Long, EntregaFallidaDTO)` · `List<IntentoEntregaDTO> listarIntentosDeEntrega(Long)` · `FotoDeVisitaDTO obtenerFotoDeVisita(Long)` · `String derivarATransportista(Long, Long)` · `List<CotizacionDTO> cotizarDerivacion(Long)` · `void despacharPedido(Long)` · `void registrarEntrega(Long, byte[])` · `void registrarEntregaDeTransportista(Long)` · `RemitoDTO obtenerRemito(Long)` · `void cancelarPedido(Long)` | `PedidosExternosResource`, `PedidoExternoListener`, `SincronizadorDePedidos`, `AsignadorDeEspera`, `ActualizacionDePedidosPorEnvio`, `RuteoService`, `PedidoBean`, `MisEntregasBean`, `VisitasBean`, `RemitoServlet`, `FotoVisitaServlet` |
| `ISeguimientoPedido` (`pedidos/negocio/ISeguimientoPedido.java`) | `listarPedidosExternos()` · `consultarPedidoExterno(Long)` · `consultarEstadoPedido(Long)` · `consultarSeguimiento(String)` · `listarTodos()` · `listarPendientes()` · `listarPedidosDeComercio(Long)` · `resumenEntregas(LocalDateTime, LocalDateTime)` · `resumenEntregasDelComercioActual(LocalDateTime, LocalDateTime)` · `listarEntregasEnCurso()` · `listarPedidosDelComercioActual()` · `listarPedidosDelRepartidorActual()` · `listarEntregasEnCursoDeRepartidor(Long)` | `NotificacionService`, `AvisoDestinatarioService`, `LiquidacionService`, `RuteoService`, `ActualizacionDePedidosPorEnvio`, `PedidosExternosResource`, `SeguimientoResource`, `SeguimientoBean`, `PedidoBean`, `PortalComercioBean` |
| `IIndicadores` (`pedidos/negocio/IIndicadores.java`) | `List<Integer> PERIODOS = [7, 30, 90]` · `IndicadoresDTO calcular(int dias, Long idComercio)` · `IndicadoresDTO calcularDelComercioActual(int dias)` | `IndicadoresBean` |

- **Depende de (negocio):** Comercios (`IConsultaComercios`), Inventario (`Instance<IReservaStock>`, `IConsultaStock`), Repartidores (`IAsignacionRepartidores`), Pagos (`IRegistroCobros`), Transportistas (`IEnvios`), Seguridad (`IContextoUsuario`). En presentación también usa Ruteo (`ILlegadaEstimada` en `SeguimientoBean` y `SeguimientoResource`), Repartidores (`IGestionRepartidores`), Notificaciones, Pagos (`IConsultaCobros`, `ILiquidaciones`) y Transportistas (`IGestionTransportistas`).
- **Entidades:** `Pedido`, `LineaPedido`, `PedidoExterno`, `LineaPedidoExterno`, `RemitoEntrega`, `IntentoEntrega`, `FotoIntentoEntrega`.
- **Eventos que publica:** `PedidoExternoRegistrado` (`PedidoService.java:315`), `EstadoPedidoCambiado` (`:1283`).
- **Eventos que observa:** `EstadoEnvioCambiado` (Transportistas), `EliminacionDeComercio`, `ComercioEliminado`, `EliminacionDeItem`, `EliminacionDeRepartidor`, `RepartidorEliminado`, más los propios `PedidoExternoRegistrado` y `EstadoPedidoCambiado` (publicadores y `AvisosDeEspera`).

### 2.4 Pagos y Cobranzas

- **Responsabilidad:** cobro de cada pedido (prepago en el banco al confirmar; contra entrega al entregar), anulación y reversa, liquidación mensual a comercios.
- **EJB:**
  - `PagoService` `@Stateless` (`pagos/negocio/PagoService.java:55`).
  - `LiquidacionService` `@Stateless`, `NOT_SUPPORTED` de clase (`:44-46`).
  - `CobroDeLiquidaciones` `@Stateless @RolesAllowed("ADMINISTRADOR")` (`:33`).
  - `ReversasBancarias` `@Stateless` (`:39`).
  - `LiquidacionMensual` `@Singleton @RunAs("ADMINISTRADOR")` (`:26`).
  - `SuscriptorPagosEstadoPedido` `@MessageDriven` (`:40`).
- **Interfaces:**

| Interfaz | Operaciones | Usada por |
|---|---|---|
| `IRegistroCobros` | `Long registrarCobro(Long idPedido, Long idComercio, BigDecimal, MedioPago)` · `void registrarCobroContraEntrega(Long)` · `void anularCobro(Long)` (ADMINISTRADOR) · `void anularCobroPorDevolucion(Long)` | `PedidoService`, `SuscriptorPagosEstadoPedido` |
| `IConsultaCobros` | `CobroDTO obtenerCobroDePedido(Long)` · `List<CobroDTO> listarTodos()` · `List<CobroDTO> listarCobrosDelComercioActual()` | `PedidoBean`, `PortalComercioBean` |
| `ILiquidaciones` | `liquidarMes(YearMonth)` · `previsualizar(YearMonth)` · `reintentarCobro(Long)` · `reintentarPendientes()` · `listarDelPeriodo(YearMonth)` · `listarPeriodos()` · `listarDelComercioActual()` · `previsualizarMesEnCursoDelComercioActual()` · `TarifaMensual tarifaVigente()` | `LiquidacionMensual`, `LiquidacionBean`, `MisLiquidacionesBean`, `IndicadoresBean` |

- **Depende de:** `integracion` (`IBancoClient`), Pedidos (`ISeguimientoPedido` en `LiquidacionService`), Comercios (`IConsultaComercios`), Seguridad (`IContextoUsuario`); recibe el tópico de estados.
- **Entidades:** `Cobro` (`cobros`), `Liquidacion` (`liquidaciones`).
- **Eventos:**
  - Publica `PagoAutorizado` (`PagoService.java:107`) y `CobroAnulado` (`:147,163`).
  - Los observa `ReversasBancarias`: `AFTER_FAILURE` y `AFTER_SUCCESS`.
  - Observa `ComercioEliminado`.

### 2.5 Repartidores

- **Responsabilidad:** flota propia: capacidad (kg y bultos), asignación por capacidad con lista de espera, hora de corte, fecha prometida, ubicación en vivo.
- **EJB:** `RepartidorService` `@Stateless` (`repartidores/negocio/RepartidorService.java:48`).
- **Interfaces:**

| Interfaz | Operaciones | Usada por |
|---|---|---|
| `IAsignacionRepartidores` | `Long asignarRepartidor(Long idPedido, Long idZonaPreferida, BigDecimal pesoKg, int bultos, Long idRepartidorExcluido)` · `String motivoParaEsperar(BigDecimal, int, Long)` · `boolean antesDelCorte()` · `LocalDate diaDeEntregaPara(LocalDateTime)` · `void liberarRepartidor(Long, Long)` · `UbicacionDTO ultimaUbicacion(Long)` | `PedidoService`, `AsignadorDeEspera` |
| `IGestionRepartidores` | `Long registrarRepartidor(DatosRepartidorDTO)` · `listarTodos()` · `obtenerRepartidor(Long)` · `void eliminarRepartidor(Long)` · `void asignarZona(Long, Long)` · `void actualizarCapacidad(Long, BigDecimal, Integer)` · `void registrarUbicacion(double, double)` · `List<UbicacionDTO> listarUbicacionesRecientes()` · `UbicacionDTO ubicacionParaPedido(Long, Long)` | `RuteoService`, `UsuarioService`, `SeguimientoResource`, `RepartidorBean`, `MapaBean`, `MisEntregasBean`, `EntregasBean`, `SeguimientoBean`, `PedidoBean`, `IndicadoresBean`, `SesionBean`, `UsuarioBean`, `PortalComercioBean` |

- **Depende de:** Seguridad (`IContextoUsuario`, `RepartidorService.java:68`). En presentación, Ruteo (`IZonas` en `RepartidorBean`).
- **Entidades:** `Repartidor` (`repartidores`), `CargaAsignada` (`repartidores_cargas`).
- **Eventos:** publica `EliminacionDeRepartidor` y `RepartidorEliminado` (`:73,76`); observa `ZonaEliminada` (`ZonaDeRepartidores.java:24`).

### 2.6 Ruteo

- **Responsabilidad:** zonas por rango de CP, pendientes por zona, despacho (propio, transportista o respaldo), hoja de ruta, recorrido recomendado, franja de llegada, geocodificación con OpenStreetMap.
- **EJB:**
  - `RuteoService` `@Stateless` (`ruteo/negocio/RuteoService.java:76`).
  - `ZonaService` `@Stateless` (`:30`).
  - `LlegadaEstimadaService` `@Stateless @RunAs` (`:26`).
  - `CacheDeCoordenadas` `@Stateless` (`:24`).
  - `Geocodificador` `@Singleton`, concurrencia BEAN, `@RunAs("OPERADOR")` (`:50-53`).
- **Interfaces:**

| Interfaz | Operaciones | Usada por |
|---|---|---|
| `IRuteo` | `List<HojaDeRutaDTO> listarEntregasEnCurso()` · `entregasEnCursoDelRepartidor()` · `RecorridoDTO recorridoRecomendado(List<HojaDeRutaDTO>)` · `RecorridoDTO recorridoDeRepartidor(Long)` · `historialDelRepartidor()` · `List<GrupoZonaDTO> listarPendientesPorZona()` · `ResultadoDespachoDTO despacharPedido(Long)` · `List<ResultadoDespachoDTO> despacharZona(Long)` | `RuteoBean`, `MisEntregasBean`, `EntregasBean`, `LlegadaEstimadaService`, `Geocodificador` |
| `IZonas` | `Long registrarZona(DatosZonaDTO)` · `darDeBajaZona(Long)` · `reactivarZona(Long)` · `eliminarZona(Long)` · `List<ZonaDTO> listarTodas()` · `ZonaDTO zonaDeCodigoPostal(String)` | `RuteoService`, `RuteoBean`, `RepartidorBean` |
| `ILlegadaEstimada` | `LlegadaEstimadaDTO estimar(Long idRepartidor, Long idPedido)` | `SeguimientoBean`, `SeguimientoResource` |

- **Depende de:** Pedidos (`ISeguimientoPedido`, `IGestionPedidos`), Comercios, Inventario (`IConsultaStock`), Repartidores (`IGestionRepartidores`), Transportistas (`IEnvios`, `IGestionTransportistas`); externo: Nominatim.
- **Entidades:** `Zona` (`zonas`), `CoordenadaDireccion` (`coordenadas_direcciones`).
- **Eventos:** publica `ZonaEliminada` (`ZonaService.java:46`).

### 2.7 Notificaciones

- **Responsabilidad:** avisos al comercio (portal y mail) y a quien recibe (mail), reaccionando al tópico de estados.
- **EJB:**
  - `NotificacionService` y `AvisoDestinatarioService`, ambos `@Stateless`.
  - `SuscriptorNotificacionesEstadoPedido` y `SuscriptorDestinatarioEstadoPedido`, ambos `@MessageDriven`.
  - `AvisosPorMail` es CDI `@ApplicationScoped`, no EJB.
- **Interfaces:**

| Interfaz | Operaciones | Usada por |
|---|---|---|
| `INotificaciones` | `boolean avisarCambioDeEstado(Long idPedido, Long idComercio, String estado, LocalDateTime)` · `listarRecientes(int)` · `listarDelComercioActual(int)` | `SuscriptorNotificacionesEstadoPedido`, `PedidoBean`, `PortalComercioBean` |
| `IAvisosAlDestinatario` | `boolean avisar(Long, Long, String, LocalDateTime)` · `List<AvisoDestinatarioDTO> listarRecientes(int)` | `SuscriptorDestinatarioEstadoPedido`, `PedidoBean` |

- **Depende de:** Pedidos (`ISeguimientoPedido`), Comercios (`IConsultaComercios`), Seguridad (`IContextoUsuario`); externo: SMTP.
- **Entidades:** `Notificacion` (`notificaciones`), `AvisoDestinatario` (`avisos_destinatario`).
- **Eventos:**
  - Publica `AvisoRegistrado` y `AvisoDestinatarioRegistrado`.
  - `AvisosPorMail` los observa con `AFTER_SUCCESS` (`AvisosPorMail.java:39,47`).
  - Observa `ComercioEliminado`.

### 2.8 Seguridad

- **Responsabilidad:** cuentas (5 roles), contraseñas bcrypt, a quién representa quien llama, guardas de páginas y límite de intentos.
- **EJB:**
  - `UsuarioService` `@Stateless` (`seguridad/negocio/UsuarioService.java:59`).
  - `ContextoUsuarioService` `@Stateless @PermitAll` (`:25`).
- **Interfaces:**

| Interfaz | Operaciones | Usada por |
|---|---|---|
| `IRegistroUsuarios` | `Long registrarUsuario(DatosUsuarioDTO)` · `void darDeBaja(Long)` · `void eliminarUsuario(Long)` · `void reactivarUsuario(Long, String)` · `void restablecerClave(Long, String)` (todas ADMINISTRADOR) | `UsuarioBean` |
| `IConsultaUsuarios` | `List<UsuarioDTO> listarTodos()` · `UsuarioDTO obtenerUsuario(Long)` | `UsuarioBean` |
| `IContextoUsuario` | `Long idComercioActual()` · `Long idRepartidorActual()` | `PedidoService`, `IndicadoresService`, `ComercioService`, `ConsultaStockService`, `RepartidorService`, `PagoService`, `LiquidacionService`, `NotificacionService`, `TransportistaService`, `SesionBean`, `PuntoPickingBean` |

- **Depende de:** Comercios (`IConsultaComercios`), Repartidores (`IGestionRepartidores`).
- **Entidades:** `Usuario` (`usuarios`).
- **Eventos:** observa `EliminacionDeComercio` y `EliminacionDeRepartidor`.

### 2.9 Transportistas

- **Responsabilidad:** empresas de logística externas: alta, cotización, derivación, seguimiento por polling y cancelación con compensación.
- **EJB:**
  - `TransportistaService` `@Stateless` (`transportistas/negocio/TransportistaService.java:62`).
  - `CancelacionesDeEnvios` `@Stateless` (`:29`).
  - `SeguimientoDeEnvios` `@Singleton @Startup @RunAs("OPERADOR")` (`:40-43`).
  - `AdaptadoresTransportista` es CDI `@ApplicationScoped`.
- **Interfaces:**

| Interfaz | Operaciones | Usada por |
|---|---|---|
| `IGestionTransportistas` | `Long registrarTransportista(DatosTransportistaDTO)` · `darDeBajaTransportista(Long)` · `reactivarTransportista(Long)` · `listarTodos()` · `listarActivos()` | `RuteoService`, `ZonaService`, `SeguimientoDeEnvios`, `TransportistaBean`, `RuteoBean`, `PedidoBean` |
| `IEnvios` | `EnvioDTO solicitarEnvio(Long idPedido, Long idComercio, Long idTransportista, DatosEnvioDTO)` · `List<CotizacionDTO> cotizarEnvio(Long, DatosEnvioDTO)` · `void cancelarEnvioDePedido(Long)` · `listarEnvios()` · `listarEnviosDelComercioActual()` | `PedidoService`, `RuteoService`, `PedidoBean`, `TransportistaBean`, `PortalComercioBean` |
| `ISeguimientoEnvios` | `List<EnvioDTO> listarEnviosActivos()` · `void registrarNovedad(Long idEnvio, EstadoEnvio)` | `SeguimientoDeEnvios` (interna) |

- **Depende de:** `integracion` (adaptadores REST y SOAP), Seguridad (`IContextoUsuario`).
- **Entidades:** `Transportista` (`transportistas`), `Envio` (`envios`).
- **Eventos:**
  - Publica `EnvioSolicitado`, `EnvioCancelado` y `EstadoEnvioCambiado` (`:79-85`).
  - Observa los dos primeros en `CancelacionesDeEnvios`: `AFTER_FAILURE` y `AFTER_SUCCESS`.
  - `EstadoEnvioCambiado` lo observa Pedidos.
  - Observa `ComercioEliminado`.

### 2.10 Integración (capa técnica, no componente de negocio)

| Clase | Tipo | Rol |
|---|---|---|
| `integracion/banco/BancoClient` | `@Stateless`, implementa `IBancoClient` (`@Local`) | Cliente SOAP del banco, timeout 5 s, consulta al circuit breaker |
| `integracion/banco/CircuitBreakerBanco` | `@Singleton @Lock(WRITE)`, `NOT_SUPPORTED` | Estado del circuito |
| `integracion/banco/BancoLegadoService(Impl)` | `@WebService` | Banco legado **simulado** dentro del WAR |
| `integracion/transportistas/AdaptadorRestTransportista` / `AdaptadorSoapTransportista` | CDI `@ApplicationScoped`, `IAdaptadorTransportista` | Adapter por tecnología |
| `integracion/transportistas/simulador/*` | `@WebService` y `@Path` | Transportista legado SOAP y moderno REST **simulados** |

`IBancoClient`: `ResultadoAutorizacion autorizar(Long idPedido, BigDecimal)` · `ResultadoAutorizacion autorizarReferencia(String, BigDecimal)` · `boolean reversar(String codigoAutorizacion)`. Usada por `PagoService`, `CobroDeLiquidaciones`, `ReversasBancarias`.

### 2.11 Infraestructura (transversal)

- `AlineadorDeRestriccionesEnum` `@Singleton @Startup` BMT: al desplegar reemplaza los CHECK de 17 columnas enum (`infraestructura/AlineadorDeRestriccionesEnum.java:76-93`, ADR-008).
- `LimpiezaDeCredenciales` `@Singleton @Startup` BMT: `ALTER TABLE IF EXISTS usuarios DROP COLUMN IF EXISTS passwordhash` (`:49`).
- `ApiRest` `@ApplicationPath("api")`, `ProblemaMapper` `@Provider` `ExceptionMapper<Throwable>`, `Problema` (Problem Details).

### 2.12 Grafo de dependencias real (negocio)

Sale de los `@Inject` de las clases de `negocio/`:

| Desde | Hacia (interfaz) |
|---|---|
| Pedidos | Comercios (`IConsultaComercios`), Inventario (`IReservaStock`, `IConsultaStock`), Repartidores (`IAsignacionRepartidores`), Pagos (`IRegistroCobros`), Transportistas (`IEnvios`), Seguridad (`IContextoUsuario`) |
| Pagos | integración (`IBancoClient`), Pedidos (`ISeguimientoPedido`), Comercios (`IConsultaComercios`), Seguridad |
| Notificaciones | Pedidos (`ISeguimientoPedido`), Comercios, Seguridad |
| Ruteo | Pedidos (`IGestionPedidos`, `ISeguimientoPedido`), Comercios, Inventario (`IConsultaStock`), Repartidores (`IGestionRepartidores`), Transportistas (`IEnvios`, `IGestionTransportistas`) |
| Inventario | Comercios, Seguridad |
| Comercios | Seguridad |
| Repartidores | Seguridad |
| Seguridad | Comercios, Repartidores |
| Transportistas | integración (adaptadores), Seguridad |

---

## 3. Modelo de datos

### 3.1 Entidades

Todas usan `@Id @GeneratedValue(AUTO)`. Las referencias entre componentes se guardan como IDs (`Long`), no como relaciones JPA.

| Entidad (archivo) | Tabla | Atributos relevantes | Relaciones / restricciones |
|---|---|---|---|
| `Comercio` (`comercios/datos/model`) | `comercios` | nombre, razonSocial, cuit, email, telefono, activo | `@OneToMany` puntosPicking (cascade ALL) |
| `PuntoPicking` | `puntos_picking` | nombre, direccion, activa | `@ManyToOne` comercio (`comercio_id`) |
| `Deposito` (`inventario`) | `depositos` | nombre, direccion, provincia, localidad, codigoPostal | `@OneToMany` items |
| `ItemInventario` | `items_inventario` | producto, cantidadDisponible, cantidadReservada, pesoKg (8,3), idComercio, **`@Version` version** | `@ManyToOne` deposito (`deposito_id`) |
| `ReservaStock` | `reservas_stock` | producto, cantidad, idComercio, estado (`EstadoReserva`), fechaCreacion, fechaExpiracion, fechaCierre | `@ManyToOne` item (`item_id`) |
| `Pedido` (`pedidos`) | `pedidos` | idComercio, origen, idPuntoPicking, estado, importe (12,2), medioPago, pesoKg (10,3), direccionEntrega (200), codigoPostalEntrega (4), idRepartidor, fechas, **codigoSeguimiento (16, unique)**, fechaEntrega, entregadoPor, conRemito, enEsperaDesde, motivoEspera (300), idZonaEspera, idRepartidorExcluido, motivoDevolucion, cobroRegistrado, nombre/telefono/emailDestinatario (100/30/120), fechaPrometida, intentosFallidos, motivoEntregaFallida, ultimaFallaContoComoVisita, fechaUltimaFalla, salioAReparto | `@OneToMany` lineas (cascade ALL, orphanRemoval) |
| `LineaPedido` | `pedidos_lineas` | idItem, producto, cantidad, idReservaStock | `@ManyToOne` pedido (`pedido_id`) |
| `PedidoExterno` | `pedidos_externos` | idComercio, origen, idPuntoPicking, fechaPedido, importe, medioPago, pesoKg, direccionEntrega, codigoPostalEntrega, sincronizado, errorSincronizacion (500), idPedido, claveIdempotencia (100), huellaSolicitud (64), cancelado, destinatario, fechaPrometida | **`uk_pedido_externo_idempotencia` (idComercio, claveIdempotencia)**; `@OneToMany` lineas |
| `LineaPedidoExterno` | `pedidos_externos_lineas` | idItem, producto, cantidad | `@ManyToOne` pedidoExterno |
| `RemitoEntrega` | `remitos_entrega` | **idPedido (unique)**, tipoContenido, imagen (`byte[]`, LAZY), tamanoBytes, fechaCarga, cargadoPor | — |
| `IntentoEntrega` | `intentos_entrega` | idPedido, idRepartidor, registradoPor, fecha, motivo, comentario (500), numeroVisita, devueltoAlComercio, latitud, longitud, precisionMetros, origenUbicacion, fechaUbicacion, cantidadFotos | **índice (idPedido)** |
| `FotoIntentoEntrega` | `fotos_intento_entrega` | idIntento, idPedido, orden, tipoContenido, imagen (`byte[]`, LAZY), tamanoBytes | **índice (idIntento)** |
| `Cobro` (`pagos`) | `cobros` | **idPedido (unique, not null)**, idComercio, importe, medioPago, estado, fechaCreacion, fechaAcreditacion, codigoAutorizacion | — |
| `Liquidacion` | `liquidaciones` | idComercio, periodo (7, "AAAA-MM"), cantidadEnvios, kilos, abono, cargoEnvios, cargoKilos, total, estado, motivo, codigoAutorizacion, intentos, fechaGeneracion, fechaCobro | **unique (idComercio, periodo)** |
| `Repartidor` | `repartidores` | nombre, telefono, estado, idPedidoActual, capacidadKg (8,2), capacidadBultos, latitud, longitud, fechaUbicacion, idZona | — |
| `CargaAsignada` | `repartidores_cargas` | idRepartidor, **idPedido (unique)**, pesoKg, bultos, fechaAsignacion | — |
| `Zona` (`ruteo`) | `zonas` | nombre, codigoPostalDesde, codigoPostalHasta, cobertura, idTransportista, activa | — |
| `CoordenadaDireccion` | `coordenadas_direcciones` | **clave (300, unique)**, encontrada, latitud, longitud, fechaConsulta | — |
| `Notificacion` | `notificaciones` | idPedido, idComercio, estadoPedido, fechaCambio, fechaAviso, mensaje | — |
| `AvisoDestinatario` | `avisos_destinatario` | idPedido, idComercio, estadoPedido, fechaCambio, fechaAviso, email, asunto, mensaje | — |
| `Usuario` | `usuarios` | **username (unique)**, rol, activo, `hash_clave` (60, bcrypt), idComercio, idRepartidor | — |
| `Transportista` | `transportistas` | nombre, tipoIntegracion, endpoint (300), activo | — |
| `Envio` | `envios` | idPedido, idComercio, codigoSeguimiento, estado, fechaSolicitud, fechaActualizacion | `@ManyToOne(EAGER)` transportista |

### 3.2 Enums

| Enum | Valores | Fuente |
|---|---|---|
| `EstadoPedido` | PENDIENTE, CONFIRMADO, EN_CAMINO, ENTREGADO, CANCELADO, DEVUELTO_AL_COMERCIO | `pedidos/datos/model/EstadoPedido.java:52-58` |
| `OrigenPedido` | STOCK_CONSIGNADO, PUNTO_PICKING | `pedidos/datos/model/OrigenPedido.java` |
| `MotivoEntregaFallida` | 10 valores (ver 5) | `pedidos/datos/model/MotivoEntregaFallida.java:22-32` |
| `OrigenUbicacion` | DISPOSITIVO, ULTIMA_COMPARTIDA | `pedidos/datos/model/OrigenUbicacion.java` |
| `MedioPago` | PREPAGO, CONTRA_ENTREGA | `pagos/dto/MedioPago.java:14` |
| `EstadoCobro` | PENDIENTE, ACREDITADO, ANULADO | `pagos/datos/model/EstadoCobro.java:15` |
| `EstadoLiquidacion` | PENDIENTE, COBRADA, RECHAZADA | `pagos/datos/model/EstadoLiquidacion.java:12` |
| `EstadoReserva` | VIGENTE, CONFIRMADA, LIBERADA, EXPIRADA, DEVUELTA | `inventario/datos/model/EstadoReserva.java` |
| `EstadoRepartidor` | DISPONIBLE, OCUPADO | `repartidores/datos/model/EstadoRepartidor.java:15` |
| `CoberturaZona` | PROPIA, TRANSPORTISTA | `ruteo/datos/model/CoberturaZona.java` |
| `Rol` | ADMINISTRADOR, OPERADOR, COMERCIO, REPARTIDOR, ERP | `seguridad/datos/model/Rol.java:10` |
| `TipoIntegracion` | REST, SOAP_LEGADO | `transportistas/datos/model/TipoIntegracion.java:4` |
| `EstadoEnvio` | SOLICITADO, EN_TRANSITO, ENTREGADO, CANCELADO | `transportistas/datos/model/EstadoEnvio.java:4` |

### 3.3 ¿Algún componente lee tablas de otro?

- **No, desde los repositorios.** Cada `*Repository` consulta solo las entidades de su componente:
  - `CobroRepository` → `Cobro`; `LiquidacionRepository` → `Liquidacion`.
  - `PedidoRepository` → `Pedido`, `LineaPedido`, `PedidoExterno`, `RemitoEntrega`, `IntentoEntrega`, `FotoIntentoEntrega`.
  - `InventarioRepository` → `Deposito`, `ItemInventario`, `ReservaStock`.
  - `NotificacionRepository` → `Notificacion`, `AvisoDestinatario`.
  - `TransportistaRepository` → `Envio`, `Transportista`.
  - `RepartidorRepository` → `Repartidor`, `CargaAsignada`.
  - `UsuarioRepository` → `Usuario`; `ComercioRepository` → `Comercio`, `PuntoPicking`.
  - `ZonaRepository` y `CoordenadaRepository` → `Zona` y `CoordenadaDireccion`.
- **Excepciones de infraestructura:** `AlineadorDeRestriccionesEnum` ejecuta `ALTER TABLE` sobre tablas de todos los componentes al desplegar (`:116-118`), y `LimpiezaDeCredenciales` sobre `usuarios` (`:49`). No son lecturas de negocio.
- **Todos comparten la misma unidad de persistencia y la misma base** (`comerciosPU`, `RabbitDS`). La separación es por código, no por esquema.
- **El login de WildFly sí lee la tabla `usuarios`:**
  - Lo hace con el `jdbc-realm` `RabbitRealmBase` (`scripts/configurar-realm-compartido.cli`).
  - Primera consulta: `SELECT hash_clave FROM usuarios WHERE username = ? AND activo AND hash_clave IS NOT NULL`.
  - Segunda consulta: `SELECT rol FROM usuarios WHERE username = ? AND (hash_clave IS NOT NULL OR NOT activo)`.
  - Va dentro del `distributed-realm` `RabbitRealmCompartido` (base primero, después `ApplicationRealm` de archivos).

---

## 4. Ciclo de vida del pedido

### 4.1 Estados y matriz exacta

`EstadoPedido.puedePasarA` (`pedidos/datos/model/EstadoPedido.java:66-74`):

| Desde \ Hacia | PENDIENTE | CONFIRMADO | EN_CAMINO | ENTREGADO | CANCELADO | DEVUELTO_AL_COMERCIO |
|---|---|---|---|---|---|---|
| PENDIENTE | — | ✔ | ✘ | ✘ | ✔ | ✘ |
| CONFIRMADO | ✔ | ✘ | ✔ | ✘ | ✔ | ✔ |
| EN_CAMINO | ✔ | ✘ | ✘ | ✔ | ✘ | ✔ |
| ENTREGADO / CANCELADO / DEVUELTO_AL_COMERCIO | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |

- El único punto que cambia el estado es `PedidoService.cambiarEstado` (`:1255-1271`). Valida, limpia la espera si el destino no es PENDIENTE, marca `salioAReparto` al pasar a EN_CAMINO y dispara `EstadoPedidoCambiado`.
- **Nombre exacto del estado de tránsito del pedido: `EN_CAMINO`** ("En camino" en pantalla). `EN_TRANSITO` es un estado del **envío** del transportista (`EstadoEnvio`), no del pedido.
- **Lista de espera:** no es un estado. Es un pedido `PENDIENTE` con `enEsperaDesde != null` (`Pedido.java:219`).

### 4.2 Transiciones: método, actor, stock y cobro

| Transición | Método (rol) | Stock | Cobro |
|---|---|---|---|
| (alta) → `PedidoExterno` | `registrarPedidoExterno` (ADMINISTRADOR, OPERADOR, ERP) `:195-318` | — | — |
| `PedidoExterno` → PENDIENTE | `sincronizarPedidoExterno` (sin rol: MDB o timer) `REQUIRES_NEW` `:347-449` | STOCK_CONSIGNADO: reserva + confirma cada línea (stock descontado); PUNTO_PICKING: nada | — |
| PENDIENTE → CONFIRMADO | `confirmarPedido` / `confirmarPedidoEnZona` (ADMIN, OPERADOR) `:492-547`; `asignarDesdeEspera` (timer `@RunAs OPERADOR`) `:581-594` | — | `registrarCobro`: PREPAGO → banco (ACREDITADO); CONTRA_ENTREGA → PENDIENTE |
| PENDIENTE → (lista de espera) | dentro de `confirmar` si `motivoParaEsperar != null` `:516-521` | — | **no cobra** |
| PENDIENTE → CONFIRMADO (derivado) | `derivarATransportista` (ADMIN, OPERADOR) `:763-790` | — | cobra si falta; `idRepartidor = null` |
| CONFIRMADO → EN_CAMINO | `despacharPedido` (ADMIN, OPERADOR, REPARTIDOR propio) `:837-844`; o `ActualizacionDePedidosPorEnvio` con EN_TRANSITO | — | — |
| EN_CAMINO → ENTREGADO | `registrarEntrega(id, foto)` (ADMIN, REPARTIDOR) `:849-874`; `registrarEntregaDeTransportista` (timer `@RunAs OPERADOR`) `:879-889` | — | CONTRA_ENTREGA se acredita en Pagos al recibir ENTREGADO por el tópico |
| CONFIRMADO/EN_CAMINO → PENDIENTE (visita fallida con visitas restantes) | `devolverADeposito` (ADMIN, REPARTIDOR) `:610-676` | — (sigue comprometido) | conserva el cobro |
| CONFIRMADO/EN_CAMINO → DEVUELTO_AL_COMERCIO | `devolverADeposito` cuando se agotan las visitas, hay rechazo o paquete dañado `:651-665` | devuelve stock (`registrarDevolucion`) salvo PAQUETE_DANADO | Pagos lo anula al recibir el estado (`anularCobroPorDevolucion`) y reversa en el banco si fue prepago |
| PENDIENTE/CONFIRMADO → CANCELADO | `cancelarPedido` (ADMIN, OPERADOR) `:929-934`; `cancelarPedidoExterno` (ERP, solo PENDIENTE sin cobro) `:936-976` | devuelve stock | CONFIRMADO: `anularCobro` (solo **ADMINISTRADOR**) + libera repartidor + cancela envío; PENDIENTE con cobro: anula |

**Regla de no cancelación (ADR-048)** — `ReglaDeCancelacion.motivoParaNoCancelar` (`pedidos/negocio/ReglaDeCancelacion.java:34-56`):

- Bloquea EN_CAMINO.
- Bloquea PENDIENTE/CONFIRMADO si `salioAReparto`. `isSalioAReparto()` es true también con `intentosFallidos > 0` (`Pedido.java:250-253`).
- Bloquea los estados finales.

### 4.3 Visitas fallidas

**`IntentosDeEntrega.evaluar`** (`pedidos/negocio/IntentosDeEntrega.java`):

- Cuenta como visita solo si el pedido estaba EN_CAMINO y el motivo cuenta.
- Devuelve al comercio cuando:
  - el motivo devuelve al comercio y hubo visita, o el motivo no cuenta como visita (PAQUETE_DANADO);
  - o las visitas llegan al máximo (2 por defecto, `rabbit.entregas.max-intentos`, 1 a 10).

**Al devolver al depósito:**

- Se excluye al repartidor que lo devolvió (`idRepartidorExcluido`).
- Entra en la lista de espera.
- Se guarda el `IntentoEntrega` con fotos y ubicación.

---

## 5. Reglas de negocio con sus valores reales

| Regla | Valor | Constante / propiedad | Fuente |
|---|---|---|---|
| Hora de corte | 07:00; `ninguno` la desactiva | `rabbit.ruteo.hora-corte`, `HoraDeCorte.POR_DEFECTO` | `repartidores/negocio/HoraDeCorte.java:28-34` |
| Fecha prometida | antes del corte: hoy; después: mañana (sin CP: null) | `HoraDeCorte.diaDeEntrega` | `HoraDeCorte.java:84-88`, `PedidoService.java:269` |
| Capacidad del vehículo | opcional (kg y/o bultos); sin capacidad: 1 pedido a la vez | `CapacidadRepartidor.entra` | `repartidores/negocio/CapacidadRepartidor.java:33-47` |
| Tope de capacidad cargable | 10.000 kg, 1.000 bultos | `CAPACIDAD_MAXIMA_KG/BULTOS` | `RepartidorService.java:56-57` |
| Peso máximo de un pedido (punto de picking) | 5.000 kg, 3 decimales | `PESO_MAXIMO_PEDIDO_KG` | `PedidoService.java:143,320-339` |
| Peso por unidad de stock | máx. 1.000 kg | `PESO_MAXIMO_KG` | `inventario/negocio/ConsultaStockService.java:54` |
| Visitas antes de devolver | 2 (1..10) | `rabbit.entregas.max-intentos` | `IntentosDeEntrega.java:30-31` |
| Vencimiento de reserva | 5 min (extensible) | `MINUTOS_DE_HOLD` | `InventarioService.java:80` |
| Timeout de la conversación stateful | 30 min | `@StatefulTimeout` | `InventarioService.java:72` |
| Tarifa de liquidación | $15.000 abono, $1.200 por envío entregado, $80 por kilo | `rabbit.tarifa.abono / por-envio / por-kilo` | `pagos/negocio/TarifaMensual.java:25-30` |
| Límite del banco simulado | $500.000 | `BancoLegadoServiceImpl.LIMITE` | `integracion/banco/BancoLegadoServiceImpl.java:40` |
| Límite de bultos del transportista simulado | 50 | `SimuladorDeEnvios.MAX_BULTOS` | `integracion/transportistas/simulador/SimuladorDeEnvios.java:32` |
| Cotización simulada | $2.500 + $350 por bulto (+ $500 si cobra al entregar), 24 h | `PRECIO_BASE`, `PRECIO_POR_BULTO`, `RECARGO_COBRO`, `PLAZO_HORAS` | `SimuladorDeEnvios.java:62-65` |
| Avance del envío simulado | 20 s por paso | `rabbit.transportista.simulador.segundos` | `SimuladorDeEnvios.java:116` |
| Bloqueo de login | 5 intentos → 15 minutos | `LimiteDeIntentos.MAXIMO`, `BLOQUEO` | `seguridad/presentacion/LimiteDeIntentos.java:28-29` |
| Contraseña | mínimo 6 caracteres, bcrypt costo 10 | — | `UsuarioService.java:276`, `seguridad/negocio/Claves.java:20` |
| Username | `^[A-Za-z0-9._-]{3,30}$` | `PATRON_USERNAME` | `seguridad/negocio/ApplicationRealmSync.java:40` |
| Foto del remito / de visita | JPG, PNG o WEBP por firma de bytes, máx. 5 MB | `Remitos.TAMANO_MAXIMO` | `pedidos/negocio/Remitos.java:12,34-51` |
| Fotos por visita | hasta 3; comentario hasta 500 | `EvidenciaDeVisita` | `pedidos/negocio/EvidenciaDeVisita.java:27-28` |
| Ubicación de la visita | GPS con precisión ≤ 5.000 m; si no, última compartida de ≤ 10 min | — | `EvidenciaDeVisita.java:30-32,98-110` |
| Ubicación vigente en el mapa | 15 min | `MINUTOS_UBICACION_VIGENTE` | `RepartidorService.java:61` |
| Multipart web | 8 MB por archivo, 26 MB por request | `web.xml` | `src/main/webapp/WEB-INF/web.xml` |
| Código de seguimiento | `RB-` + 10 caracteres de `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (SecureRandom) | `CodigosDeSeguimiento` | `pedidos/negocio/CodigosDeSeguimiento.java:17-30` |
| Código postal | 4 dígitos o CPA (`C1414ABC`); se infiere de la dirección | `CodigosPostales` | `pedidos/negocio/CodigosPostales.java:19-53` |
| Rango de zonas | CP 1000 a 9999, sin superponerse | `CP_MINIMO/MAXIMO` | `ruteo/negocio/ZonaService.java:35-36` |
| Destinatario | email obligatorio (≤120); nombre ≤100; teléfono 6–30 | `Destinatarios` | `pedidos/negocio/Destinatarios.java:24-72` |
| Idempotency-Key | `[A-Za-z0-9_-]{8,100}` | `CLAVE_VALIDA` | `pedidos/presentacion/rest/PedidosExternosResource.java:101` |
| Minutos por parada (franja) | 20 (5..120) | `rabbit.ruteo.minutos-por-parada` | `ruteo/negocio/EstimacionDeLlegada.java:38-39` |
| Paradas por link de Google Maps | 9 | `EnlaceMapa.PARADAS_POR_TRAMO` | `ruteo/negocio/EnlaceMapa.java:39` |
| Geocodificación | 20 direcciones por pasada, 1,1 s entre consultas, reintento de no encontradas a los 7 días | `POR_PASADA`, `PAUSA_MS`, `DIAS_PARA_REINTENTAR` | `Geocodificador.java:60-61`, `CacheDeCoordenadas.java:30` |
| Indicadores | períodos 7, 30, 90 días (30 por defecto) | `IIndicadores.PERIODOS` | `IIndicadores.java:14`, `IndicadoresBean.java:52` |

**Motivos de entrega fallida** (`MotivoEntregaFallida.java:23-32`):

| Motivo | Descripción | Cuenta como visita | Devuelve al comercio | Pide comentario | Pide foto |
|---|---|---|---|---|---|
| CLIENTE_AUSENTE | Cliente ausente | sí | no | no | no |
| DIRECCION_INCORRECTA | Dirección incorrecta o inexistente | sí | no | no | no |
| NADIE_HABILITADO | Nadie habilitado para recibir | sí | no | no | no |
| DOMICILIO_CERRADO | Local o domicilio cerrado | sí | no | no | no |
| ZONA_PELIGROSA | Zona inaccesible o insegura | sí | no | no | no |
| RECHAZADO | El cliente rechazó el pedido | sí | **sí** | **sí** | no |
| PAQUETE_DANADO | Paquete dañado | **no** | **sí** (sin devolver stock) | no | **sí** |
| NO_ENTRA_EN_VEHICULO | No entra en el vehículo | **no** | no | no | no |
| PROBLEMA_DEL_REPARTIDOR | Problema del repartidor | **no** | no | no | no |
| OTRO | Otro | sí | no | **sí** | no |

---

## 6. Actores, roles y pantallas

### 6.1 Roles y mecanismos de restricción

- **Roles:** ADMINISTRADOR, OPERADOR, COMERCIO, REPARTIDOR, ERP (`seguridad/datos/model/Rol.java:10`).
- **EJB:** `@RolesAllowed` en las operaciones sensibles, con `@PermitAll` de clase en los servicios que llaman timers o MDB sin usuario. Ver la columna "rol" de la sección 4.2 y del componente.
- **web.xml:**
  - `/api/v1/pedidos-externos/*` exige el rol `ERP` + `CONFIDENTIAL`.
  - `/` exige `CONFIDENTIAL` (HTTPS).
  - `/api/simulador/*`, `/BancoLegadoService` y `/TransportistaLegadoService` van con `NONE` (HTTP).
  - `login-config`: `BASIC`, realm `ApplicationRealm`.
- **Pantallas:** no tienen `security-constraint` por rol. Las protegen el login programático (`LoginBean.login` → `request.login`, `seguridad/presentacion/LoginBean.java:63`) y un `f:viewAction` por página que llama a `SesionBean.exigirPersonal / exigirAdministrador / exigirComercio / exigirRepartidor / exigirPersonalOComercio` (`seguridad/presentacion/SesionBean.java:69-113`). Sin el rol, redirige a la página de inicio del rol del usuario o al login.
- **"Cada uno ve lo suyo":**
  - `IContextoUsuario` deriva el comercio o el repartidor de la **identidad autenticada** (`ContextoUsuarioService.java:36-62`), nunca de un parámetro.
  - Lo usan las operaciones `*DelComercioActual` y `*DelRepartidorActual`.
  - `exigirRepartidorAsignado` (`PedidoService.java:1128`), `puedeVerRemito` (`:912`) y `verificarQueEsDelErp` (`:1185`) completan el control. Lo ajeno se informa como inexistente (404).
- **Cabecera de sesión:** cookie `HttpOnly` + `Secure`, seguimiento solo por cookie (`web.xml`).

### 6.2 Pantallas por rol

Menú: `src/main/webapp/WEB-INF/plantillas/template.xhtml:59-105`.

| Rol | Página | Guarda | Qué permite (acciones del bean) |
|---|---|---|---|
| Personal (ADMIN+OPERADOR) | `personal/pedidos.xhtml` (inicio) | `exigirPersonal` | listar, confirmar, cancelar, cotizar, derivar, despachar, registrar entrega (admin), simular pedido del ERP (`registrarPedidoExterno`), ver avisos |
| Personal | `personal/ruteo.xhtml` | `exigirPersonal` | zonas (alta, baja, reactivar, eliminar), despachar pedido o zona |
| Personal | `personal/entregas.xhtml` | `exigirPersonal` | tablero de entregas en curso (solo lectura) |
| Personal | `personal/mapa.xhtml` | `exigirPersonal` | mapa Leaflet + OSM de repartidores (`mapaBean.actualizar`) |
| Personal | `personal/indicadores.xhtml` | `exigirPersonal` | KPIs, gráficos, tabla y CSV |
| Personal | `personal/repartidores.xhtml` (Repartos) | `exigirPersonal` | alta, zona, capacidad, eliminar (admin) |
| Personal | `personal/transportistas.xhtml` (pestaña de Repartos) | `exigirPersonal` | alta, baja, reactivar transportistas |
| Personal | `personal/comercios.xhtml` | `exigirPersonal` | alta, baja, reactivar, eliminar (admin) |
| Personal | `personal/depositos.xhtml` | `exigirPersonal` | alta de depósitos |
| Personal | `personal/items.xhtml` (stock de un depósito) | `exigirPersonal` | cargar, editar, eliminar (admin) stock |
| Personal | `personal/reservas.xhtml` | `exigirPersonal` | reservar, confirmar, extender, liberar |
| Personal | `personal/historial.xhtml` | `exigirPersonal` | historial de reservas con filtros |
| Personal | `personal/liquidaciones.xhtml` | `exigirPersonal` | liquidar mes, reintentar cobro (los EJB exigen ADMIN) |
| Administrador | `personal/usuarios.xhtml` | `exigirAdministrador` | crear, dar de baja, reactivar, restablecer, eliminar cuentas |
| Comercio | `comercio/mis-pedidos.xhtml` (inicio) | `exigirComercio` | pedidos, remito, avisos, seguimiento |
| Comercio | `comercio/mis-indicadores.xhtml` | `exigirComercio` | KPIs propios y CSV |
| Comercio | `comercio/mi-stock.xhtml` | `exigirComercio` | stock propio (lectura) |
| Comercio y personal | `comercio/puntos-picking.xhtml` | `exigirPersonalOComercio` | alta, baja, reactivar puntos de picking |
| Comercio y personal | `comercio/visitas.xhtml` | `exigirPersonalOComercio` | visitas fallidas con evidencia |
| Comercio | `comercio/mis-liquidaciones.xhtml` | `exigirComercio` | liquidaciones y vista previa del mes |
| Repartidor | `repartidor/mis-entregas.xhtml` (inicio) | `exigirRepartidor` | hoja de ruta, retirar, entregar con foto, devolver con motivo, compartir ubicación |
| Público | `login.xhtml`, `seguimiento.xhtml` | — | ingreso; seguimiento por código |
| — | Servlets `/remito` y `/foto-visita` | autorización en el EJB (`obtenerRemito`, `obtenerFotoDeVisita`) | imágenes |
| ERP | sin pantallas; solo API REST | `web.xml` + `@RolesAllowed("ERP")` | ver sección 8 |

---

## 7. Integración

### 7.1 Sistemas externos

| Sistema | Canal | Dirección | Clase adaptadora | URL / configuración | Timeout |
|---|---|---|---|---|---|
| ERP del comercio | REST/JSON, HTTP Basic, HTTPS | Entrante | `PedidosExternosResource` | `/Rabbit/api/v1/pedidos-externos` | — |
| Cliente final | REST/JSON y JSF | Entrante | `SeguimientoResource`, `SeguimientoBean` | `/api/v1/seguimiento/{codigo}`, `seguimiento.xhtml` | — |
| Banco legado | SOAP 1.1 document/literal | Saliente | `BancoClient` (+ `CircuitBreakerBanco`) | `rabbit.banco.wsdl` (def. `http://localhost:8080/Rabbit/BancoLegadoService?wsdl`, se lee al desplegar) | 5.000 ms connect y receive (`BancoClient.java:54,113-114`) |
| Transportista legado | SOAP | Saliente | `AdaptadorSoapTransportista` | `Transportista.endpoint` (URL del WSDL, cargada en la pantalla) | 5.000 ms (`AdaptadorSoapTransportista.java:34`) |
| Transportista moderno | REST/JSON | Saliente | `AdaptadorRestTransportista` | `Transportista.endpoint` (base URL); simulado en `/Rabbit/api/simulador/transportista-rest` | 5 s connect y read (`AdaptadorRestTransportista.java:40-46`) |
| Servidor SMTP (Gmail por configuración) | SMTP (Jakarta Mail), STARTTLS/SSL | Saliente | `AvisosPorMail` + `ConfiguracionSmtp` | `rabbit.mail.smtp.*`, `rabbit.mail.remitente` (sin host no manda) | 5.000 ms connection/read/write (`ConfiguracionSmtp.java:24,66-68`) |
| OpenStreetMap Nominatim | HTTP/JSON (GET) | Saliente | `Geocodificador` + `Nominatim` | `rabbit.geocodificacion.url` (def. `https://nominatim.openstreetmap.org/search`); `rabbit.geocodificacion=ninguna` lo apaga | 5 s connect, 8 s request (`Geocodificador.java:72,136`) |
| Tiles de OpenStreetMap | HTTPS desde el navegador | Saliente (cliente) | `mapa.js` (Leaflet 1.9.4 de cdnjs) | `https://{s}.tile.openstreetmap.org` | — |
| Google Maps | Link (sin API key) | Saliente (navegador) | `EnlaceMapa`, `IntentoEntregaDTO` | `https://www.google.com/maps/dir/?api=1…` | — |
| Base PostgreSQL (Supabase) | JDBC (datasource JNDI) | — | Repositorios JPA; realm JDBC de Elytron | `java:jboss/datasources/RabbitDS` | pool: `socketTimeout=30`, `connectTimeout=10` (README) |

### 7.2 Integraciones internas

| Mecanismo | Instancias |
|---|---|
| Interfaces `@Local` (llamada sincrónica en proceso) | 26 interfaces (sección 2) |
| Cola JMS punto a punto | `cola.pedidos.externos` (sección 10) |
| Tópico JMS pub/sub | `topico.pedidos.estado` con 3 suscriptores (sección 10) |
| Eventos CDI con fase de transacción | `PedidoExternoRegistrado`, `EstadoPedidoCambiado` → AFTER_SUCCESS (publicadores, `AvisosDeEspera`); `PagoAutorizado`, `EnvioSolicitado` → AFTER_FAILURE (compensaciones); `CobroAnulado`, `EnvioCancelado`, `AvisoRegistrado`, `AvisoDestinatarioRegistrado` → AFTER_SUCCESS |
| Eventos CDI sincrónicos | `EstadoEnvioCambiado` (Transportistas → Pedidos); veto y limpieza: `EliminacionDeComercio`, `ComercioEliminado`, `EliminacionDeItem`, `EliminacionDeRepartidor`, `RepartidorEliminado`, `ZonaEliminada` |
| Base de datos compartida (login) | Realm JDBC de Elytron sobre `usuarios` |

### 7.3 Modelo de integración (según el código)

- **Hacia afuera: punto a punto.**
  - Cada sistema externo lo usa un solo componente, a través de un adaptador:
    - banco → Pagos vía `IBancoClient`;
    - transportistas → Transportistas vía `IAdaptadorTransportista`;
    - SMTP → Notificaciones;
    - Nominatim → Ruteo;
    - ERP → Pedidos.
  - No hay bus ni ESB: **NO ENCONTRADO** ningún componente de enrutamiento o transformación central.
- **Adentro: híbrido.**
  - Llamadas punto a punto por interfaces `@Local`.
  - El tópico actúa como **hub** para los cambios de estado del pedido: un productor (`PublicadorEstadosPedido`) y tres consumidores que el productor no conoce.
- La justificación está en la documentación funcional fuera del repo (sección 7) y en `docs/integraciones/MensajeriaFinal.md`.

### 7.4 Tipos de integración usados

| Tipo | Dónde | Evidencia |
|---|---|---|
| Procedural (RPC/sincrónico) | API REST del ERP, SOAP banco, SOAP/REST transportistas, Nominatim, interfaces `@Local` | `PedidosExternosResource`, `BancoClient`, adaptadores, `Geocodificador` |
| Procesos (mensajería asincrónica/eventos) | Cola y tópico JMS; eventos CDI con fase de transacción | `PublicadorPedidosExternos`, `PublicadorEstadosPedido`, MDB |
| Portales | Portal del comercio y seguimiento: una pantalla compone datos de varios componentes | `PortalComercioBean` (Pedidos, Pagos, Inventario, Notificaciones, Transportistas, Repartidores), `SeguimientoBean` (Pedidos + Repartidores + Ruteo) |
| Datos | Login de WildFly leyendo la tabla `usuarios` (jdbc-realm) | `scripts/configurar-realm-compartido.cli` |
| Archivos | (a) CSV de indicadores, **generado en el navegador** (`webapp/resources/rabbit/1_19/indicadores.js:272-294`), no es una integración entre sistemas sino una exportación al usuario; (b) `ApplicationRealmSync` lee y escribe `application-users.properties` / `application-roles.properties` de WildFly (`seguridad/negocio/ApplicationRealmSync.java:54-147`) | ver contradicción 2 |

---

## 8. API REST

Base: `https://<host>:8443/Rabbit/api` (`infraestructura/ApiRest.java:10`). Contrato: `docs/integraciones/openapi.yaml`.

### 8.1 Endpoints expuestos

| Método y ruta | Rol | Headers | Cuerpo | Respuestas (causa) | Fuente |
|---|---|---|---|---|---|
| `POST /v1/pedidos-externos` | ERP (web.xml + `@RolesAllowed`) | `Authorization: Basic`, **`Idempotency-Key`** (obligatorio, `[A-Za-z0-9_-]{8,100}`), `Content-Type: application/json` | `PedidoExternoRequest`: origen, idPuntoPicking, lineas[1..100] {idItem, producto≤200, cantidad>0}, importe (>0, 10,2), medioPago, direccionEntrega≤200, codigoPostalEntrega≤8, pesoKg (0.001, 7,3), nombre≤100, telefono≤30, **emailDestinatario obligatorio**≤120 | **201** + `Location` (también al reintentar con la misma clave: siempre `Response.created`) · **400** `cuerpo-invalido`, `clave-idempotencia-invalida`, `datos-con-formato-invalido` (con `errores[]`) · **401** sin credenciales (contenedor) · **403** `cuenta-erp-sin-comercio`, `acceso-denegado` · **422** `clave-idempotencia-reutilizada`, `datos-invalidos` · **500** `error-interno` | `PedidosExternosResource.java:123-158` |
| `GET /v1/pedidos-externos/{id}` | ERP | Basic | — | **200** representación con `resultado`, `idPedido`, `estadoPedido`, `codigoSeguimiento`, `fechaPrometida`, `intentosFallidos`, `enListaDeEspera`, `motivo`, `_links` (self, cancelar, seguimiento) · **404** `pedido-inexistente` (también si es de otro comercio) · **403** `cuenta-erp-sin-comercio` | `:166-176`, `:222-269` |
| `POST /v1/pedidos-externos/{id}/cancelacion` | ERP | Basic | — | **200** (idempotente) · **404** `pedido-inexistente` · **409** `cancelacion-no-permitida`, `stock-en-conflicto` · **422** `cancelacion-fallida` · **403** | `:183-203` |
| `GET /v1/seguimiento/{codigo}` | Público (`@PermitAll`) | — | — | **200** {codigoSeguimiento, estado, fechaPrometida?, llegadaEstimada{numeroParada, totalParadas, desde, hasta}?, ubicacionRepartidor{latitud, longitud, actualizada}?} · **404** `pedido-inexistente` | `SeguimientoResource.java:61-98` |

### 8.2 Problem Details: todos los `type`

- **Formato:** `type = "/problemas/<tipo>"`, `Content-Type: application/problem+json;charset=UTF-8` (`infraestructura/Problema.java:24-34,71-75`).
- **Tipos de los recursos:**
  - `cuerpo-invalido`, `clave-idempotencia-invalida`, `datos-con-formato-invalido` (400);
  - `clave-idempotencia-reutilizada`, `datos-invalidos`, `cancelacion-fallida` (422);
  - `cancelacion-no-permitida`, `stock-en-conflicto` (409);
  - `pedido-inexistente` (404);
  - `cuenta-erp-sin-comercio` (403).
- **Tipos de `ProblemaMapper`** (`infraestructura/ProblemaMapper.java:51-92`):
  - `cuerpo-invalido` (400);
  - `no-autenticado` (401);
  - `acceso-denegado` (403);
  - `recurso-inexistente` (404);
  - `metodo-no-admitido` (405);
  - `formato-no-disponible` (406);
  - `formato-no-admitido` (415);
  - `error-http` (otros 4xx);
  - `error-interno` (5xx).

### 8.3 Idempotencia

- **Huella:** SHA-256 hex de origen | idPuntoPicking | importe | medioPago | dirección | CP resuelto | peso (si vino) | nombre, teléfono y email normalizados (si vinieron) | cada línea idItem:producto:cantidad (`pedidos/negocio/HuellaDePedido.java:25-53`). Se guarda en `PedidoExterno.huellaSolicitud` (64).
- **Misma clave y misma huella:** devuelve el pedido existente. **Misma clave y otra huella:** `ClaveIdempotenciaReutilizadaException` (422) (`PedidoService.java:239-251`).
- **Carrera de dos reintentos simultáneos:**
  - La resuelve la restricción única `uk_pedido_externo_idempotencia (idComercio, claveIdempotencia)` (`PedidoExterno.java`, `@Table`).
  - El recurso reintenta una vez ante `EJBException` (`PedidosExternosResource.java:209-218`).

### 8.4 REST consumido (transportista moderno)

`AdaptadorRestTransportista.java`, contrato del simulador `TransportistaRestSimuladoResource.java`:

| Método y ruta (sobre `endpoint`) | Cuerpo | Respuestas tratadas |
|---|---|---|
| `POST /cotizaciones` | {referencia, direccionRetiro, direccionEntrega, bultos, cobrarAlEntregar?} | 200 {precio, plazoHoras} · 422 {error} · otro → NO_DISPONIBLE |
| `POST /envios` | mismo cuerpo | 201 {codigoSeguimiento} · 422 {error} → RECHAZADO · otro → NO_DISPONIBLE |
| `GET /envios/{codigo}` | — | 200 {estado: SOLICITADO, EN_TRANSITO, ENTREGADO, CANCELADO} · otro → DESCONOCIDO |
| `DELETE /envios/{codigo}` | — | 204 o 404 → ok · 409 (ya entregado) u otro → falla |

---

## 9. SOAP

### 9.1 Banco legado

- **Interfaz:** `integracion/banco/BancoLegadoService.java:27-50`, `name = "BancoLegadoPortType"`, namespace `http://rabbit.example/legado/banco`, DOCUMENT/LITERAL/WRAPPED.
- **Publicado por:** `BancoLegadoServiceImpl` (`serviceName = "BancoLegadoService"`, `portName = "BancoLegadoPort"`).
- **WSDL:** `http://localhost:8080/Rabbit/BancoLegadoService?wsdl`.

```java
@WebService(name = "BancoLegadoPortType", targetNamespace = "http://rabbit.example/legado/banco")
@SOAPBinding(style = DOCUMENT, use = LITERAL, parameterStyle = WRAPPED)
public interface BancoLegadoService {
    @WebMethod(operationName = "autorizarPago") @WebResult(name = "codigoAutorizacion")
    String autorizarPago(@WebParam(name = "referencia") String referencia,
                         @WebParam(name = "importe") BigDecimal importe) throws PagoRechazadoException;
    @WebMethod(operationName = "reversarPago")
    void reversarPago(@WebParam(name = "codigoAutorizacion") String codigoAutorizacion);
}
```

| Operación | Entrada | Salida | Fault | Cuándo |
|---|---|---|---|---|
| `autorizarPago` | referencia (`PEDIDO-<id>` o `LIQ-<id>`), importe | codigoAutorizacion (`AUT-n`) | `PagoRechazado` {motivo} (`PagoRechazadoException.java:10`) | importe null o > $500.000 (`BancoLegadoServiceImpl.java:52-56`) |
| `reversarPago` | codigoAutorizacion | void | — | idempotente |

- **Caída simulada:** `rabbit.banco.simular.caida=true` demora 10 s y lanza una excepción (`:74-87`).

### 9.2 Transportista legado

- **Interfaz:** `integracion/transportistas/TransportistaLegadoService.java:19-42`, `TransportistaLegadoPortType`, namespace `http://rabbit.example/legado/transportista`.
- **Publicado por:** `simulador/TransportistaLegadoServiceImpl` (`TransportistaLegadoService` / `TransportistaLegadoPort`).
- **WSDL:** `http://localhost:8080/Rabbit/TransportistaLegadoService?wsdl`.

```java
@WebService(name = "TransportistaLegadoPortType", targetNamespace = "http://rabbit.example/legado/transportista")
@SOAPBinding(style = DOCUMENT, use = LITERAL, parameterStyle = WRAPPED)
public interface TransportistaLegadoService {
    @WebMethod(operationName = "registrarEnvio") @WebResult(name = "codigoSeguimiento")
    String registrarEnvio(@WebParam(name = "referencia") String referencia,
                          @WebParam(name = "direccionRetiro") String direccionRetiro,
                          @WebParam(name = "direccionEntrega") String direccionEntrega,
                          @WebParam(name = "bultos") int bultos) throws EnvioRechazadoException;
    @WebMethod(operationName = "consultarEnvio") @WebResult(name = "estado")
    String consultarEnvio(@WebParam(name = "codigoSeguimiento") String codigoSeguimiento);
    @WebMethod(operationName = "anularEnvio")
    void anularEnvio(@WebParam(name = "codigoSeguimiento") String codigoSeguimiento) throws EnvioRechazadoException;
}
```

| Operación | Salida | Fault | Cuándo |
|---|---|---|---|
| `registrarEnvio` | `TL-…` | `EnvioRechazado` {motivo} | bultos < 1 o > 50 |
| `consultarEnvio` | RECIBIDO, EN_VIAJE, ENTREGADO, ANULADO (null si no existe) | — | — |
| `anularEnvio` | void | `EnvioRechazado` | ya entregado |

- **No cotiza:** el adaptador responde `NO_COTIZA` sin llamar (`AdaptadorSoapTransportista.java:55-58`).
- **Traducción de estados:** RECIBIDO→SOLICITADO, EN_VIAJE→EN_TRANSITO, ANULADO→CANCELADO (`:87-98`).

### 9.3 Generación del cliente y cambio de endpoint

- **No hay `wsimport`** ni clases generadas: el cliente es dinámico.
  - `Service.create(new URL(wsdl), QName)` + `getPort(PORT, Interfaz.class)`, reutilizando la interfaz `@WebService` anotada.
  - Ver `BancoClient.java:110-130` y `AdaptadorSoapTransportista.java:100-111`.
- **Banco:** el endpoint sale de `rabbit.banco.wsdl`, una constante `static final` que se lee al cargar la clase (redeploy). El `Service` se cachea.
- **Transportista legado:** el endpoint es el `endpoint` del transportista en la base; hay un `Service` en caché por WSDL.

---

## 10. Mensajería JMS

### 10.1 Destinos

| | Cola | Tópico |
|---|---|---|
| Nombre físico | `cola.pedidos.externos` | `topico.pedidos.estado` |
| JNDI | `java:/jms/queue/PedidosExternos` | `java:/jms/topic/EstadosPedido` |
| Declaración | `@JMSDestinationDefinitions` en `PublicadorPedidosExternos.java:72-79` | `@JMSDestinationDefinition` en `PublicadorEstadosPedido.java:49-53` |
| Connection factory | `java:/jms/rabbit/ConnectionFactory`, `connectors=in-vm` (`PublicadorPedidosExternos.java:62-71`) | la misma |
| Productor | `PublicadorPedidosExternos.publicarPedidoExternoRegistrado`, observa `PedidoExternoRegistrado` **AFTER_SUCCESS**, `@TransactionAttribute(NOT_SUPPORTED)` (`:113-147`) | `PublicadorEstadosPedido.publicarCambioDeEstado`, observa `EstadoPedidoCambiado` **AFTER_SUCCESS**, `NOT_SUPPORTED` (`:65-90`) |
| Mensaje | `TextMessage` JSON `{"idPedidoExterno": n}` + propiedad `origen` (STOCK_CONSIGNADO o PUNTO_PICKING) | `TextMessage` JSON `{idPedido, idComercio, estado, fechaCambio}` + propiedades `estado` (String) e `idPedido` (long) |
| Si el envío falla | se loguea; lo levanta el polling (`SincronizadorDePedidos`) | se loguea; el aviso se pierde (el estado ya está guardado) |

### 10.2 Consumidores

| Consumidor | Destino | Selector | Durable | clientId | subscriptionName | shareSubscriptions | Qué hace | Fuente |
|---|---|---|---|---|---|---|---|---|
| `PedidoExternoListener` | cola | — | n/a | — | — | — | `sincronizarPedidoExterno` (REQUIRES_NEW) | `pedidos/negocio/PedidoExternoListener.java:65-110` |
| `SuscriptorPagosEstadoPedido` | tópico | `estado IN ('ENTREGADO', 'DEVUELTO_AL_COMERCIO')` | Durable | `rabbit-pagos` | `pagos-estados-pedido` | true | ENTREGADO → `registrarCobroContraEntrega`; DEVUELTO → `anularCobroPorDevolucion` (lee solo propiedades) | `pagos/negocio/SuscriptorPagosEstadoPedido.java:40-77` |
| `SuscriptorNotificacionesEstadoPedido` | tópico | — | Durable | `rabbit-notificaciones` | `notificaciones-estados-pedido` | true | `avisarCambioDeEstado` | `notificaciones/negocio/SuscriptorNotificacionesEstadoPedido.java:32-66` |
| `SuscriptorDestinatarioEstadoPedido` | tópico | — | Durable | `rabbit-destinatarios` | `destinatarios-estados-pedido` | true | `IAvisosAlDestinatario.avisar` (mail al cliente) | `notificaciones/negocio/SuscriptorDestinatarioEstadoPedido.java:35-69` |

### 10.3 Reintentos y escalado

- **`PedidoExternoListener`:**
  - `PedidoYaSincronizadoException` → se consume y se ignora.
  - `ConflictoDeStockException` → se relanza: rollback y el broker reintenta.
  - `ValidacionException` → `descartarPedidoExterno` y se consume.
  - Otra `RuntimeException` → se relanza (redelivery).
  - Mensaje ilegible → se consume y se loguea `SEVERE` (`:80-134`).
- **Suscriptores del tópico:** el mensaje ilegible se consume; una `RuntimeException` se propaga y el broker reintenta.
- **Duplicados y desorden:**
  - Pagos es idempotente: no hace nada si el cobro no está PENDIENTE o ya está ANULADO.
  - Notificaciones descarta por `fechaCambio`.
- **Redelivery:** no hay configuración propia en el repo. Rigen los valores por defecto del `address-setting` de WildFly/Artemis: **NO ENCONTRADO** `max-delivery-attempts` ni `redelivery-delay` en el proyecto.
- **Consumidores de la cola configurables:** `maxSession = ${rabbit.cola.consumidores:15}` en `src/main/webapp/WEB-INF/jboss-ejb3.xml` (se lee al desplegar).

### 10.4 Escalabilidad medida

Fuente: `docs/consigna/DESAFIOS-OPCIONALES.md`, sección 2; script `scripts/prueba_escalabilidad.py`.

**Condiciones:** 29/09/2026, 100 pedidos por corrida (+10 de calentamiento), WildFly 41 local de 10 núcleos, Supabase sa-east-1 con Session Pooler, pool de 10 conexiones.

| Consumidores | Tiempo total | Pedidos/s | Espera media | p95 | Mejora |
|---|---|---|---|---|---|
| 1 | 43,2 s | 2,3 | 22,2 s | 41,1 s | base |
| 4 | 11,3 s | 8,9 | 6,1 s | 10,8 s | x3,8 |
| 8 | 6,8 s | 14,6 | 3,8 s | 6,4 s | x6,3 |

---

## 11. Resiliencia y transacciones

### 11.1 Circuit breaker

`integracion/banco/CircuitBreakerBanco.java`:

- **Estados y transiciones** (`:61`):
  - CERRADO → ABIERTO con `UMBRAL_FALLAS` fallas seguidas (por defecto 3, `rabbit.banco.cb.umbral`).
  - ABIERTO → SEMIABIERTO pasados `ESPERA_MS` (por defecto 30.000, `rabbit.banco.cb.espera.ms`).
  - SEMIABIERTO → CERRADO con un éxito; → ABIERTO con una falla.
- **Qué cuenta como falla:** solo `WebServiceException`/`MalformedURLException` (sin respuesta). Un `PagoRechazado` es éxito (`BancoClient.java:79-87`).
- **Tickets por generación:** descartan resultados de llamadas viejas (`:74-80`, `:111-133`).
- **Concurrencia:** `@Lock(WRITE)` de clase y `@Lock(READ)` en `getEstado`.
- **Propiedades:** `static final`; se leen al cargar la clase (requiere redeploy o reinicio).

### 11.2 Timeouts

| Dónde | Valor | Fuente |
|---|---|---|
| Banco SOAP | 5.000 ms connect/receive | `BancoClient.java:54,113-114` |
| Transportista SOAP | 5.000 ms | `AdaptadorSoapTransportista.java:34,108-109` |
| Transportista REST | 5 s connect/read | `AdaptadorRestTransportista.java:40-46` |
| SMTP | 5.000 ms connection/timeout/write | `ConfiguracionSmtp.java:24,66-68` |
| Nominatim | 5 s connect, 8 s request | `Geocodificador.java:72,136` |
| Conversación stateful | 30 min | `InventarioService.java:72` |
| Reserva | 5 min | `InventarioService.java:80` |
| Datasource | socketTimeout 30 s, connectTimeout 10 s, idle 5 min, validación cada 30 s | README (configuración de WildFly, no código) |
| `Service.create` del banco | sin timeout propio (único punto que puede demorar más) | `BancoClient.java:56-57` |

### 11.3 Timers

Hay 6 `@Schedule` y 1 timer programático:

| Bean | Expresión | Qué hace | Fuente |
|---|---|---|---|
| `BarredorDeReservas.liberarReservasVencidas` | `hour="*", minute="*", second="0"` (cada minuto, :00) | Expira reservas vencidas (REQUIRES_NEW por reserva) | `inventario/negocio/BarredorDeReservas.java:102` |
| `Geocodificador.cadaMinuto` | `second="15"` (cada minuto, :15) | Ubica hasta 20 direcciones en Nominatim | `ruteo/negocio/Geocodificador.java:76` |
| `SincronizadorDePedidos.sincronizarPendientes` | `second="30"` (cada minuto, :30) | Polling de respaldo de la cola (`rabbit.sincronizador.pausado` lo pausa) | `pedidos/negocio/SincronizadorDePedidos.java:99` |
| `AsignadorDeEspera.cadaMinuto` | `second="45"` (cada minuto, :45) | Asigna la lista de espera en orden de `enEsperaDesde` (solo antes del corte) | `pedidos/negocio/AsignadorDeEspera.java:70` |
| `SeguimientoDeEnvios.consultarEnviosActivos` | `second="*/15"` (cada 15 s) | Polling a transportistas | `transportistas/negocio/SeguimientoDeEnvios.java:57` |
| `LiquidacionMensual.liquidar` | `hour="6", minute="0", second="0"` (**diario** a las 06:00) | Liquida el mes anterior (idempotente) y reintenta liquidaciones PENDIENTE | `pagos/negocio/LiquidacionMensual.java:36` |
| `AvisosDeEspera.alVencer` | `createSingleActionTimer(500 ms)` al recibir ENTREGADO, CANCELADO, PENDIENTE o DEVUELTO_AL_COMERCIO | Dispara `AsignadorDeEspera.procesar()` cuando se libera lugar | `pedidos/negocio/AvisosDeEspera.java:49-61` |

Todos los `@Schedule` son `persistent = false`.

### 11.4 Atributos transaccionales relevantes

| Método | Atributo | Fuente |
|---|---|---|
| `PedidoService.registrarPedidoExterno`, `confirmarPedido(EnZona)`, `devolverADeposito`, `derivarATransportista`, `despacharPedido`, `registrarEntrega`, `registrarEntregaDeTransportista`, `cancelarPedido`, `cancelarPedidoExterno` | REQUIRED | `PedidoService.java` |
| `sincronizarPedidoExterno`, `descartarPedidoExterno`, `asignarDesdeEspera`, `sacarDeEspera` | REQUIRES_NEW | `:347,469,581,596` |
| `cotizarDerivacion` | NOT_SUPPORTED | `:797` |
| `listarIntentosDeEntrega`, `obtenerFotoDeVisita`, `obtenerRemito` | SUPPORTS | `:725,744,900` |
| `RepartidorService.asignarRepartidor`, `liberarRepartidor` | REQUIRED (se suman a la de Pedidos) | `:86-87,176-177` |
| `RepartidorService.motivoParaEsperar`, `antesDelCorte` | SUPPORTS | `:120-136` |
| `PagoService.registrarCobro`, `anularCobro`, `anularCobroPorDevolucion`, `registrarCobroContraEntrega` | REQUIRED | `:81-165` |
| `TransportistaService.cotizarEnvio` | NOT_SUPPORTED; `solicitarEnvio` REQUIRED; `registrarNovedad` REQUIRES_NEW | `:150-281` |
| `CobroDeLiquidaciones.crear`, `cobrar` | REQUIRES_NEW (cada comercio por separado) | `:46,68` |
| `LiquidacionService` | NOT_SUPPORTED de clase | `:46` |
| Publicadores JMS, `ReversasBancarias`, `CancelacionesDeEnvios`, `CircuitBreakerBanco`, timers | NOT_SUPPORTED | ver sección 10 |
| Recursos REST | NOT_SUPPORTED de clase | `PedidosExternosResource.java:93`, `SeguimientoResource.java:48` |
| `BarredorDeReservas.expirarReserva` | REQUIRES_NEW | `:135` |

**`@ApplicationException(rollback = true)`:**

- Las `ValidacionException` de los 8 componentes (comercios, inventario, pagos, pedidos, repartidores, ruteo, seguridad, transportistas).
- `PedidoYaSincronizadoException`.
- `SinLugarException`, que la declara explícitamente.
- Las subclases heredan la anotación: `ConflictoDeStockException`, `CancelacionNoPermitidaException`, `ClaveIdempotenciaReutilizadaException`, `CuentaErpSinComercioException`, `PedidoNoEncontradoException`, `SinRepartidorConLugarException`, `StockModificadoException`, `NovedadRechazadaException`.
- `PagoRechazadoException` y `EnvioRechazadoException` son checked (faults SOAP).

### 11.5 Transacciones compensatorias

| Observador | Evento y fase | Qué llama | Fuente |
|---|---|---|---|
| `ReversasBancarias.compensarPagoDeshecho` | `PagoAutorizado`, **AFTER_FAILURE** | `IBancoClient.reversar(codigo)` | `pagos/negocio/ReversasBancarias.java:47-52` |
| `ReversasBancarias.devolverCobroAnulado` | `CobroAnulado`, AFTER_SUCCESS | `reversar` | `:54-58` |
| `CancelacionesDeEnvios.compensarEnvioDeshecho` | `EnvioSolicitado`, **AFTER_FAILURE** | `adaptador.cancelarEnvio` | `transportistas/negocio/CancelacionesDeEnvios.java:37-42` |
| `CancelacionesDeEnvios.avisarCancelacion` | `EnvioCancelado`, AFTER_SUCCESS | `cancelarEnvio` | `:44-48` |

Si la reversa o la cancelación no se confirma, queda en log `SEVERE` para hacerla a mano.

### 11.6 Bloqueos

| Bloqueo | Método | Fuente |
|---|---|---|
| `PESSIMISTIC_WRITE` sobre `Pedido` | `PedidoRepository.buscarPedidoParaActualizar` | `pedidos/datos/PedidoRepository.java:85` |
| `PESSIMISTIC_WRITE` sobre `PedidoExterno` | `buscarPedidoExternoParaActualizar` (cola vs. polling vs. cancelación del ERP) | `:412` |
| `PESSIMISTIC_WRITE` sobre `Repartidor` (uno o todos los candidatos, ordenados zona primero y por id) | `buscarParaActualizar`, `bloquearCandidatos` | `repartidores/datos/RepartidorRepository.java:32,42-49` |
| `PESSIMISTIC_WRITE` sobre `ReservaStock` | `buscarReservaParaActualizar` | `inventario/datos/InventarioRepository.java:280` |
| Optimista `@Version` en `ItemInventario` | `StockModificadoException` → `ConflictoDeStockException` (reintentable) | `ItemInventario`, `PedidoService.java:410-413` |
| `PESSIMISTIC_WRITE` sobre `Envio` | `buscarEnvioParaActualizar` | `transportistas/datos/TransportistaRepository.java:71` |
| `PESSIMISTIC_WRITE` sobre `Cobro` y `Liquidacion` | `buscarPorPedidoParaActualizar`, `buscarParaActualizar` | `pagos/datos/CobroRepository.java:41`, `LiquidacionRepository.java:32` |

**Orden de bloqueo pedido → envío** (ADR-025):

- `registrarNovedad` dispara primero el evento a Pedidos, que bloquea el pedido, y recién después bloquea el envío (`TransportistaService.java:290-347`).
- Es el mismo orden que la cancelación.

### 11.7 Tabla de fallas

| Situación | Qué hace el código | Qué ve el usuario | Fuente |
|---|---|---|---|
| Banco no responde | timeout 5 s → `NO_DISPONIBLE` → `ValidacionException`, rollback | "El banco no respondió: el pedido N no se confirmó…" y el pedido sigue PENDIENTE | `PagoService.java:168-179` |
| 3 fallas seguidas | circuito ABIERTO 30 s, respuesta inmediata | mismo mensaje sin esperar 5 s | `CircuitBreakerBanco.java:123-133` |
| Banco rechaza | fault → `RECHAZADO` | "Pago rechazado por el banco: <motivo>" | `PagoService.java:173-174` |
| Cobró y después no hay repartidor | `SinLugarException` → rollback → `AFTER_FAILURE` reversa | "…Probá de nuevo en un momento." (el pedido **no** entra en la lista de espera) | `PedidoService.java:536-539`, `ReversasBancarias.java:48` |
| Transportista no responde al derivar | `NO_DISPONIBLE` → `ValidacionException` | "<X> no respondió: el pedido no se derivó…" | `TransportistaService.java:204-206` |
| Derivó y después falla | `AFTER_FAILURE` → cancela el envío | error de la operación | `CancelacionesDeEnvios.java:38` |
| Transportista no responde en el seguimiento | `DESCONOCIDO` → reintenta en la próxima pasada | estado con hasta 15 s de atraso | `SeguimientoDeEnvios.java:71-89` |
| Transportista cancela el envío por su cuenta | solo se loguea | hay que resolverlo a mano | `ActualizacionDePedidosPorEnvio.java:51-57` |
| Broker caído al publicar en la cola | log + polling cada minuto | el pedido aparece hasta 1 min después | `PublicadorPedidosExternos.java:140-146` |
| Broker caído al publicar en el tópico | log, el estado queda guardado | el aviso o el cobro contra entrega no se procesa | `PublicadorEstadosPedido.java:85-89` |
| Sin stock al sincronizar | `ValidacionException` → descartado | el ERP ve "Descartado" con el motivo | `PedidoExternoListener.java:99-103` |
| Conflicto de stock | `ConflictoDeStockException` → redelivery o reintento en la próxima pasada | demora | `PedidoExternoListener.java:93-98` |
| SMTP caído | log, el aviso queda en la base | el pedido avanza; el aviso está en el portal | `AvisosPorMail.java:74-77` |
| Nominatim caído | corta la pasada | la parada se ordena por CP | `Geocodificador.java:117-120` |
| Pasó la hora de corte | lista de espera sin cobrar | "Pasó la hora de corte (07:00): se asigna mañana" | `RepartidorService.java:145-151` |
| Login fallido 5 veces | bloqueo de 15 min | mismo mensaje exista o no la cuenta | `LimiteDeIntentos.java` |

---

## 12. Patrones de diseño (clases exactas)

| Patrón | Dónde |
|---|---|
| DAO / Repository | `ComercioRepository`, `InventarioRepository`, `PedidoRepository`, `CobroRepository`, `LiquidacionRepository`, `RepartidorRepository`, `ZonaRepository`, `CoordenadaRepository`, `NotificacionRepository`, `UsuarioRepository`, `TransportistaRepository` |
| DTO | paquetes `*/dto` (por ejemplo `PedidoDTO.desde`, `DatosPedidoExternoDTO`); el REST usa `PedidoExternoRequest` |
| Facade | `PedidoService` (orquesta Comercios, Inventario, Repartidores, Pagos, Transportistas); `RuteoService` (despacho) |
| Adapter | `IAdaptadorTransportista` → `AdaptadorRestTransportista`, `AdaptadorSoapTransportista`; `IBancoClient` → `BancoClient` |
| Selección de adaptador en tiempo de ejecución (Strategy/Factory simple) | `AdaptadoresTransportista.para(TipoIntegracion)` (`transportistas/negocio/AdaptadoresTransportista.java:25-27`) |
| Strategy (documentado como tal en `PATRONES.md`) | Implementado con `if`, sin clases Strategy: origen del pedido (`PedidoService.sincronizarPedidoExterno:379-433`), medio de pago (`PagoService.registrarCobro:100-110`), cobertura de zona (`RuteoService.despachar:403-409`) |
| Singleton (EJB) | `CircuitBreakerBanco`, timers (`BarredorDeReservas`, `SincronizadorDePedidos`, `AsignadorDeEspera`, `Geocodificador`, `SeguimientoDeEnvios`, `LiquidacionMensual`), `AlineadorDeRestriccionesEnum`, `LimpiezaDeCredenciales` |
| Provider / fábrica de instancias | `Instance<IReservaStock>` en `PedidoService.java:155,404,1035` |
| Observer | Eventos CDI (sección 7.2) y tópico JMS |
| Máquina de estados (State simplificado) | `EstadoPedido.puedePasarA` + `PedidoService.cambiarEstado` |
| Transacción compensatoria | `ReversasBancarias`, `CancelacionesDeEnvios` |
| Circuit Breaker | `CircuitBreakerBanco` |
| Exception Mapper / Problem Details | `ProblemaMapper`, `Problema` |
| Idempotency Key | `HuellaDePedido` + `uk_pedido_externo_idempotencia` |
| Competing Consumers | `PedidoExternoListener` con `maxSession` |
| Polling Consumer (respaldo) | `SincronizadorDePedidos`, `SeguimientoDeEnvios` |
| HATEOAS | `_links` en `PedidosExternosResource.representacion` |

---

## 13. ADRs

Fuente: `docs/arquitectura/DECISIONES.md` (1.448 líneas). Los 49 existen y no hay citas a ADR inexistentes (rango citado en código y docs: 001-049).

| ADR | Título | Decisión (una línea) | Estado | Contra el código |
|---|---|---|---|---|
| 001 | Cola JMS + polling de respaldo | La cola es el camino principal; `SincronizadorDePedidos` es respaldo; `PESSIMISTIC_WRITE` + `sincronizado` | Aceptada | ok |
| 002 | El envío del mensaje no comparte transacción | AFTER_SUCCESS + NOT_SUPPORTED en el publicador | Aceptada | ok |
| 003 | Timeout del padrón fiscal | — | Reemplazada (sin SOAP de padrón) | ok |
| 004 | Importe y medio de pago vienen del ERP | Se copian al `Pedido` | Aceptada | ok |
| 005 | Máquina de estados en el enum | `puedePasarA` + un único `cambiarEstado` | Aceptada | ok |
| 006 | Tópico para cambios de estado | `topico.pedidos.estado`, orden por `fechaCambio` | Aceptada | ok |
| 007 | Endpoint REST reemplaza al formulario del ERP | `POST /api/v1/pedidos-externos` | Aceptada | ok |
| 008 | CHECK de enums alineados al desplegar | `AlineadorDeRestriccionesEnum` | Aceptada | ok |
| 009 | Suscripciones durables y selector | "Las dos suscripciones… selector `estado = 'ENTREGADO'`" | Aceptada | **Desactualizado:** hay 3 suscripciones y el selector es `IN ('ENTREGADO','DEVUELTO_AL_COMERCIO')` |
| 010 | Cobro SOAP con reversa compensatoria | `PagoAutorizado` + AFTER_FAILURE | Aceptada | ok |
| 011 | Circuit breaker propio | 3 fallas, 30 s | Aceptada | ok |
| 012 | Sin alta pública de usuarios | Solo ADMINISTRADOR crea cuentas | Aceptada | ok |
| 013 | Vistas por tipo de usuario | COMERCIO, REPARTIDOR, `IContextoUsuario` | Aceptada | ok |
| 014 | Consumidores competidores | `rabbit.cola.consumidores` (15) | Aceptada | ok |
| 015 | Banco en Node.js | Mismo WSDL; desde ADR-030, en la rama aparte | Aceptada | ok |
| 016 | Adapter por tecnología + polling | `IAdaptadorTransportista` | Aceptada | ok |
| 017 | Zonas por CP y despacho por cobertura | `zonas`, PROPIA o TRANSPORTISTA | Aceptada | ok |
| 018 | API versionada, idempotente, atada al comercio | `/v1`, `Idempotency-Key`, 404 ajeno | Aceptada | ok |
| 019 | HTTPS obligatorio salvo simulados | `CONFIDENTIAL` en web.xml | Aceptada | ok |
| 020 | Cotización antes de derivar | `cotizarEnvio`; el legado no cotiza | Aceptada | ok |
| 021 | Seguimiento público y tarjetas en el celular | `seguimiento.xhtml` | Aceptada | ok |
| 022 | Webhook de novedades | Se sacó | Reemplazada en parte por 029 | ok (no hay webhook) |
| 023 | Contraseñas solo en el realm; límite de intentos | 5 intentos, 15 min | Aceptada (parcialmente reemplazada por 031) | ok |
| 024 | Sin catálogo de productos | — | Aceptada | ok |
| 025 | Orden de bloqueo pedido → envío | — | Aceptada | ok |
| 026 | Cierre de reserva con el mismo bloqueo | `escribirStock` + PESSIMISTIC_WRITE | Aceptada | ok |
| 027 | Consultas de stock en un @Stateless | `ConsultaStockService` | Aceptada | ok |
| 028 | Eliminar comercio con solo cancelados | Veto por eventos | Aceptada | ok |
| 029 | Seguimiento solo por polling | Sin webhook | Aceptada | ok |
| 030 | Heterogeneidad en rama aparte | `desafio-heterogeneidad` | Aceptada | ok, pero la rama quedó 29 commits atrás de `main` (sección 15) |
| 031 | Cuentas en la base (realm JDBC, bcrypt) | `hash_clave`, `RabbitRealmBase` | Aceptada | ok |
| 032 | Peso y capacidad | `CapacidadRepartidor` | Aceptada | ok |
| 033 | Entrega con foto del remito | REPARTIDOR o ADMIN | Aceptada | ok |
| 034 | Liquidación mensual | Timer diario 06:00; tarifa 15000/1200/80 | Aceptada | ok |
| 035 | Ubicación en vivo y mapa | Cada 30 s, solo con entregas en curso | Aceptada | ok |
| 036 | Admin elimina usuarios, repartidores y zonas | Baja física con veto | Aceptada | ok |
| 037 | Lista de espera, hora de corte, devolución | — | Aceptada (en parte reemplazada por 041) | ok |
| 038 | Hoja de ruta recomendada | Vecino más cercano | Aceptada | ok (ahora por distancia con ADR-046) |
| 039 | Datos de quien recibe | "tres campos **opcionales**" | **Propuesta**, "en la rama feature" | **Desactualizado:** el email es obligatorio (`Destinatarios.emailObligatorio`, `PedidoExternoRequest.java:74`) y está mergeado en `main` |
| 040 | Avisos a quien recibe (3.er suscriptor) | `SuscriptorDestinatarioEstadoPedido` | Propuesta | Implementado en `main`: el estado está desactualizado |
| 041 | Entrega fallida con motivos e intentos | — | Propuesta | Implementado en `main` |
| 042 | Fecha prometida | — | Propuesta | Implementado en `main` |
| 043 | Horario estimado (parada N de M) | — | Propuesta | Implementado en `main` |
| 044 | Tablero de indicadores | — | Propuesta | Implementado en `main` |
| 045 | Evidencia de visita fallida | — | Propuesta | Implementado en `main` |
| 046 | Hoja de ruta con OpenStreetMap | — | Propuesta | Implementado en `main` |
| 047 | SMTP con login y TLS (Gmail) | — | Propuesta | Implementado en `main` |
| 048 | Lo que salió a reparto no se cancela | — | Aceptada | ok |
| 049 | Editar y eliminar stock | — | Aceptada | ok |

---

## 14. Historias de usuario y pruebas

- **Fuente de las HU y los CU:** `Rabbit-Documentacion-Funcional.pdf` (22 páginas, 18/10/2026), **fuera del repo**. En el repo: **NO ENCONTRADO** ningún `HU-xx` ni `CU-xx` (se buscó en `docs/`, `README.md` y `src/`).
- **Pruebas:** `docs/pruebas/FLUJO-DE-PRUEBAS.md`, secciones 1 a 14.

| HU | Título | Estado | Evidencia (código · prueba) |
|---|---|---|---|
| HU-01 | Enviar un pedido | Sí | `PedidosExternosResource.registrar`, `PedidoService.registrarPedidoExterno` · FLUJO §4. **Diferencia:** la HU dice "un código postal que no es argentino… da 422"; el código da **400** si no pasa `@Size(max=8)` y **422** si el formato no se resuelve |
| HU-02 | Saber cómo terminó | Sí | `consultar`, `aDTOConPedido` · §4.1 |
| HU-03 | Cancelar (ERP) | Sí | `cancelarPedidoExterno`, `ReglaDeCancelacion` · §4.1, §12 |
| HU-04 | Seguir sin registrarse | Sí | `SeguimientoResource`, `seguimiento.xhtml`, `ILlegadaEstimada` · §12 |
| HU-05 | Mail por novedad | Sí, requiere SMTP configurado | `SuscriptorDestinatarioEstadoPedido`, `TextosAlDestinatario` · §12 |
| HU-06 | Comercio: pedidos y avisos | Sí | `PortalComercioBean`, `mis-pedidos.xhtml` · §3 |
| HU-07 | Remito y visitas | Sí | `RemitoServlet`, `FotoVisitaServlet`, `comercio/visitas.xhtml`, `puedeVerRemito` · §13 |
| HU-08 | Mi stock | Sí | `listarStockDelComercioActual`, `mi-stock.xhtml` · §3 |
| HU-09 | Puntos de picking | Sí | `PuntoPickingBean`, `IRegistroComercios` · §3 |
| HU-10 | Mis liquidaciones | Sí | `MisLiquidacionesBean`, `previsualizarMesEnCursoDelComercioActual` · §10 |
| HU-11 | Mis indicadores | Sí | `IndicadoresService.calcularDelComercioActual`, `indicadores.js` (CSV) · §12 |
| HU-12 | Orden del recorrido | Sí | `IRuteo.recorridoRecomendado`, `OrdenDeRecorrido`, `EnlaceMapa` · §11, §14 |
| HU-13 | Retiré el pedido | Sí | `despacharPedido` (REPARTIDOR) · §5 |
| HU-14 | Entrega con remito | Sí | `registrarEntrega`, `Remitos` · §10 |
| HU-15 | Entrega fallida | Sí | `devolverADeposito`, `EvidenciaDeVisita`, `IntentosDeEntrega` · §11, §13 |
| HU-16 | Comercios | Sí | `ComercioService` · §2.1 |
| HU-17 | Stock | Sí | `ConsultaStockService.registrarItem`, `actualizarItem`, `eliminarItem` (ADR-049) · §2.2 |
| HU-18 | Reservar stock | Sí | `InventarioService`, `BarredorDeReservas` · §2.2 |
| HU-19 | Confirmar pedido | Sí | `confirmarPedido`. **Diferencia:** "Se le asigna el primer repartidor **de la zona**": el código prioriza la zona pero puede asignar a uno de otra zona (`bloquearCandidatos` ordena zona primero) · §5 |
| HU-20 | Cancelar (personal) | Sí | `cancelarPedido`, `anularCobro` (ADMIN) · §5 |
| HU-21 | Derivar | Sí | `cotizarDerivacion`, `derivarATransportista`, `SeguimientoDeEnvios` · §6b |
| HU-22 | Despachar por zona | Sí | `RuteoService.despacharZona`; si el de respaldo rechaza, prueba el más barato de los demás (`derivar`, `:432-452`) · §6c |
| HU-23 | Flota | Sí | `RepartidorService` · §2.3, §10 |
| HU-24 | Entregas del día y mapa | Sí | `EntregasBean`, `MapaBean` · §10 |
| HU-25 | Indicadores de la operación | Sí | `IndicadoresService.calcular` · §12 |
| HU-26 | Simular pedido del ERP | Sí | `PedidoBean.registrarPedidoExterno` · §4 |
| HU-27 | Cuentas | Sí | `UsuarioService`, `LimiteDeIntentos` · §1, §7b |
| HU-28 | Liquidar el mes | Sí | `LiquidacionService.liquidarMes`, `LiquidacionMensual` · §10 |

| CU | Estado | Observaciones |
|---|---|---|
| CU-01 Del pedido del ERP a la entrega | Sí | Coincide con el código |
| CU-02 Confirmar prepago | Parcial en el texto | Camino 4a: "el cobro se reversa y el pedido va a la lista de espera". En el código se reversa, pero el pedido **queda PENDIENTE fuera de la lista de espera** y la pantalla dice "Probá de nuevo en un momento" (`PedidoService.java:538-539`) |
| CU-03 Entrega fallida | Sí | Coincide |

**Comportamiento implementado sin HU propia:**

- Lista de espera automática: está dentro de HU-19.
- Geocodificación: está en la sección 2.6 de la documentación funcional.
- Despacho individual de un pedido por zona (`RuteoBean.despacharPedido`).
- Historial del repartidor (`IRuteo.historialDelRepartidor`, "5.6 Historial" del manual).
- Ubicación en vivo: está en HU-13 y HU-24.
- Temas visuales y responsive (anexo de la consigna).
- Eliminación de zonas por el administrador (ADR-036; la HU-22 no lo menciona).

---

## 15. Rama `desafio-heterogeneidad`

- **Contenido:** un solo commit propio sobre `main`, `9411d4f` (14/10/2026): "main más el banco en Node.js y el transportista en Python".
- **Base:** `ccaf503`.
- **Atraso:** **29 commits detrás de `main`**. No tiene nada desde el 14/10: login con bcrypt, capacidad, remito, liquidación, lista de espera, mejoras de última milla, ADR-048/049.

| Servicio | Lenguaje | Puerto | Contrato | Cómo se levanta |
|---|---|---|---|---|
| `banco-legado/` | Node.js ≥ 18, librería `soap` ^1.13.1 | 8090 (`PUERTO`) | Mismo WSDL que el banco Java (`banco-legado/banco.wsdl`), límite $500.000 | `npm install && npm start`; Rabbit: `/system-property=rabbit.banco.wsdl:add(value=http://localhost:8090/BancoLegadoService?wsdl)` + redeploy |
| `transportista-moderno/` | Python 3, solo biblioteca estándar (`http.server`) | 8095 (`PUERTO`), `PASO_SEGUNDOS` 20 | Mismo contrato REST que el simulador (`/cotizaciones`, `/envios`, `/envios/{codigo}`) | `python3 transportista-moderno/servidor.py`; en Rabbit se registra como transportista REST con endpoint `http://localhost:8095` |

**Cambios en Rabbit:** ninguno funcional. El único archivo Java tocado es un comentario en `integracion/banco/BancoClient.java:46-49`; el resto es documentación y la colección de Postman.

---

## 16. Cómo levantar el sistema

Fuente: `README.md`, "Cómo levantar el sistema" y "Credenciales y cuentas necesarias".

### Requisitos

- Java 17+ y Maven 3.9+.
- WildFly 41 con `standalone-full.xml`.
- PostgreSQL (Supabase, Session Pooler en el puerto **5432**; el 6543 rompe el despliegue).
- Driver JDBC de PostgreSQL.

### Configuración de WildFly

1. Módulo `org.postgresql` y datasource `RabbitDS` (`java:jboss/datasources/RabbitDS`).
2. Pool: `max-pool-size=10`, validación de fondo cada 30 s, `idle-timeout-minutes=5`, `socketTimeout=30`, `connectTimeout=10`, `tcpKeepAlive=true`.
3. Usuario de management con `add-user.sh` (sin `-a`) y `~/.m2/settings.xml` con `<server id="rabbit-wildfly">`.
4. Después del primer despliegue: `jboss-cli --file=scripts/configurar-realm-compartido.cli` (realm JDBC). Para volver atrás: `revertir-realm-compartido.cli`.
5. Primer administrador (base vacía): `add-user.sh -a -u … -g ADMINISTRADOR`.

### System properties

| Propiedad | Por defecto | Cuándo se lee |
|---|---|---|
| `rabbit.ruteo.hora-corte` | 07:00 | en cada uso |
| `rabbit.entregas.max-intentos` | 2 | en cada uso |
| `rabbit.ruteo.minutos-por-parada` | 20 | en cada uso |
| `rabbit.tarifa.abono` / `por-envio` / `por-kilo` | 15000 / 1200 / 80 | en cada uso |
| `rabbit.mail.smtp.host`, `.port`, `.usuario`, `.password`, `.seguridad`, `rabbit.mail.remitente` | sin correo | en cada envío |
| `rabbit.url-publica` | `https://localhost:8443/Rabbit` | en cada uso |
| `rabbit.contacto.mail` | `altas_rabbit@gmail.com` | — |
| `rabbit.geocodificacion`, `.url` | activa, Nominatim público | en cada pasada |
| `rabbit.cola.consumidores` | 15 | al desplegar |
| `rabbit.banco.wsdl` | banco simulado | al desplegar |
| `rabbit.banco.cb.umbral`, `.espera.ms` | 3 / 30000 | al cargar la clase |
| `rabbit.banco.simular.caida` | false | demo |
| `rabbit.sincronizador.pausado` | false | prueba de escalabilidad |
| `rabbit.transportista.simulador.segundos` | 20 | demo |

### Build y deploy

- `mvn clean package wildfly:deploy`.
- Tests: `mvn test` (151) y `mvn verify -Pintegracion -Drabbit.erp.usuario=… -Drabbit.erp.clave=… -Drabbit.punto=…` (17).
- URL: `https://localhost:8443/Rabbit`.

### Usuarios de prueba

- Viven en la tabla `usuarios` de la base compartida (sin contraseñas en el repo).
- El flujo para crearlos está en `docs/pruebas/FLUJO-DE-PRUEBAS.md` §2.4-2.5.
- Las credenciales de la base y de WildFly no están en el repositorio (README, "Credenciales").

### Limpieza de datos

`scripts/Rabbit-limpieza-datos-de-prueba.sql`, con opciones A/B/C. Se corre en el editor SQL de Supabase.

---

## 17. Requisitos de la cátedra

**Enunciado:**

- En el repo: **NO ENCONTRADO** (solo hay un resumen propio, `docs/consigna/CHECKLIST.md`).
- Fuera del repo: `TP_Integrador_DesarrolloAplicacionesII.docx` (Consigna 2.º C 2026) y `Anexo_TP_Integrador_DesarrolloAplicacionesII.docx`.

### Sección 6 de la consigna (obligatorios)

| Requisito | Dónde se cumple en el código | Sección del dossier |
|---|---|---|
| ≥ 6 componentes con interfaz explícita | 9 componentes de negocio, 26 interfaces `@Local` | 1, 2 |
| ≥ 1 stateful y 1 stateless justificados | `InventarioService` `@Stateful` con `@PostConstruct`, `@PreDestroy`, `@PrePassivate`, `@PostActivate` y `@Remove` (`InventarioService.java:104-155`); resto `@Stateless` | 2.2 |
| Capas en cada componente | `presentacion/negocio/datos/dto` | 1 |
| ≥ 3 patrones | DAO, DTO, Facade, Adapter, Observer, Singleton, Provider, Circuit Breaker, compensatoria… | 12 |
| SOAP con WSDL, sistema legado | `BancoLegadoService` (+ transportista legado) | 9 |
| REST, partner moderno o API de consumo | API del ERP + seguimiento (expuestas); transportista moderno (consumida) | 8 |
| 2 procesos asíncronos: cola y tópico | `cola.pedidos.externos`, `topico.pedidos.estado` | 10 |
| Seguridad declarativa en ≥ 2 operaciones | `@RolesAllowed` (por ejemplo `anularCobro`, `eliminarComercio`, `registrarUsuario`, `registrarEntrega`) + web.xml | 6 |
| Transacciones declarativas en un flujo crítico | `confirmar`: cobro → asignación → CONFIRMADO, una transacción REQUIRED con compensación | 4, 11 |
| Stack consistente | Jakarta EE 10 / WildFly 41 | 1 |
| Git incremental | 133 commits desde el 01/09, 4+ autores | 0 |

### Entrega Parcial N.º 2 (19/10)

| Pedido | Dónde |
|---|---|
| Diagrama de arquitectura de integración SOA | `docs/entregas/diagramas/arquitectura-integracion-soa.svg`; diagramas 03 y 04 de este dossier |
| Clasificación sync/async de cada integración externa | 7.1, `docs/entregas/ENTREGA-PARCIAL-2.md` |
| Un proceso asíncrono de punta a punta con broker real | 10 (cola + tópico, Artemis) |

### Anexo, sección 6 (19/10)

| Pedido | Estado |
|---|---|
| Documentación funcional (HU/CU) | `Rabbit-Documentacion-Funcional.pdf` (fuera del repo) |
| Manual de usuario | `Rabbit-Manual-de-Usuario.pdf` (fuera del repo, a propósito) |
| Prototipo Figma/InVision | **NO ENCONTRADO** en el repo |

### Opcionales

- Temas claro/oscuro/normal en tiempo real: `tema.js`, `estilos.css`.
- Responsive: commits `12f1c81`, `78ff676`.
- Resiliencia: sección 11.
- Escalabilidad: 10.4.
- Heterogeneidad: sección 15.
- ≥ 2 ADR: hay 49.
- Tablero Jira/Trello: mencionado en los ADR (Trello); **NO ENCONTRADO** el enlace en el repo.

---

## 18. Uso de IA

| Evidencia | Detalle |
|---|---|
| Declaración | `docs/consigna/USO-DE-IA.md`: Claude Code (Anthropic) para implementación asistida, revisiones, tests, documentación. Marcado como **"Borrador: completar con lo que usó cada integrante"** |
| Commits con co-autoría | 97 de 147 commits (todas las ramas) con `Co-Authored-By`: Claude Opus 5.5 (85), Claude Sonnet 5 (5), Claude Opus 5 (5), Claude Sonnet 5.5 (1), Claude Sonnet 4.6 (1) |
| `CLAUDE.md` / `.claude/` en el repo | **NO ENCONTRADO** |
| Comentarios en código | No hay marcas de "generado por IA" (**NO ENCONTRADO**) |

---

## 19. Limitaciones y trabajo futuro

**TODO / FIXME en el código:** **NO ENCONTRADO**. Las coincidencias de "TODO" son la palabra en castellano, por ejemplo "TODO el stock" en `ConsultaStockService.java:193`.

**Funcionalidades simuladas:**

- Banco legado (`BancoLegadoServiceImpl`, movimientos en memoria; se pierden al redesplegar).
- Transportista legado SOAP y moderno REST (`SimuladorDeEnvios`, avance por tiempo).
- ERP: no existe uno real; se simula desde Pedidos, "Simular un pedido del ERP", o con Postman/curl.

**Limitaciones documentadas** (`docs/PROXIMOS-PASOS.md` §1, `docs/consigna/DESAFIOS-OPCIONALES.md` "Limitaciones", `docs/integraciones/MENSAJERIA-SINCRONICA.md`):

- Cobro prepago huérfano si se pierde la respuesta del banco tras el timeout. Falta una clave de idempotencia en `autorizarPago`.
- La reversa que falla queda solo logueada.
- El circuit breaker y el broker son por servidor: no hay cluster probado.
- La latencia hasta Supabase domina los tiempos.
- Al cambiar `rabbit.banco.wsdl`, las reversas van al banco configurado en ese momento.
- `ValidacionException` está duplicada en 8 paquetes, y las validaciones y los try/catch de los beans se repiten.
- Faltan tests unitarios de comercios, inventario, ruteo (zonas), transportistas, pagos (circuit breaker, reversa) y servicios de notificaciones.
- Log `SEVERE` cosmético de Mojarra en `personal/pedidos.xhtml`.
- PR #6 pendiente de decisión (traer la heterogeneidad a `main`).
- Una cancelación del envío iniciada por el transportista no se procesa: se resuelve a mano (`ActualizacionDePedidosPorEnvio.java:51-57`).

**Decisiones descartadas** (`docs/arquitectura/DECISIONES.md`):

| Descartado | Dónde |
|---|---|
| XA / outbox para cola + base | ADR-002 (línea 29) |
| Patrón State completo | ADR-005 |
| Flyway/Liquibase en lugar de `hbm2ddl=update` | ADR-008 (línea 109) |
| MicroProfile Fault Tolerance (no viene en `standalone-full`) | ADR-011 (línea 160) |
| Webhook de transportistas | ADR-022 → ADR-029 |
| MicroProfile OpenAPI para generar el contrato | ADR-018 (línea 380) |
| ESB / bus de integración | Documentación funcional, sección 7 (fuera del repo) |
| ObjectMessage y MapMessage | Documentación funcional, sección 10 |
| SOAP del padrón fiscal | ADR-003 |
| Catálogo de productos en Rabbit | ADR-024 |

---

## 20. Contradicciones a resolver

> **Actualización (18/10/2026, después del relevamiento):** se corrigieron en los `.md`
> del repo las contradicciones **de documentación**:
> - 3: ADR-009, `MensajeriaFinal.md`, `MENSAJERIA-ASINCRONICA.md` y `FLUJO-DE-PRUEBAS.md`;
> - 4: README, CHECKLIST y `ARQUITECTURA.md`;
> - 7: `ARQUITECTURA.md`;
> - 9 y 10: estados de ADR-039 a 047;
> - 11: diagrama de dependencias de `ARQUITECTURA.md`;
> - 15: `USO-DE-IA.md`;
> - 16: ADR-030 y `DESAFIOS-OPCIONALES.md`;
> - 18: `PATRONES.md`.
>
> Las referencias a la rama `feature/mejoras-lideres-ultima-milla` pasaron a `main`, y la rama se eliminó.
>
> Siguen abiertas, porque están en la documentación funcional (fuera del repo) o en el código:
> - en la documentación funcional: 6 (texto de la liquidación), 11 (dependencias), 12, 13 y 19;
> - en el código: 14 (comentario en `PedidoService.java:1280`).

### Las ocho pedidas

1. **Modelo de integración**
   - **Código:** híbrido. Hacia afuera, punto a punto, cada sistema externo detrás de un adaptador, un solo componente por sistema y ningún bus. Adentro, llamadas `@Local` punto a punto más el tópico como **hub** de los cambios de estado (1 productor, 3 consumidores desacoplados, `PublicadorEstadosPedido.java:49-90`).
   - **Docs:** la documentación funcional (sección 7) lo dice bien.
2. **Integración de datos y de archivos**
   - **Datos: sí.** El login de WildFly lee `usuarios` con un jdbc-realm (`scripts/configurar-realm-compartido.cli`).
   - **Archivos: débil.**
     - El CSV de indicadores se arma **en el navegador** (`indicadores.js:272-294`). Es una exportación para el usuario, no un intercambio entre sistemas.
     - El único intercambio por archivos entre sistemas es `ApplicationRealmSync`, que lee y borra líneas de `application-users/roles.properties` de WildFly (`ApplicationRealmSync.java:54-147`).
   - **Recomendación:** presentar el CSV como "exportación de archivos" y no como integración principal, o citar también el realm de archivos.
3. **Suscriptores del tópico: 3.**
   - Son `SuscriptorPagosEstadoPedido`, `SuscriptorNotificacionesEstadoPedido` y `SuscriptorDestinatarioEstadoPedido`.
   - El selector de Pagos es `estado IN ('ENTREGADO', 'DEVUELTO_AL_COMERCIO')` (`SuscriptorPagosEstadoPedido.java:43`). **Sí incluye DEVUELTO_AL_COMERCIO.**
   - **Docs desactualizados:**
     - ADR-009 ("dos suscripciones", selector `= 'ENTREGADO'`, `DECISIONES.md:118-120`);
     - `MensajeriaFinal.md:162` (selector viejo);
     - `MENSAJERIA-ASINCRONICA.md:117` (diagrama con `= 'ENTREGADO'`; el texto de la línea 93 está bien);
     - `ENTREGA-PARCIAL-2.md:175` y su SVG ("Notificaciones (2 suscriptores)": en Notificaciones son 2, más Pagos da 3 en total; aclarar).
4. **Componentes: 9 de negocio.**
   - `integracion` (banco + adaptadores + simuladores) es una capa técnica con una interfaz `@Local` (`IBancoClient`).
   - **Docs inconsistentes:**
     - README:179 y CHECKLIST ("10 componentes");
     - el mismo CHECKLIST dice "la integración con el banco… es un adaptador técnico, no un componente de negocio";
     - `ARQUITECTURA.md` lista "Integración banco legado" como fila de componente;
     - la documentación funcional dice "nueve".
   - **Recomendación:** "9 componentes de negocio + 1 capa de integración".
5. **Estado de tránsito**
   - **Del pedido:** `EN_CAMINO` ("En camino").
   - **Del envío del transportista:** `EN_TRANSITO` ("En tránsito", `EstadoEnvio`). El legado lo informa como `EN_VIAJE`.
   - **Docs:**
     - `FLUJO-DE-PRUEBAS.md:352,361` y `PRUEBAS-Y-COMANDOS.md:310` hablan de "En tránsito" al seguir envíos: hay que verificar que se refiera al envío y no al pedido;
     - la documentación funcional (sección 9) dice "EN_VIAJE → En tránsito": correcto para el envío.
6. **Timers: 6 `@Schedule` + 1 timer programático** (tabla 11.3).
   - Lista de espera: cada minuto (:45), más el disparo a los 500 ms al liberarse lugar.
   - Geocodificación: cada minuto (:15).
   - Liquidación: **diaria** a las 06:00, no mensual. Liquida el mes anterior de forma idempotente.
   - Además: sincronizador (:30), barredor (:00) y seguimiento de envíos (cada 15 s).
   - **Docs:** la documentación funcional (sección 2.6) dice que la liquidación "reintenta los cobros que el banco **rechazó**". El código reintenta las **PENDIENTE** (sin respuesta del banco); las RECHAZADA se reintentan a mano (`LiquidacionService.java:136-148`, `CobroDeLiquidaciones.java:86-93`).
7. **Interfaces y operaciones entre documentos**

   | Elemento | Código | `ARQUITECTURA.md` | Doc funcional (sección 6) | `ENTREGA-PARCIAL-2.md` |
   |---|---|---|---|---|
   | `ILiquidaciones` | sí | sí (completa) | sí (`liquidarMes`, `previsualizar`) | no |
   | `IAvisosAlDestinatario` | sí | sí | sí (`avisar`) | no |
   | `ILlegadaEstimada` | sí | sí | sí (`estimar`) | no |
   | `devolverADeposito` | sí | sí | sí | no |
   | `diaDeEntregaPara` | sí | sí | sí | no |
   | `registrarUbicacion` | sí | sí | sí | no |
   | `recorridoRecomendado` | sí | sí | sí | no |
   | `IIndicadores` | sí | sí | **no** | no |
   | `IZonas`, `IConsultaUsuarios`, `IConsultaCobros`, `IGestionTransportistas`, `ISeguimientoEnvios` | sí | sí | **no** (solo "principales") | no |
   | `IAsignacionRepartidores.ultimaUbicacion` | sí | **no** | no | no |
   | `IGestionPedidos.listarIntentosDeEntrega`, `obtenerFotoDeVisita` | sí | **no** | no | no |
   | `IConsultaStock.listarItemsPorComercio` | sí | **no** (resumido como `PorComercio`) | no | no |

8. **Gmail y OpenStreetMap en `main`**
   - **OpenStreetMap: sí**, integrado en código:
     - Nominatim en `Geocodificador` (timer) y `Nominatim`;
     - tiles en `mapa.js` con Leaflet, en las páginas `personal/mapa.xhtml` y `seguimiento.xhtml`.
   - **Gmail: no hay código específico.** Hay un cliente SMTP genérico (`ConfiguracionSmtp`, `AvisosPorMail`) que funciona con Gmail **por configuración** (`rabbit.mail.smtp.host=smtp.gmail.com` + contraseña de aplicación). Sin propiedades no se manda nada.
   - **Docs:** decir "Gmail" como sistema externo es correcto solo si se aclara que es la configuración elegida, no una integración propia. La documentación funcional (sección 7) lo lista como externo "Gmail · SMTP".

### Otras contradicciones encontradas

9. **ADR-039 a ADR-047 figuran como "Propuesta… en la rama `feature/mejoras-lideres-ultima-milla`"**, pero están mergeados en `main` (PR #8, `7faa0c9`) e implementados. Hay que pasarlos a Aceptada.
10. **ADR-039 dice que nombre, teléfono y email son "opcionales"**: el email es obligatorio (`Destinatarios.java:63-72`, `PedidoExternoRequest.java:74`).
11. **Dependencias de componentes en la documentación funcional (sección 6):**
    - "Repartidores — Depende de: Ninguno", pero depende de Seguridad (`RepartidorService.java:68`).
    - A "Pagos" le faltan Pedidos (`LiquidacionService` → `ISeguimientoPedido`) y Comercios.
    - "Pedidos → Ruteo" existe solo en presentación (`SeguimientoBean`, `SeguimientoResource` → `ILlegadaEstimada`), no en `PedidoService`.
    - `ARQUITECTURA.md` (diagrama de dependencias) no muestra Repartidores→Seguridad, Pagos→Pedidos, Pagos→Comercios, Pedidos→`IConsultaStock`, ni los eventos `EliminacionDeItem`, `EliminacionDeRepartidor`, `RepartidorEliminado` y `ZonaEliminada`.
12. **CU-02, camino 4a:** dice que el pedido "va a la lista de espera"; el código lo deja PENDIENTE fuera de la espera y pide reintentar (ver sección 14).
13. **HU-01:** "un código postal que no es argentino… da 422": si el CP tiene más de 8 caracteres da **400** (Bean Validation); si no se puede interpretar, **422**.
14. **Comentario obsoleto en el código:** `PedidoService.java:1280` dice "Todavía no hay observer", pero hay tres (`PublicadorEstadosPedido`, `AvisosDeEspera` y, vía tópico, los suscriptores).
15. **`docs/consigna/USO-DE-IA.md` sigue como "Borrador"** y no menciona las mejoras del 16 al 18/10 (indicadores, visitas fallidas, OSM, SMTP real).
16. **La rama `desafio-heterogeneidad` quedó 29 commits atrás de `main`.** La documentación (ADR-030) dice que es "main más un commit": ya no es cierto para el `main` actual.
17. **Tests:** el commit `aadb303` dice "148 + 17"; el README, el CHECKLIST y el último `mvn test` dicen 151 unitarios. **Vale 151 + 17.**
18. **`Strategy` en `PATRONES.md`** está descrito como patrón aplicado, pero son `if` sin clases intercambiables. El único punto con selección polimórfica en tiempo de ejecución es `AdaptadoresTransportista.para(TipoIntegracion)`.
19. **HU-19 "primer repartidor de la zona":** el código prioriza la zona pero cae en cualquier repartidor con lugar (`RepartidorRepository.bloquearCandidatos`, orden `CASE WHEN idZona = :zona`).
