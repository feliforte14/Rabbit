# Checklist de la Sección 6 (requisitos transversales)

Dónde se cumple cada requisito obligatorio de la consigna, para ubicarlo
rápido en la defensa.

| Requisito | Estado | Dónde |
|---|---|---|
| Mínimo 6 componentes de negocio con interfaz explícita y documentada | Cumple (9 implementados) | Comercios, Inventario, Pedidos, Pagos y Cobranzas, Repartidores, Notificaciones, Seguridad, Ruteo (mínimo) e Integración con el banco legado. Interfaces en [ARQUITECTURA.md](ARQUITECTURA.md). Transportistas queda identificado en el diseño, sin implementar |
| Al menos 1 componente stateful y 1 stateless, justificados | Cumple | `InventarioService` (`@Stateful`, la reserva es una conversación) y el resto `@Stateless`; `@PostConstruct` / `@PreDestroy` como evidencia del ciclo de vida. Ver [ARQUITECTURA.md](ARQUITECTURA.md) |
| Arquitectura en capas en cada componente | Cumple | `presentacion/`, `negocio/`, `datos/`, `dto/` en cada paquete |
| Al menos 3 patrones de diseño, aplicados y justificados | Cumple | DAO, DTO, Facade, Adapter, Singleton, Provider, Observer, máquina de estados, transacción compensatoria, Circuit Breaker. Ver [PATRONES.md](PATRONES.md) |
| Integración síncrona SOAP con WSDL, con un sistema legado | Cumple | Banco legado: `BancoLegadoService` (`/Rabbit/BancoLegadoService?wsdl`). Ver [MENSAJERIA-SINCRONICA.md](MENSAJERIA-SINCRONICA.md) |
| Integración síncrona REST, partner moderno o API de consumo externo | Cumple | `POST /api/pedidos-externos` (ERP) y `GET /api/seguimiento/{id}` (público). Ver [MENSAJERIA-SINCRONICA.md](MENSAJERIA-SINCRONICA.md) |
| 2 procesos asíncronos: cola punto a punto y tópico pub/sub | Cumple | `cola.pedidos.externos` y `topico.pedidos.estado` sobre ActiveMQ Artemis. Ver [MENSAJERIA-ASINCRONICA.md](MENSAJERIA-ASINCRONICA.md) |
| Seguridad declarativa en al menos 2 operaciones sensibles | Cumple | `@RolesAllowed` en `eliminarComercio`, alta de usuarios (`registrarUsuario`), `listarTodos` / `darDeBaja` usuarios, `anularCobro` y la API del ERP. Ver [SEGURIDAD.md](SEGURIDAD.md) |
| Transacciones declarativas en 1 flujo crítico de varios pasos | Cumple | `confirmarPedido`: cobrar → asignar repartidor → confirmar, con reversa en el banco si falla. Ver [TRANSACCIONES.md](TRANSACCIONES.md) |
| Stack consistente y justificado | Cumple | Jakarta EE 10 sobre WildFly 41 |
| Repositorio Git con historial incremental | Cumple | Commits desde agosto, de varios integrantes |

## Desafíos opcionales

| Desafío | Estado |
|---|---|
| Al menos 2 ADR | Cumple: 14 ADR en [DECISIONES.md](DECISIONES.md) |
| Resiliencia ante fallas | Cumple ([DESAFIOS-OPCIONALES.md](DESAFIOS-OPCIONALES.md)): Circuit Breaker frente al banco legado (`CircuitBreakerBanco`, ADR-011) con demo por system property; timeout de 5 s; transacción compensatoria; si el broker falla, el polling recupera los pedidos externos. Ver [MENSAJERIA-SINCRONICA.md](MENSAJERIA-SINCRONICA.md) |
| Heterogeneidad tecnológica | No |
| Prueba de escalabilidad | Cumple: el consumidor de la cola escala con `rabbit.cola.consumidores`; con 8 consumidores, 100 pedidos se procesan 6,3 veces más rápido que con uno (medido). Ver [DESAFIOS-OPCIONALES.md](DESAFIOS-OPCIONALES.md) |
