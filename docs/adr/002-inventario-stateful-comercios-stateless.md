# ADR 002 — ServicioDeInventario stateful, ServicioDeComercios stateless

- **Estado:** Aceptada
- **Fecha:** 2026-09-05
- **Componentes afectados:** ServicioDeInventario (`com.rabbit.inventario`), ServicioDeComercios (`com.rabbit.comercios`)

## Contexto

El checklist de la consigna (Sección 6) exige "al menos 1 componente
stateful y 1 stateless, ambos justificados por escrito", y la Entrega
Obligatoria N.º 1 agrega que debe haber "evidencia de que el contenedor
gestiona su ciclo de vida (uso de anotaciones o callbacks de inicialización /
destrucción)".

Hasta este cambio el sistema tenía dos componentes implementados y **los dos
eran `@Stateless`**: ServicioDeComercios y la primera versión de
ServicioDeInventario, que solo cubría el ABM de depósitos y stock. El
requisito de componente stateful no se cumplía.

El documento de la Entrega Parcial N.º 1 ya había anticipado dónde iba a
aparecer el estado conversacional: *"ServicioDeInventario sí es stateful
porque debe mantener una reserva de stock vigente, con su vencimiento, a lo
largo de varias interacciones"*. Faltaba implementarlo.

## Decisión

**ServicioDeComercios queda `@Stateless`. ServicioDeInventario pasa a
`@Stateful`**, incorporando la interfaz `IReservaStock`.

El criterio no es la anotación sino la forma de las operaciones:

| | ServicioDeComercios | ServicioDeInventario (IReservaStock) |
|---|---|---|
| Forma de la operación | Autocontenida | Conversación de varios pasos |
| Parámetros | Recibe todo lo que necesita | Las operaciones de cierre no reciben el ID de la reserva |
| Dónde vive el estado | En la base de datos | En memoria, en la instancia del bean |
| Instancias | Pool compartido | Una dedicada por cliente |

`registrarComercio(datos)` no depende de ninguna llamada anterior: recibe los
datos, valida, persiste y termina. Cualquier instancia del pool puede
atenderla.

`confirmarReserva()` **no recibe qué reserva confirmar**. Opera sobre la que
esta conversación dejó abierta en `reservarStock()`. Ese "recordar cuál
reserva está abierta" es el estado conversacional, y es lo que obliga al
contenedor a asignar una instancia dedicada por cliente.

### Ciclo de vida gestionado por el contenedor

`InventarioService` declara `@PostConstruct`, `@PrePassivate`,
`@PostActivate`, `@PreDestroy`, `@Remove` y `@StatefulTimeout(30 minutos)`.
Todos los invoca WildFly: la instancia nunca se crea con `new`.

Sumando el `@Singleton @Startup @Schedule` del barredor, el componente
demuestra las tres formas de ciclo de vida gestionado que ofrece EJB: pool
de instancias (`@Stateless`), instancia por cliente (`@Stateful`) e
instancia única con timer (`@Singleton`).

## Alternativas consideradas

### A. Separar en dos beans: `InventarioService` stateless + `ReservaStockService` stateful

La más limpia en teoría: el ABM de depósitos no necesita estado
conversacional y quedaría en un bean stateless, mientras la reserva vive en
otro stateful.

Descartada. El diagrama de clases del grupo modela **un solo**
`ServicioDeInventario` `«Stateful»` con el campo `reservaActual : ReservaStock`,
y el documento de la Parcial N.º 1 ya describe ese componente como la unidad
stateful del sistema. Partirlo obligaba a rehacer diagrama y documento a doce
días de la entrega, a cambio de una optimización que a esta escala no se
percibe.

Se asume el costo: `DepositoBean` e `ItemInventarioBean`, que solo hacen ABM,
también reciben instancias stateful. La defensa correcta es que **lo que
define al componente es que su contrato incluye mantener una conversación, no
que todas sus operaciones la usen**.

