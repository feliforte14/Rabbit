# Documentación técnica de Rabbit

Un documento por tema. Cada uno distingue lo **implementado** (con las
clases donde vive) de lo **planificado** para la Entrega Obligatoria N.º 2
(09/11/2026), marcado siempre como `Planificado`.

| Documento | Qué justifica |
|---|---|
| [ARQUITECTURA.md](ARQUITECTURA.md) | Capas, componentes, interfaces entre componentes, stateless vs. stateful, máquina de estados del pedido |
| [PATRONES.md](PATRONES.md) | Cada patrón de diseño: qué problema resuelve, dónde está, qué alternativa se descartó |
| [SEGURIDAD.md](SEGURIDAD.md) | Autenticación, roles, `@RolesAllowed`, operaciones sensibles |
| [TRANSACCIONES.md](TRANSACCIONES.md) | Atributos transaccionales usados y qué pasa ante una falla a mitad de cada flujo |
| [MENSAJERIA-SINCRONICA.md](MENSAJERIA-SINCRONICA.md) | SOAP con el banco legado (cobro, reversa, timeout) y API REST (ERP y seguimiento) |
| [MENSAJERIA-ASINCRONICA.md](MENSAJERIA-ASINCRONICA.md) | Cola JMS de pedidos externos y tópico de estados del pedido |
| [DECISIONES.md](DECISIONES.md) | Registro de decisiones de arquitectura (ADRs) |
| [CHECKLIST.md](CHECKLIST.md) | Dónde se cumple cada requisito obligatorio de la consigna (Sección 6) |
| [USO-DE-IA.md](USO-DE-IA.md) | Declaración de uso de IA generativa (obligatoria por consigna) |

Cómo levantar el sistema y el resumen funcional siguen en el
[README principal](../README.md).

## Regla de mantenimiento

Todo cambio que agregue o modifique un componente, una integración, una
regla de seguridad o un flujo transaccional actualiza el documento que
corresponde **en el mismo commit**. Una decisión que tenga alternativas
razonables se registra como ADR en [DECISIONES.md](DECISIONES.md).
