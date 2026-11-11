---
title: "BRD · Rabbit"
subtitle: "Documento de requisitos de negocio · Desarrollo de Aplicaciones II · Grupo 9 · Lunes TM"
date: "Versión 1.0 · 02/11/2026"
---

# BRD · Rabbit, logística de última milla

**Grupo 9 · Lunes TM · Desarrollo de Aplicaciones II (3.4.218) · UADE · 2.º cuatrimestre 2026**

| Dato | Valor |
|---|---|
| Documento | Business Requirements Document (BRD), el "por qué" de Rabbit |
| Estándar | ISO/IEC/IEEE 29148:2018 (BRS / StRS) · IIBA BABOK v3 |
| Versión | 1.0 · 02/11/2026 |
| Integrantes | Felipe Forte · Florencia Ayala · Facundo (a completar apellido) · *(completar si hay más)* |
| Fuente versionada | `docs/negocio/BRD.md` en el repositorio |
| Documentos relacionados | [Documentación funcional](../funcional/DOCUMENTACION-FUNCIONAL.md) (el "qué": HU, reglas, RNF, matriz) · [Arquitectura](../arquitectura/ARQUITECTURA.md) · [Decisiones (ADR)](../arquitectura/DECISIONES.md) |

El BRD dice **qué problema de negocio resuelve Rabbit y cómo se mide si lo resolvió**. No describe pantallas ni la solución técnica: eso está en la documentación funcional (SRS) y en la arquitectura. Cada requisito de negocio (BR-xx) se traza a historias de usuario en la matriz de la documentación funcional.

## 1. Resumen ejecutivo

Rabbit es una plataforma de logística de última milla. Conecta a comercios que venden en línea con una red de depósitos y repartidores propios, y con empresas de envío externas cuando la flota no alcanza.

El comercio manda cada venta desde su ERP por una API, sin cargarla a mano. Rabbit reserva el stock que el comercio dejó en consignación (o lo retira de su punto de picking), cobra el pedido, le asigna un repartidor al que le entre en el vehículo, le arma la hoja de ruta y registra la entrega con prueba: foto del remito, código de 4 dígitos que solo tiene quien compró y ubicación. Quien recibe sigue su pedido sin registrarse y recibe un mail en cada cambio. A fin de mes, Rabbit le liquida al comercio su servicio y lo cobra por el banco.

El negocio se mide con cinco objetivos (sección 3): pedidos sin carga manual, entregas a tiempo, entregas al primer intento, cobro sin intervención manual y cero incidentes de datos personales. Hay 11 requisitos de negocio priorizados con MoSCoW: 5 Must, 4 Should y 2 Could (sección 6).

## 2. Contexto y problema de negocio

**Situación actual de los comercios que venden en línea.** Un comercio chico o mediano que vende por su tienda o un marketplace resuelve la entrega de una de estas formas, todas con problemas:

| Hoy | Problema |
|---|---|
| Carga cada venta a mano en el sistema de una empresa de envíos | Doble carga, errores de dirección, demora entre la venta y el despacho |
| Despacha con su propio personal | No tiene ruteo, ni seguimiento, ni prueba de entrega; no escala en los picos |
| Usa varias empresas de envío | Cada una con su portal, sus estados y su facturación; no hay una sola vista |
| Guarda la mercadería en su local | Tiene que preparar cada pedido y esperar al repartidor; no puede prometer "llega hoy" |

Del lado de quien compra: no sabe cuándo llega el pedido, no puede avisar cómo entrar o cambiar el día, y una visita fallida termina en llamadas y reclamos al comercio.

**Oportunidad.** Un operador de última milla que se integre con el ERP del comercio, guarde su mercadería en depósitos propios, prometa el día de entrega, reparta con su flota y derive a terceros cuando hace falta, y que le dé al comercio y a quien recibe una sola vista del pedido. Las referencias del mercado son Mercado Libre Flex y Full, Andreani, Onfleet, ShipBob y DHL On Demand Delivery (citadas en las ADR-042 a 059).

## 3. Objetivos de negocio (SMART)

Los valores objetivo son la propuesta del equipo para el primer semestre de operación. La línea de base es 0 porque Rabbit no está en producción. Cada objetivo se puede medir hoy con datos que Rabbit ya registra (Indicadores, liquidaciones, logs).

