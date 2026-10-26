# Documentación de Rabbit

Rabbit es una plataforma de logística de última milla: conecta comercios con una red de depósitos y repartidores.
Es el trabajo práctico integrador de Desarrollo de Aplicaciones II (UADE, 2.º cuatrimestre 2026). Está hecho con
Jakarta EE sobre WildFly: EJB, JPA, JMS (ActiveMQ Artemis), API REST (JAX-RS), SOAP (JAX-WS) y vistas JSF, con la base
en PostgreSQL.

Esta carpeta explica **cómo está construido y por qué**. Qué hace el sistema, cómo levantarlo y el resumen funcional
están en el [README principal](../README.md).

## Cómo leer esta documentación

- **Un documento por tema.** Cada uno describe lo **implementado**, con las clases donde vive.
- **Las mejoras pendientes** están marcadas como tales dentro de cada documento y en las "Consecuencias" de los ADR.
- **Cada decisión importante** tiene su ADR (*Architecture Decision Record*): el problema, lo que se decidió y lo que
  se descartó. Están todas en [DECISIONES.md](arquitectura/DECISIONES.md), con un índice al principio.

### Por dónde empezar

1. [README principal](../README.md): qué hace el sistema y cómo se levanta.
2. [ARQUITECTURA.md](arquitectura/ARQUITECTURA.md): las capas, los componentes y cómo se hablan entre sí.
3. [PATRONES.md](arquitectura/PATRONES.md): los patrones de diseño que se aplican y dónde (empieza con un catálogo).
4. [MENSAJERIA-JUSTIFICACION.md](integraciones/MENSAJERIA-JUSTIFICACION.md): con qué tecnología se integra cada
   sistema y por qué.
5. [DECISIONES.md](arquitectura/DECISIONES.md): el detalle de cada decisión, cuando haga falta saber el porqué.

## Cómo está organizada la carpeta

```
docs/
├── arquitectura/    ← cómo está construido Rabbit y por qué
├── integraciones/   ← mensajería con otros sistemas y contrato de la API
├── seguridad/       ← autenticación, roles y protección de la API
├── pruebas/         ← recorrido de prueba, comandos y colección de Postman
└── consigna/        ← cumplimiento de la consigna, desafíos y uso de IA
```

## ¿Qué documento me sirve?

- Entender cómo está armado Rabbit → leé [ARQUITECTURA.md](arquitectura/ARQUITECTURA.md).
- Saber por qué se decidió algo y qué alternativas se descartaron → leé [DECISIONES.md](arquitectura/DECISIONES.md) (con índice de ADR).
- Ver qué patrones de diseño se aplican y dónde → leé [PATRONES.md](arquitectura/PATRONES.md) (con catálogo).
- Entender las transacciones y qué pasa si algo falla a la mitad → leé [TRANSACCIONES.md](arquitectura/TRANSACCIONES.md).
- Saber con qué tecnología se integra cada sistema y por qué → leé [MENSAJERIA-JUSTIFICACION.md](integraciones/MENSAJERIA-JUSTIFICACION.md).
- Consultar el contrato de la API REST → leé [openapi.yaml](integraciones/openapi.yaml).
- Ver los roles, permisos y protecciones → leé [SEGURIDAD.md](seguridad/SEGURIDAD.md).
- Levantar el sistema y probarlo → leé [PRUEBAS-Y-COMANDOS.md](pruebas/PRUEBAS-Y-COMANDOS.md) y [FLUJO-DE-PRUEBAS.md](pruebas/FLUJO-DE-PRUEBAS.md).
- Ubicar cómo se cumple cada requisito de la consigna → leé [CHECKLIST.md](consigna/CHECKLIST.md).

## Índice

### [arquitectura/](arquitectura/)