### B. Simular el hold con un campo en la base y mantener todo stateless

Guardar la reserva en `reservas_stock` y que cada operación reciba el ID de
la reserva por parámetro. Funcionalmente equivalente, y el sistema quedaría
enteramente stateless.

Descartada por dos motivos. Primero, incumple el requisito: no habría
componente stateful en todo el sistema. Segundo, y más de fondo, sería
deshonesto con el dominio: el hold **es** una conversación, y modelarlo
pasando el ID en cada llamada es esconder el estado conversacional en el
cliente en vez de reconocerlo en el contrato del componente.

### C. Liberar las reservas vencidas solo con `@PreDestroy` (sin barredor)

Cuando el contenedor descarta la instancia —cierre explícito o
`@StatefulTimeout`— se libera la reserva pendiente. Cero código extra.

Descartada porque deja un agujero real. Cubre "el cliente abandonó y el bean
murió", pero no el caso más frecuente: **la reserva venció a los 5 minutos y
la conversación sigue viva**, con el usuario mirando la pantalla sin decidir.
Ahí el `@StatefulTimeout` recién actúa a los 30 minutos, y durante 25 minutos
esa cantidad figura comprometida, bajándole el stock libre a todos los demás.

Se implementó `BarredorDeReservas` (`@Singleton @Startup` + `@Schedule` cada
minuto) que marca `EXPIRADA` lo vencido y devuelve la cantidad al stock
libre. El `@PreDestroy` se conservó igual: cubren fallas distintas.

## Consecuencias

**Positivas**

- Se cumple el requisito de componente stateful, con los dos casos
  contrastables lado a lado en el mismo sistema.
- Evidencia concreta de ciclo de vida gestionado, en tres formas distintas.
- Primer consumo real de las interfaces del ADR 001: `reservarStock` valida
  contra `IConsultaComercios` que el comercio esté activo, sin poder
  modificarlo.
- Flujo transaccional de varios pasos (reservar → confirmar) que sirve además
  para el requisito de transacciones declarativas.

**Negativas / costos**

- Los Managed Beans de ABM reciben instancias stateful que no necesitan.
- `ReservaBean` debe ser `@SessionScoped` en vez de `@ViewScoped` para que la
  conversación sobreviva a las recargas, lo que lo hace inconsistente con el
  resto de los Beans de la app.
- Un `@Stateful` consume memoria por cliente. Mitigado con `@StatefulTimeout`.

**Limitación conocida**

El barredor corre cada minuto: entre que una reserva vence y se libera puede
pasar hasta un minuto. Para el alcance del TP es irrelevante; en un sistema
real con alta contención convendría además un chequeo de vencimiento al leer
la disponibilidad.

## Verificación

Al desplegar, WildFly registra el componente por sus dos contratos y arranca
el barredor solo:

```
WFLYEJB0473: JNDI bindings for session bean named 'InventarioService':
    java:module/InventarioService!com.rabbit.inventario.negocio.IConsultaStock
    java:module/InventarioService!com.rabbit.inventario.negocio.IReservaStock
[Barredor] Activo — revisa reservas vencidas cada 1 minuto
```

Ciclo probado de punta a punta contra PostgreSQL:

| Paso | disponible | reservada | libre |
|---|---|---|---|
| Estado inicial | 12 | 0 | 12 |
| `reservarStock(5)` | 12 | 5 | 7 |
| segunda reserva | *rechazada: "Ya tenés una reserva vigente"* | | |
| `confirmarReserva()` | 7 | 0 | 7 |
| `reservarStock(4)` + `liberarReserva()` | 7 | 0 | 7 |

El stock queda comprometido pero no descontado mientras la reserva está
vigente, y sale del depósito recién al confirmar. El contador de vencimiento
baja entre requests distintos: evidencia de que el contenedor mantiene la
misma instancia para esa conversación.
