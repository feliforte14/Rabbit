# Documentación técnica de Rabbit

Un documento por tema, con lo **implementado** y las clases donde vive.
Lo que queda como mejora está marcado como tal en cada documento y en las
"Consecuencias" de los ADR.

```
docs/
├── arquitectura/    ← cómo está construido Rabbit y por qué
├── integraciones/   ← mensajería con otros sistemas y contrato de la API
├── seguridad/       ← autenticación, roles y protección de la API
├── pruebas/         ← recorrido de prueba y demo
└── consigna/        ← cumplimiento de la consigna, desafíos y uso de IA
```

### [arquitectura/](arquitectura/)

| Documento | Qué justifica |
|---|---|
| [ARQUITECTURA.md](arquitectura/ARQUITECTURA.md) | Capas, componentes, interfaces entre componentes, stateless vs. stateful, máquina de estados del pedido |
| [PATRONES.md](arquitectura/PATRONES.md) | Cada patrón de diseño: qué problema resuelve, dónde está, qué alternativa se descartó |
| [TRANSACCIONES.md](arquitectura/TRANSACCIONES.md) | Atributos transaccionales usados y qué pasa ante una falla a mitad de cada flujo |
| [DECISIONES.md](arquitectura/DECISIONES.md) | Registro de decisiones de arquitectura (24 ADR) |

### [integraciones/](integraciones/)

| Documento | Qué justifica |
|---|---|
| [MensajeriaFinal.md](integraciones/MensajeriaFinal.md) | **Por qué** cada integración usa cola, tópico, SOAP o REST: el criterio de decisión aplicado a todas |
| [MENSAJERIA-SINCRONICA.md](integraciones/MENSAJERIA-SINCRONICA.md) | SOAP con el banco legado (cobro, reversa, timeout, circuit breaker), transportistas SOAP y REST (cotización, derivación, polling y webhook) y API REST (ERP y seguimiento) |
| [MENSAJERIA-ASINCRONICA.md](integraciones/MENSAJERIA-ASINCRONICA.md) | Cola JMS de pedidos externos, tópico de estados del pedido y avisos por mail |
| [openapi.yaml](integraciones/openapi.yaml) | Contrato OpenAPI 3.1 de la API REST (se abre en Swagger Editor o se importa en Postman) |

### [seguridad/](seguridad/)

| Documento | Qué justifica |
|---|---|
| [SEGURIDAD.md](seguridad/SEGURIDAD.md) | Autenticación, roles, `@RolesAllowed`, operaciones sensibles, límite de intentos de login, HTTPS y clave del webhook |

### [pruebas/](pruebas/)

| Documento | Qué justifica |
|---|---|
| [FLUJO-DE-PRUEBAS.md](pruebas/FLUJO-DE-PRUEBAS.md) | Recorrido manual de punta a punta sobre la app desplegada, para verificar y para la demo. Los tests automatizados están en `src/test` (ver el README principal) |

### [consigna/](consigna/)

| Documento | Qué justifica |
|---|---|
| [CHECKLIST.md](consigna/CHECKLIST.md) | Dónde se cumple cada requisito obligatorio de la consigna (Sección 6) |
| [DESAFIOS-OPCIONALES.md](consigna/DESAFIOS-OPCIONALES.md) | Desafíos opcionales: resiliencia, escalabilidad, ADRs desarrollados con sus alternativas y heterogeneidad tecnológica |
| [USO-DE-IA.md](consigna/USO-DE-IA.md) | Declaración de uso de IA generativa (obligatoria por consigna) |

Cómo levantar el sistema y el resumen funcional siguen en el
[README principal](../README.md).

## Regla de mantenimiento

Todo cambio que agregue o modifique un componente, una integración, una
regla de seguridad o un flujo transaccional actualiza el documento que
corresponde **en el mismo commit**. Una decisión que tenga alternativas
razonables se registra como ADR en [DECISIONES.md](arquitectura/DECISIONES.md).
