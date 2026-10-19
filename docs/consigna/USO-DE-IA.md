# Declaración de uso de inteligencia artificial

La consigna (Sección 9) permite usar IA generativa como asistente y pide
declarar qué se usó y para qué en cada entrega obligatoria.

> **Borrador:** completar con lo que usó cada integrante.

| Herramienta | Para qué se usó |
|---|---|
| Claude Code (Anthropic) | Implementación asistida de componentes (Pagos, Repartidores, Notificaciones, tópico JMS, integración SOAP con el banco, API REST, circuit breaker, vistas por tipo de usuario y ruteo), revisión de seguridad y sus correcciones, optimización de consultas (N+1), robustez (pool de conexiones, integridad al eliminar comercios), desafíos opcionales (prueba de escalabilidad y su script, banco legado en Node.js), componente Transportistas (adaptadores REST y SOAP, simuladores y seguimiento), ruteo por zona (zonas por código postal y despacho automático), organización de las vistas por tipo de usuario, link al recorrido en el mapa, revisión de la API REST contra la Clase 10 y sus correcciones (versión `/v1`, idempotencia, Problem Details, Bean Validation, cancelación, HATEOAS, seguimiento por código, ERP atado a su comercio y contrato OpenAPI), cotización con transportistas, HTTPS obligatorio, código de seguimiento en el portal del comercio, página pública de seguimiento, mejoras visuales (tablas como tarjetas en el celular, encabezado compacto, resumen de pedidos), revisiones de código y sus correcciones, deuda técnica (webhook de transportistas, después retirado por ADR-029; transportista moderno en Python, avisos por mail, contraseñas solo en el realm y límite de intentos de login, plan B en el despacho por zona), tests automatizados (JUnit 5 y REST Assured), mejoras del tablero de Trello (peso y capacidad, remito, liquidación mensual, mapa en vivo, cuentas compartidas, eliminación por el administrador) y sus correcciones (lista de espera, hora de corte, devolución al depósito, hoja de ruta recomendada, propuesta de cobro por almacenamiento), mejoras frente a los líderes de última milla (datos y avisos por mail a quien recibe, visitas fallidas con evidencia y devolución al comercio, fecha prometida, franja horaria en el seguimiento, indicadores con gráficos y CSV, hoja de ruta con OpenStreetMap, mails reales por SMTP, hora de corte a las 07:00, email obligatorio, no cancelar lo que salió a reparto, editar y eliminar stock), revisión responsive, pruebas funcionales sobre la app desplegada, redacción de la documentación técnica, del manual de usuario, de la documentación funcional y del dossier de contexto verificado contra el código (`docs/entrega/`) con sus diagramas |

Todo el código generado con asistencia fue revisado por el equipo, y
cada integrante tiene que poder explicarlo y defenderlo en la instancia
oral.