- [ARQUITECTURA.md](arquitectura/ARQUITECTURA.md): Capas, componentes, interfaces entre componentes, stateless vs. stateful, máquina de estados del pedido
- [PATRONES.md](arquitectura/PATRONES.md): Catálogo de patrones y, para cada uno, qué problema resuelve, dónde está y qué alternativa se descartó
- [TRANSACCIONES.md](arquitectura/TRANSACCIONES.md): Atributos transaccionales usados y qué pasa ante una falla a mitad de cada flujo
- [DECISIONES.md](arquitectura/DECISIONES.md): Registro de decisiones de arquitectura (59 ADR), con índice

### [integraciones/](integraciones/)

- [MENSAJERIA-JUSTIFICACION.md](integraciones/MENSAJERIA-JUSTIFICACION.md): **Por qué** cada integración usa cola, tópico, SOAP o REST: el criterio de decisión aplicado a todas
- [MENSAJERIA-SINCRONICA.md](integraciones/MENSAJERIA-SINCRONICA.md): SOAP con el banco legado (cobro, reversa, timeout, circuit breaker), transportistas SOAP y REST (cotización, derivación y seguimiento por polling) y API REST (ERP y seguimiento)
- [MENSAJERIA-ASINCRONICA.md](integraciones/MENSAJERIA-ASINCRONICA.md): Cola JMS de pedidos externos, tópico de estados del pedido y avisos por mail
- [openapi.yaml](integraciones/openapi.yaml): Contrato OpenAPI 3.1 de la API REST (se abre en Swagger Editor; para Postman está la colección de `pruebas/`)

### [seguridad/](seguridad/)

- [SEGURIDAD.md](seguridad/SEGURIDAD.md): Autenticación, roles, `@RolesAllowed`, operaciones sensibles, límite de intentos de login y HTTPS

### [pruebas/](pruebas/)

- [FLUJO-DE-PRUEBAS.md](pruebas/FLUJO-DE-PRUEBAS.md): Recorrido manual de punta a punta sobre la app desplegada, para verificar y para la demo. Los tests automatizados están en `src/test` (ver el README principal)
- [PRUEBAS-Y-COMANDOS.md](pruebas/PRUEBAS-Y-COMANDOS.md): Comandos para prender y apagar cada sistema, perillas de las demos y la lista completa de pruebas para marcar
- [Rabbit.postman_collection.json](pruebas/Rabbit.postman_collection.json): Colección de Postman: API del ERP, validaciones, seguimiento y llamados directos a los transportistas y bancos, más una carpeta automatizada de contrato y seguridad (64 requests)

### [consigna/](consigna/)

- [CHECKLIST.md](consigna/CHECKLIST.md): Dónde se cumple cada requisito obligatorio de la consigna (Sección 6)
- [DESAFIOS-OPCIONALES.md](consigna/DESAFIOS-OPCIONALES.md): Desafíos opcionales: resiliencia, escalabilidad y ADRs desarrollados con sus alternativas (la heterogeneidad tecnológica está en la rama `desafio-heterogeneidad`)
- [USO-DE-IA.md](consigna/USO-DE-IA.md): Declaración de uso de IA generativa (obligatoria por consigna)

## Convenciones

- **Nombres:** los documentos van en mayúsculas con guiones (`MENSAJERIA-ASINCRONICA.md`). Los archivos que no son
  texto (`openapi.yaml`, la colección de Postman) conservan su nombre habitual.
- **Enlaces:** siempre relativos, para que funcionen igual en GitHub y en el editor.
- **ADR:** una entrada por decisión en [DECISIONES.md](arquitectura/DECISIONES.md), con el título
  `## ADR-NNN: Título` y un estado (Aceptada, Propuesta o Reemplazada). La numeración no se reutiliza.
- **Diagramas:** van en el propio texto, en Mermaid, para que se vean directamente en GitHub.
- **Idioma:** español, con el nombre de las clases y de los métodos tal cual aparecen en el código.

## Regla de mantenimiento

Todo cambio que agregue o modifique un componente, una integración, una regla de seguridad o un flujo transaccional
actualiza el documento que corresponde **en el mismo commit**. Una decisión que tenga alternativas razonables se
registra como ADR en [DECISIONES.md](arquitectura/DECISIONES.md).