| Id | Objetivo | Métrica y meta | Plazo | Cómo se mide |
|---|---|---|---|---|
| O-1 | Eliminar la carga manual de pedidos de los comercios integrados | 100 % de los pedidos de un comercio con ERP integrado entran por la API; 0 cargados a mano | Desde el primer mes de cada comercio | Pedidos por origen (API vs. "Simular pedido del ERP") |
| O-2 | Cumplir el día de entrega prometido | ≥ 90 % de los pedidos con fecha prometida entregados ese día o antes | Promedio móvil de 30 días, desde el 3.er mes | Indicador "a tiempo" (ADR-044) |
| O-3 | Reducir las visitas fallidas | ≥ 85 % de los pedidos entregados al primer intento | Promedio móvil de 30 días, desde el 3.er mes | Indicador "al primer intento" (ADR-044) |
| O-4 | Cobrar sin intervención manual | 100 % de los comercios activos liquidados el día 1 de cada mes; 0 pedidos prepagos confirmados sin cobro | Cada cierre mensual | Liquidaciones por mes; cobros por pedido |
| O-5 | Proteger los datos personales (Ley 25.326) | 0 incidentes de exposición de datos de quien recibe; 0 vulnerabilidades críticas o altas abiertas | Permanente; revisión en cada versión | Matriz SEC-01 a 10, ZAP, escaneo de dependencias |

## 4. Alcance

**Incluye**

- Recepción de pedidos desde el ERP del comercio por API REST, con alta idempotente, consulta y cancelación.
- Stock consignado en depósitos de Rabbit (con su peso y medidas) y retiro en el punto de picking del comercio.
- Anuncio y recepción de mercadería en los depósitos.
- Promesa del día de entrega según la hora de corte.
- Cobro del pedido: prepago en el banco al confirmar o contra entrega.
- Asignación de repartidores propios por zona y capacidad del vehículo, lista de espera, despacho por zona y hoja de ruta.
- Derivación a transportistas externos (REST y SOAP) con cotización y seguimiento automático.
- Entrega con prueba (foto del remito, código de 4 dígitos, ubicación), visitas fallidas con evidencia y devolución al comercio.
- Retiro en depósito por parte de quien compró (si no lo busca en 21 días, vuelve al comercio).
- Seguimiento público sin cuenta, avisos por mail y opciones de quien recibe (reprogramar, instrucciones, persona autorizada), y calificación de la entrega.
- Portal del comercio: pedidos, avisos, stock, puntos de picking, liquidaciones e indicadores.
- Liquidación mensual del servicio al comercio (abono, envíos, kilos y almacenamiento) cobrada por el banco.
- Administración de cuentas y roles.

**Excluye**

- El catálogo de productos y la tienda del comercio: viven en su ERP (ADR-024).
- La facturación fiscal (AFIP/ARCA) de lo que Rabbit cobra: Rabbit liquida y cobra, no emite comprobantes.
- La app nativa del repartidor: usa la web responsive desde el celular.
- La logística inversa iniciada por quien compra (devoluciones de producto).
- La gestión de lotes y vencimientos de la mercadería.
- Pagos con tarjeta en línea: el cobro prepago lo hace el banco legado.

## 5. Stakeholders y usuarios

| Stakeholder | Tipo | Qué necesita de Rabbit | Cómo participa |
|---|---|---|---|
| Comercio | Cliente (paga el servicio) | Que sus ventas se entreguen a tiempo, sin carga manual y con prueba; saber cuánto paga | Portal del comercio; ERP integrado |
| ERP del comercio | Sistema externo | Una API estable, idempotente y que solo acepte pedidos de su comercio | `/api/v1/pedidos-externos` |
| Quien recibe (cliente final) | Usuario sin cuenta | Saber cuándo llega, poder cambiar la entrega, no exponer sus datos | Seguimiento público y mails |
| Repartidor | Usuario interno | Saber qué retirar y entregar, en qué orden, y registrar la entrega desde el celular | Mis entregas, escaneo de etiquetas |
| Operador de Rabbit | Usuario interno | Operar toda la red: comercios, stock, pedidos, despacho, entregas | Pantallas del personal |
| Administrador de Rabbit | Usuario interno | Lo del operador más cuentas, eliminaciones y liquidaciones | Pantallas del personal |
| Banco legado | Sistema externo | Recibir los cobros y reversas por SOAP | `BancoLegadoService` |
| Transportistas | Sistemas externos | Recibir envíos derivados y reportar su estado | Adaptadores REST y SOAP |
| Dirección de Rabbit | Patrocinador | Crecer en comercios con margen y sin incidentes | Indicadores y liquidaciones |

## 6. Requisitos de negocio (BR-xx)

Prioridad MoSCoW: **Must** = sin esto Rabbit no opera; **Should** = importante, con alternativa temporal; **Could** = mejora el servicio, se puede postergar.

