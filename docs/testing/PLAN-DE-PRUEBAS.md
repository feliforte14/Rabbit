# Plan y estrategia de pruebas

Entregable 1 de los *Criterios de Testing y Aseguramiento de Calidad* (Clase 15, estructura de ISO/IEC/IEEE 29119-3).
Sistema bajo prueba: **Rabbit**, plataforma de logística de última milla (Jakarta EE sobre WildFly 41, PostgreSQL 16,
ActiveMQ Artemis, API REST, SOAP y vistas JSF). Versión: etiqueta `v1.0-testing`.

- **Grupo:** *(completar)*
- **QA responsable:** *(completar; es quien figura en Jira con ese rol)*

## 1. Alcance

**Dentro del alcance**

- Las **36 historias de usuario** de [DOCUMENTACION-FUNCIONAL.md](../funcional/DOCUMENTACION-FUNCIONAL.md): las 28 de la
  entrega del diseño (HU-01 a HU-28) y las 8 agregadas después (HU-29 a HU-36), con sus 127 criterios de aceptación. La de IA (HU-37, ADR-066) se trabaja
  aparte, en la rama `AI-GateWay`.
- API REST del ERP (`/api/v1/pedidos-externos`) y seguimiento público.
- Integraciones: SOAP con el banco legado, transportistas (REST y SOAP), cola y tópico JMS.
- Seguridad (Eje D), accesibilidad, rendimiento y estabilidad de las pruebas.

**Fuera del alcance** (y por qué)

| Qué | Motivo |
|---|---|
| Banco y transportistas reales | Son sistemas de otras empresas; se prueba contra los simulados que viajan en el mismo WAR |
| Desafío de heterogeneidad (banco en Node.js, transportista en Python) | Vive en la rama `desafio-heterogeneidad`, no en la versión entregada |
| Aplicación móvil nativa | No hay: la vista es web responsive (por eso MASVS no aplica) |
| Certificado HTTPS de una autoridad reconocida, y varios nodos | El certificado es autofirmado y el límite de intentos de login vive en memoria de cada servidor (ver [SEGURIDAD.md](../seguridad/SEGURIDAD.md), limitaciones) |

## 2. Niveles y herramientas

| Nivel | Herramienta | Dónde corre | Qué prueba |
|---|---|---|---|
| Unitario | JUnit 5 + Mockito | Job «Build, tests y cobertura» | Reglas de negocio, validaciones, estados, textos, límites (1147 pruebas al 11/11/2026) |
| Integración | REST Assured (`*IT`) contra el sistema levantado con `docker compose` (base PostgreSQL y broker Artemis reales) | Job «Compose, integración y Newman» | API REST, SOAP, seguridad, errores, pertenencia (51 pruebas) |
| API | Postman + Newman, colección versionada en el repo | Mismo job | 50 requests y 71 aserciones |
| Contrato | Schemathesis (OpenAPI) y `scripts/contrato/soap.py` (WSDL) | Job «Contrato» | Respuestas contra `openapi.yaml` y los WSDL publicados |
| Punta a punta | Playwright | Job «E2E y accesibilidad» | 5 flujos, 4 de seguridad (SEG-1 a 4) |
| Accesibilidad | axe + Lighthouse | Mismo job | 8 pantallas, WCAG 2.2 AA |
| Mutación | PIT (pitest) | Job «Mutación» (en `main` o a mano) | Calidad de las pruebas del dominio |
| Análisis estático | SonarCloud + CodeQL | CI | Quality Gate, vulnerabilidades, duplicación |
| Seguridad | OSV-Scanner, Trivy, ZAP baseline y ZAP API activo, gitleaks | Workflow «Seguridad» y job «Secretos» | Ver [INFORME-SEGURIDAD.md](../seguridad/INFORME-SEGURIDAD.md) |
| Rendimiento | k6 | Workflow «Carga» | 50 usuarios, 5 minutos |
| Estabilidad | La suite del CI corrida 3 veces | Workflow «Estabilidad» | Pruebas inestables (flaky) |
| Exploratoria y manual | Guion en [FLUJO-DE-PRUEBAS.md](../pruebas/FLUJO-DE-PRUEBAS.md) | A mano | Flujos de la demo y casos sin automatizar |

**Limitación declarada.** El criterio pide pruebas de integración de repositorios y consumidores de mensajes con base y
broker reales en contenedor (Testcontainers o Arquillian). Rabbit no tiene tests de ese tipo escritos como tales: los
repositorios y los consumidores JMS se ejercitan **a través de la API sobre el sistema real levantado con compose**, no
con tests propios de cada repositorio. Es una diferencia con el criterio y se informa como tal.

## 3. Entornos y datos de prueba

- **CI:** máquina limpia de GitHub Actions; el sistema se levanta con el mismo `docker-compose.yml` que usa cualquier
  persona (acción compartida `.github/actions/levantar-rabbit`), con contraseñas generadas al azar en cada corrida.
- **Local:** `docker compose up --build` (ver el README). Una persona sin nada instalado puede seguir la guía
  *Rabbit-Levantar-con-Docker.pdf*.
- **Datos:** la semilla [`docker/semilla/semilla.sql`](../../docker/semilla/semilla.sql) carga 2 comercios, un depósito con
  stock, un punto de picking, un repartidor y 7 cuentas (administrador y demo). Los pedidos que crean los tests llevan
  `[TEST]` en la dirección.
