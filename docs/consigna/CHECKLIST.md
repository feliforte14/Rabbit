# Checklist de la Sección 6 (requisitos transversales)

Dónde se cumple cada requisito obligatorio de la consigna, para ubicarlo
rápido en la defensa.

| Requisito | Estado | Dónde |
|---|---|---|
| Mínimo 6 componentes de negocio con interfaz explícita y documentada | Cumple (10 implementados) | Comercios, Inventario, Pedidos, Pagos y Cobranzas, Repartidores, Notificaciones, Seguridad, Ruteo, Transportistas e Integración con el banco legado. Interfaces en [ARQUITECTURA.md](../arquitectura/ARQUITECTURA.md) |
| Al menos 1 componente stateful y 1 stateless, justificados | Cumple | `InventarioService` (`@Stateful`, la reserva es una conversación) y el resto `@Stateless`, incluidas las consultas de stock (`ConsultaStockService`, ADR-027); `@PostConstruct` / `@PreDestroy` como evidencia del ciclo de vida. Ver [ARQUITECTURA.md](../arquitectura/ARQUITECTURA.md) |
| Arquitectura en capas en cada componente | Cumple | `presentacion/`, `negocio/`, `datos/`, `dto/` en cada componente de negocio. La integración con el banco (`integracion.banco`) es un adaptador técnico, no un componente de negocio |
| Al menos 3 patrones de diseño, aplicados y justificados | Cumple | DAO, DTO, Facade, Adapter, Singleton, Provider, Observer, máquina de estados, transacción compensatoria, Circuit Breaker. Ver [PATRONES.md](../arquitectura/PATRONES.md) |
| Integración síncrona SOAP con WSDL, con un sistema legado | Cumple | Banco legado: `BancoLegadoService` (`/Rabbit/BancoLegadoService?wsdl`). Ver [MENSAJERIA-SINCRONICA.md](../integraciones/MENSAJERIA-SINCRONICA.md). Además, un transportista legado por SOAP (`TransportistaLegadoService`) |
| Integración síncrona REST, partner moderno o API de consumo externo | Cumple | `/api/v1/pedidos-externos` (ERP: alta idempotente, consulta y cancelación) y `GET /api/v1/seguimiento/{codigo}` (público). Problem Details, HATEOAS y contrato OpenAPI. Ver [MENSAJERIA-SINCRONICA.md](../integraciones/MENSAJERIA-SINCRONICA.md) y [openapi.yaml](../integraciones/openapi.yaml) |
| 2 procesos asíncronos: cola punto a punto y tópico pub/sub | Cumple | `cola.pedidos.externos` y `topico.pedidos.estado` sobre ActiveMQ Artemis. Ver [MENSAJERIA-ASINCRONICA.md](../integraciones/MENSAJERIA-ASINCRONICA.md) |
| Seguridad declarativa en al menos 2 operaciones sensibles | Cumple | `@RolesAllowed` en `eliminarComercio` (y en eliminar usuarios, repartidores y zonas), alta de usuarios (`registrarUsuario`), `listarTodos` / `darDeBaja` usuarios, `anularCobro` y la API del ERP. HTTPS obligatorio (`transport-guarantee CONFIDENTIAL`). Ver [SEGURIDAD.md](../seguridad/SEGURIDAD.md) |
| Transacciones declarativas en 1 flujo crítico de varios pasos | Cumple | `confirmarPedido`: cobrar → asignar repartidor → confirmar, con reversa en el banco si falla. Ver [TRANSACCIONES.md](../arquitectura/TRANSACCIONES.md) |
| Stack consistente y justificado | Cumple | Jakarta EE 10 sobre WildFly 41 |
| Repositorio Git con historial incremental | Cumple | Commits desde agosto, de varios integrantes |
| Tests automatizados (no lo exige la consigna) | Hay | 43 tests unitarios (JUnit 5) en cada build y 10 tests de integración de la API con REST Assured (`mvn verify -Pintegracion`). Ver el README |

## Desafíos opcionales

| Desafío | Estado |
|---|---|
| Al menos 2 ADR | Cumple: 36 ADR en [DECISIONES.md](../arquitectura/DECISIONES.md) |
| Resiliencia ante fallas | Cumple ([DESAFIOS-OPCIONALES.md](DESAFIOS-OPCIONALES.md)): Circuit Breaker frente al banco legado (`CircuitBreakerBanco`, ADR-011) con demo por system property; timeout de 5 s; transacción compensatoria; si el broker falla, el polling recupera los pedidos externos. Ver [MENSAJERIA-SINCRONICA.md](../integraciones/MENSAJERIA-SINCRONICA.md) |
| Heterogeneidad tecnológica | Cumple, en la [rama `desafio-heterogeneidad`](https://github.com/feliforte14/Rabbit/tree/desafio-heterogeneidad) (ADR-030): el banco legado implementado también en Node.js, que Rabbit consume por el mismo WSDL, y el transportista moderno en Python, por REST. `main` queda con el sistema interno solo |
| Prueba de escalabilidad | Cumple: el consumidor de la cola escala con `rabbit.cola.consumidores`; con 8 consumidores, 100 pedidos se procesan 6,3 veces más rápido que con uno (medido). Ver [DESAFIOS-OPCIONALES.md](DESAFIOS-OPCIONALES.md) |

## Anexo de la consigna (interfaz)

| Requisito | Estado | Dónde |
|---|---|---|
| Diseño responsive (obligatorio para una web app) | Cumple | Escritorio, tablet y celular, probado en 10 tamaños de 360 a 1920 px **sin ningún scroll horizontal**. A menor pantalla, menos información: cada tabla marca sus columnas secundarias (`col-p3`, detalle, se oculta por debajo de 1366 px; `col-p2`, complementaria, por debajo de 1100 px) y en el celular las tablas se ven como tarjetas con solo lo esencial (`tablas.js`). Hasta 860 px el menú lateral pasa a una barra con botón "Menú" (`menu.js`); en tablet las tarjetas van de a dos. Objetivos táctiles de 44 px en el menú y en los botones de acción del celular (los del repartidor, a lo ancho). Revisado con las cuentas de comercio y repartidor el 14/10 |
| Temas claro, oscuro y normal (opcional, puntos extra) | Cumple | Se cambian en tiempo real, sin recargar, desde el menú lateral o el pie del login, el seguimiento y la página de error (`tema.js`). Cada tema solo redefine las variables de color de `estilos.css`; la elección queda guardada en el navegador y se aplica antes de pintar la página, sin parpadeo. En el tema claro el naranja de acento se oscurece (`#c2410c`) para llegar al contraste 4,5:1 de WCAG AA sobre blanco |