| Id | Prioridad | Requisito de negocio | Métrica de aceptación | Objetivo |
|---|---|---|---|---|
| BR-01 | Must | Los comercios mandan sus pedidos a Rabbit desde su ERP, sin carga manual, y saben al instante si fueron aceptados y qué día llegan. | 0 pedidos de comercios integrados cargados a mano. El ERP recibe el número de pedido y el día prometido en la misma respuesta. Un reintento no duplica el pedido. | O-1, O-2 |
| BR-02 | Must | Cada pedido se entrega con prueba verificable de que llegó a quien compró. | 100 % de las entregas a domicilio con foto del remito; las de la flota propia, además, con el código de 4 dígitos de quien recibe. | O-3 |
| BR-03 | Must | Rabbit cobra sin intervención manual: el pedido prepago al confirmarlo, el contra entrega al entregarlo y su propio servicio cada mes. | 0 pedidos prepagos confirmados sin cobro. 100 % de los comercios activos liquidados el día 1. Si algo falla después de cobrar, el cobro se reversa. | O-4 |
| BR-04 | Must | Cada actor accede solo a lo suyo y los datos de quien recibe no se exponen. | 0 accesos a pedidos o evidencias de otro comercio. Los 10 criterios SEC en CUMPLE. Ningún dato personal en logs ni en el seguimiento público. | O-5 |
| BR-05 | Must | Los pedidos se asignan a repartidores a los que les entran, por zona, y lo que no entra espera sin cobrarse. | 0 asignaciones que superen la capacidad del vehículo (kilos y bultos). 0 pedidos en espera cobrados. | O-2 |
| BR-06 | Should | Quien recibe sabe en qué está su pedido sin llamar al comercio. | 100 % de los pedidos con código de seguimiento y un mail en cada cambio de estado. | O-2, O-3 |
| BR-07 | Should | Las visitas fallidas se reducen y, cuando ocurren, se resuelven sin perder mercadería ni plata. | ≥ 85 % al primer intento (O-3). Cada visita fallida con motivo y evidencia. A las 2 visitas, el pedido vuelve al comercio con el stock repuesto y el cobro anulado. | O-3 |
| BR-08 | Should | El comercio controla su stock en los depósitos y lo que Rabbit le cobra. | El comercio ve su stock, sus liquidaciones y la vista previa del mes en curso. Toda mercadería que entra queda registrada con lo esperado y lo recibido. | O-4 |
| BR-09 | Should | El personal ve la operación del día y la mejora con datos. | Tablero de entregas en curso, mapa de repartidores e indicadores a tiempo / primer intento / costo por envío, por comercio y por repartidor. | O-2, O-3 |
| BR-10 | Could | Los picos y las zonas que la flota no cubre se resuelven con transportistas externos, sin perder el seguimiento. | 100 % de los pedidos derivados con estado actualizado automáticamente. El personal elige el transportista comparando precio y plazo. | O-2 |
| BR-11 | Could | Quien compra puede retirar en un depósito y calificar la entrega; el almacenamiento se cobra según lo que ocupa. | Retiro con código. Calificación de 1 a 5 visible en Indicadores. Cargo por unidad y día según tamaño en la liquidación, con 7 días gratis y un recargo por stock antiguo. | O-4 |

El ejemplo de la cátedra para el formato de un BR ("Las clínicas facturan a obras sociales sin carga manual. Métrica: 0 prestaciones cargadas a mano") corresponde acá a **BR-01**.

## 7. Supuestos, restricciones y dependencias

**Supuestos**

- Cada comercio integrado tiene un ERP que puede llamar a una API REST por HTTPS con usuario y contraseña.
- El ERP manda el email de quien recibe (obligatorio) y, si lo tiene, el nombre y el teléfono.
- Rabbit reparte todos los días; la hora de corte es una sola (07:00) para toda la red.
- La mercadería en consignación llega con su peso; las medidas las carga el personal.

**Restricciones**

- Plataforma Jakarta EE 10 sobre WildFly 41, con PostgreSQL 16 y ActiveMQ Artemis (consigna de la materia).
- Integración obligatoria con un sistema legado por SOAP (el banco) y con un partner moderno por REST.
- HTTPS obligatorio para toda la aplicación; los datos personales se tratan según la Ley 25.326.
- La interfaz es web responsive: tiene que funcionar en el celular del repartidor.

**Dependencias**

- **Banco legado** (SOAP): sin él no se cobra un prepago ni una liquidación. Si no responde, el pedido no se confirma y se reintenta.
- **Transportistas** (REST y SOAP): solo para pedidos derivados.
- **Servidor de correo** (SMTP): los avisos a quien recibe. Si no está, el pedido avanza igual.
- **OpenStreetMap / Nominatim**: ubicar direcciones y ordenar la hoja de ruta; sin ubicación se ordena por código postal.

## 8. Riesgos