- **Datos sensibles:** ningún dato real. Las contraseñas no están en el repositorio (`.env` fuera del control de versiones).

## 4. Criterios de entrada y de salida

**Entrada:** el sistema levanta con un solo comando en 15 minutos o menos desde cero (G1); el pipeline compila; el
alcance está congelado en la etiqueta `v1.0-testing` (G2).

**Salida** (mínimos de los criterios; los valores medidos están en [INFORME-DE-EJECUCION.md](INFORME-DE-EJECUCION.md)):

| Criterio | Umbral |
|---|---|
| HU del alcance ejecutadas | 100 % |
| Criterios de aceptación pasados | ≥ 95 %, ninguno de una HU «Must» fallado |
| Cobertura de líneas / ramas, capa de negocio | ≥ 80 % / ≥ 70 % |
| Mutation score, dominio | ≥ 60 % |
| Quality Gate | Aprobado, 0 Blocker/Critical, 0 vulnerabilidades, duplicación ≤ 3 % |
| Pipeline | ≤ 15 minutos, 0 pruebas inestables |
| Carga (50 usuarios, 5 min) | p95 < 500 ms, errores < 1 % |
| Defectos abiertos | 0 bloqueantes, 0 críticos, ≤ 3 mayores con alternativa documentada |

## 5. Riesgos priorizados

Probabilidad (P) e impacto (I) en escala 1 a 3; la prioridad es P × I.

| # | Riesgo | P | I | Prio. | Cómo lo cubren las pruebas |
|---|---|---|---|---|---|
| R1 | Un pedido se duplica por un reintento del ERP | 3 | 3 | 9 | `ApiRestIT` (reintento con la misma clave, 422 con otro cuerpo), `HuellaDePedidoTest` |
| R2 | Un comercio ve o cancela datos de otro (BOLA/IDOR) | 2 | 3 | 6 | `ApiRestIT.unPedidoDeOtroComercioEs404`, `ContextoYReferenciasDeUsuariosTest` |
| R3 | El banco no responde o rechaza al cobrar | 2 | 3 | 6 | `BancoClientTest` (Fault, timeout, circuito abierto, reversa), `CircuitBreakerBancoTest` |
| R4 | Se pierde o duplica un mensaje de la cola | 2 | 3 | 6 | Polling de respaldo (CA-01.5), `PedidoYaSincronizadoException` y `PedidoServiceTest`. **No hay prueba automatizada con el broker apagado ni con cola de mensajes muertos (DLQ): se prueba a mano en la demo** |
| R5 | Inyección o XSS en un campo de texto | 2 | 3 | 6 | `SeguridadIT`, `ApiRestIT`, E2E SEG-1 y SEG-2, ZAP activo |
| R6 | El pedido pasa a un estado inválido | 2 | 3 | 6 | `TransicionesInvalidasDevuelven409Test` y los tests de estado |
| R7 | Se filtra un secreto al repositorio | 2 | 3 | 6 | gitleaks con reglas propias en el CI; ocurrió una vez (ver el [incidente](../seguridad/INCIDENTE-2026-10-27-SECRETOS-EN-HISTORIAL.md)) |
| R8 | Datos personales en logs | 2 | 2 | 4 | `DatosPersonalesTest` y la prueba de log del geocodificador |
| R9 | Degradación bajo carga | 2 | 2 | 4 | k6, 50 usuarios |
| R10 | Pantallas inaccesibles | 2 | 2 | 4 | axe en 8 pantallas, Lighthouse |
| R11 | Error 500 con sesión vencida | 2 | 2 | 4 | `ManejadorDeVistaVencida` (7 unitarias) y E2E SEG-4 |
| R12 | Cuentas con hash débil (MD5, las anteriores a ADR-031) | 3 | 2 | 6 | **Cerrado el 10/11/2026** (SEG-04): todas las cuentas están en bcrypt y un paso del CI corta si aparece otra |

## 6. Gestión de defectos

Se registran en Jira con severidad, prioridad, pasos, esperado y obtenido, evidencia, estado y causa raíz al cerrarlos
([REGISTRO-DE-DEFECTOS.md](REGISTRO-DE-DEFECTOS.md)). Severidades de los criterios:

| Severidad | Definición | Máximo abierto |
|---|---|---|
| Bloqueante | Impide usar el flujo principal o tira el sistema | 0 |
| Crítica | Pérdida o corrupción de datos, falla de seguridad explotable, HU «Must» incumplida | 0 |
| Mayor | Funcionalidad afectada con alternativa disponible | 3, con workaround documentado |
| Menor | Impacto bajo, visual o de texto | Sin límite, registrados |

## 7. Cronograma

| Fecha | Hito |
|---|---|
| 09/11 | Último día para consultas a la cátedra en Teams |
| 10/11, antes de las 13:30 | Etiqueta `v1.0-testing` en `main` (G2); plan, matriz, informe y registro de defectos en Teams (G4) |
| 10/11 (clase 15) | Prueba en vivo: admisibilidad, presentación del QA, ejecución pedida por la cátedra, preguntas |
| 17/11 | 4.ª Entrega Parcial Obligatoria |