| Id | Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|---|
| R-01 | El banco legado no responde y los pedidos prepagos no se pueden confirmar | Media | Alto | Timeout de 5 s y circuit breaker (no se cuelga la operación); el pedido sigue pendiente y se reintenta; reversa si falla después de cobrar |
| R-02 | Un comercio accede a pedidos o evidencias de otro | Baja | Alto | Pertenencia validada en el negocio (el comercio sale de la sesión); 404 para lo ajeno; pruebas automáticas de BOLA y BFLA |
| R-03 | Exposición de datos de quien recibe (logs, seguimiento, mensajes) | Baja | Alto | Logs sin datos personales, seguimiento con solo el estado, mensajes JMS con solo IDs; criterios SEC-08 a 10 |
| R-04 | Pico de pedidos que la flota no alcanza | Alta | Medio | Lista de espera sin cobrar, derivación a transportistas, despacho por zona con transportista de respaldo |
| R-05 | Visitas fallidas por datos incompletos o ausencia | Alta | Medio | Opciones de quien recibe (reprogramar, instrucciones, autorizado), motivo y evidencia, devolución al comercio a la 2.ª visita |
| R-06 | Pérdida de pedidos si el broker de mensajería se cae | Baja | Alto | El pedido queda guardado antes de publicarse; un proceso de polling lo procesa aunque el mensaje no llegue |
| R-07 | Cobrar almacenamiento con datos de medidas incompletos | Media | Medio | Sin medidas cuenta como mediano y se marca "medidas sin cargar"; cargar las medidas antes de activar precios |
| R-08 | Fuerza bruta sobre las cuentas | Media | Medio | Bloqueo de 15 min tras 5 intentos en la web y en la API; contraseñas con bcrypt |

## 9. Criterios de éxito y KPIs

Rabbit cumple su objetivo de negocio si, al final del primer semestre de operación:

1. Los cinco objetivos de la sección 3 están en meta.
2. Ningún requisito Must (BR-01 a 05) tiene un criterio de aceptación incumplido.
3. Los comercios usan el portal para responder a sus clientes sin llamar a Rabbit (BR-06).

| KPI | Fórmula | Meta | Dónde se ve |
|---|---|---|---|
| Entregas a tiempo | Entregados el día prometido o antes / pedidos con fecha prometida | ≥ 90 % | Indicadores (personal y comercio) |
| Entregas al primer intento | Entregados sin visitas fallidas / (entregados + devueltos al comercio) | ≥ 85 % | Indicadores |
| Horas de recibido a entregado | Promedio en el período | Seguimiento, sin meta fija | Indicadores |
| Pedidos en espera | Pedidos pendientes con marca de espera | Tendencia a 0 al cierre del día | Inicio del personal, Indicadores |
| Costo por envío | Liquidación del mes / envíos entregados | Informativo para el comercio | Mis indicadores |
| Calificación de la entrega | Promedio de 1 a 5 de las entregas calificadas | ≥ 4,5 | Indicadores |
| Cobro de liquidaciones | Liquidaciones cobradas / liquidaciones del mes | 100 % al día 5 | Liquidaciones |
| Seguridad | Vulnerabilidades críticas o altas abiertas | 0 | ZAP, OSV, Trivy, matriz SEC |

## 10. Glosario y aprobaciones

| Término | Significado |
|---|---|
| Comercio | Empresa que contrata a Rabbit para entregar sus ventas |
| ERP | Sistema de gestión del comercio, que manda los pedidos por la API |
| Quien recibe | La persona que compró (o a quien va dirigido el pedido); no tiene cuenta en Rabbit |
| Stock consignado | Mercadería del comercio guardada en un depósito de Rabbit |
| Punto de picking | Lugar del comercio donde Rabbit retira la mercadería |
| Hora de corte | Hora límite (07:00) para que un pedido salga en la hoja de ruta del día |
| Fecha prometida | Día de entrega que Rabbit le promete al recibir el pedido |
| Lista de espera | Pedidos pendientes que no entran en ningún vehículo; se asignan solos cuando se libera lugar |
| Visita fallida | Intento de entrega que no se pudo completar, con motivo y evidencia |
| Remito | Comprobante firmado de la entrega; se registra con una foto |
| Código de entrega | Código de 4 dígitos que recibe por mail quien compra y que el repartidor ingresa al entregar |
| Liquidación | Cobro mensual del servicio de Rabbit al comercio (abono, envíos, kilos y almacenamiento) |
| Transportista | Empresa de envíos externa a la que se deriva un pedido |
| MoSCoW | Priorización: Must, Should, Could, Won't |

**Aprobaciones**

| Rol | Nombre | Fecha | Firma |
|---|---|---|---|
| Patrocinador (Dirección de Rabbit, rol simulado) | *(a completar)* | | |
| Analista funcional | *(a completar)* | | |
| QA responsable | *(a completar)* | | |
| Docente (revisión) | Cátedra de Desarrollo de Aplicaciones II | | |
