# Informe de ejecución

Entregable 4 de los *Criterios de Testing* (Clase 15). Versión probada: etiqueta `v1.0-testing`. Los resultados se
midieron sobre el commit **`b696023`** de `main`, que es el último que cambió código. La etiqueta apunta a un commit
posterior que solo corrige referencias en la documentación (`docs/`), así que el código es idéntico y los números valen
igual. Los resultados salen de las corridas del CI sobre `b696023`, salvo que se indique otra fuente.

Evidencia (corridas sobre `b696023`):
[CI](https://github.com/feliforte14/Rabbit/actions/runs/41564819013) ·
[Seguridad](https://github.com/feliforte14/Rabbit/actions/runs/41564819042) ·
[Carga](https://github.com/feliforte14/Rabbit/actions/runs/41564819073) ·
[CodeQL](https://github.com/feliforte14/Rabbit/actions/runs/41564818522)

## 1. Totales por nivel

| Nivel | Ejecutados | Pasados | Fallados | Bloqueados | Observaciones |
|---|---|---|---|---|---|
| Unitario (JUnit 5) | 1022 | 1022 | 0 | 0 | 0 omitidos |
| Integración (`*IT`, REST Assured) | 51 | 51 | 0 | 0 | Contra el sistema real levantado con compose |
| API (Newman) | 50 requests | 71 aserciones, todas | 0 | 0 | |
| Contrato REST (Schemathesis) | 4 operaciones × 3 fases (ejemplos, cobertura, fuzzing) | todas | 0 | 0 | Con semilla fija (ver CON-02) |
| Contrato SOAP (WSDL) | Job «Contrato (OpenAPI y WSDL)» | pasa | 0 | 0 | |
| Punta a punta (Playwright) | 5 flujos + 4 de seguridad | 9 | 0 | 0 | |
| Accesibilidad (axe) | 8 pantallas | 8 | 0 | 0 | El CI corta ante cualquier violación (WCAG 2.2 AA) |
| Seguridad automatizada | ZAP baseline, ZAP API activo, Trivy, OSV, gitleaks | todas | 0 | 0 | Detalle en el [informe de seguridad](../seguridad/INFORME-SEGURIDAD.md) |
| Manual / exploratoria | **Sin ejecutar** | — | — | — | Ver el punto 6 |

## 2. Cobertura

Medida con JaCoCo (`mvn test`, `target/site/jacoco/jacoco.csv`) sobre los paquetes `*.negocio`, que es la capa de
negocio:

| Métrica | Medido | Mínimo | Resultado |
|---|---|---|---|
| Cobertura de líneas, capa de negocio | **97,7 %** (3676 de 3761) | 80 % | Cumple |
| Cobertura de ramas, capa de negocio | **91,8 %** (1924 de 2095) | 70 % | Cumple |
| Cobertura de líneas, todo el código | 64,4 % (5669 de 8804) | — | Informativo: incluye vistas, DTO y simuladores |

## 3. Mutación (PIT)

Sobre `com.rabbit.*.negocio.*` y `com.rabbit.*.datos.model.*`, en el job «Mutación (PIT)» del CI:

| Métrica | Valor |
|---|---|
| Mutantes generados | 2574 |
| Mutantes detectados | **2264 (88 %)**, mínimo exigido 60 % |
| Mutantes sin cobertura | 47 |
| Test strength | 90 % |
| Cobertura de líneas de las clases mutadas | 97 % (4333 de 4484) |
| Duración | 7 min 8 s |

## 4. Análisis estático

CodeQL en verde sobre el commit (Java, JavaScript, Python y Actions). SonarCloud, **medido el 03/11/2026 sobre el
análisis de `b696023`**, con los resultados de las correcciones SON-01 a SON-07:

| Criterio de la cátedra | Umbral | Medido sobre **todo** el proyecto | Resultado |
|---|---|---|---|
| Quality Gate | Aprobado | **OK** | Cumple |
| Bugs Blocker o Critical | 0 | **0 bugs** abiertos | Cumple |
| Vulnerabilidades | 0 | **0** | Cumple |
| Rating de confiabilidad / seguridad | A / A | **A / A** (mantenibilidad: A) | Cumple |
| Issues Blocker o Critical, de cualquier tipo | — | **0** | — |
| Duplicación | ≤ 3 % | 0,7 % | Cumple |
| Security hotspots revisados | 100 % | 100 % | Cumple |

**Antes de las correcciones** (medido el 02/11/2026 sobre `8b97ec6`, y esto quedó registrado para que se vea el cambio): 15
bugs (3 Blocker y 7 Critical), 13 vulnerabilidades, ratings E de confiabilidad y C de seguridad. El Quality Gate daba OK
igual, porque el gate por defecto de SonarCloud solo evalúa el *código nuevo* de cada cambio. Las correcciones son
SON-01 a SON-05 del [registro de defectos](REGISTRO-DE-DEFECTOS.md).

**Code smells: 206 abiertos, todos de severidad menor y dejados a propósito.** 201 son `java:S6813` (inyección por campo),
que es la convención de Jakarta EE (los EJB no admiten inyección por constructor y las pruebas usan `@InjectMocks`);
4 están en `qrcode.js`, una librería de terceros copiada al repositorio; y 1 es `java:S8786` en `FormatoDeEmailTest`, que
conserva a propósito la expresión regular anterior para comparar sus resultados con los del código nuevo. Los criterios de
la cátedra no piden cero code smells y el rating de mantenibilidad es A. Durante esta entrega se corrigieron un smell
Blocker (`S1845`, SON-06) y cuatro expresiones regulares con tiempo cuadrático (`S8786`, SON-07).

**Cobertura en SonarCloud.** El PR de las correcciones falló el gate por `new_coverage` (2,8 % contra 80 %): subir de versión
la carpeta `resources/rabbit` (1_22 a 1_23) hizo que 1330 líneas de JavaScript contaran como código nuevo, y SonarCloud
no mide cobertura de JavaScript. Se resolvió con dos cambios: se **excluyó `src/main/webapp/resources/**` solo de la
métrica de cobertura** (sigue analizándose para bugs y vulnerabilidades) y se agregaron 13 pruebas unitarias para el código
Java que había cambiado (`ServletsDeImagenTest`, `AlineadorDeRestriccionesEnumTest`, `PedidoRepositoryTest` y una de
`BancoClientTest`). La cobertura de la capa de negocio
(punto 2) la mide JaCoCo y no cambia.

**Nota.** El Quality Gate por defecto de SonarCloud evalúa el código nuevo, y por eso salía verde en cada PR aunque el
proyecto tuviera issues viejos: por eso hay que medir también el proyecto completo, como se hizo acá. El detalle de cada
issue está en el tablero de SonarCloud (proyecto `feliforte14_Rabbit`). *(Adjuntar la captura del tablero, que los criterios
piden como evidencia.)*

## 5. Rendimiento (k6, 50 usuarios, 5 minutos)

| Endpoint | Mediana (ms) | p95 (ms) | Máximo (ms) |
|---|---|---|---|
| Alta del ERP (POST) | 104 | 120 | 143 |
| Consulta del ERP (GET) | 96 | 106 | 137 |
| Seguimiento público (GET) | 2 | 5 | 43 |
| Todos | 2 | 98 | 432 |

16 216 requests (46,8 por segundo) y **0,00 % de errores**. Umbrales: p95 < 500 ms y errores < 1 %. Cumple.

Accesibilidad (Lighthouse, 5 pantallas): **100** en accesibilidad en todas, mínimo exigido 90. «Buenas prácticas»: 96 en
todas, con una auditoría que falla, `errors-in-console` (ver OBS-01 en el registro de defectos).

## 6. Tiempos y estabilidad

| Medida | Resultado | Umbral |
|---|---|---|
| Workflow CI | 7 min 34 s (incluye la mutación, que en un PR se saltea) | pipeline ≤ 15 min |
| Workflow Seguridad | 11 min (4 min cuando la base del NVD está en caché) | |
| Workflow Carga | 7 min 22 s | |
| Pruebas inestables | **0** en las 3 vueltas del workflow Estabilidad (11 min) | 0 |

El workflow Estabilidad corre la suite completa 3 veces seguidas y compara cada prueba: una que cambie de resultado entre
vueltas se reporta como inestable. Se corrió a mano sobre `main` en `b696023` (el 03/11/2026) y terminó en verde, con el
job «Comparar las 3 vueltas» incluido: [corrida](https://github.com/feliforte14/Rabbit/actions/runs/41566041263).

## 7. Qué falta ejecutar

- **Pruebas manuales** de las 8 HU con pasos manuales (ver la [matriz](MATRIZ-DE-TRAZABILIDAD.md)) y de
  [FLUJO-DE-PRUEBAS.md](../pruebas/FLUJO-DE-PRUEBAS.md): sin ejecutar al momento de redactar este informe.
- **Prueba exploratoria** (sesión expirada, doble clic, volver atrás, recarga en medio de una operación, concurrencia entre
  dos usuarios): se hizo en parte (generó SES-01); falta una pasada completa registrada.
- **Resiliencia con el broker, de punta a punta:** el outbox y la cola de mensajes muertos (punto 9) tienen pruebas
  unitarias y una prueba manual documentada, pero **no hay una prueba automatizada con un broker real** que lo apague
  y lo vuelva a levantar: se hace en vivo en la demo (`scripts/dlq.sh` para ver y reprocesar la DLQ).
- **Formularios JSF**: inyección y XSS a mano (la API ya está cubierta por pruebas automatizadas).

## 8. Conclusión

Sobre lo automatizado, **cumplen todos los umbrales medidos**: cobertura, mutación, rendimiento, tiempos, estabilidad y
análisis estático sobre todo el proyecto (SonarCloud, punto 4, tras las correcciones SON-01 a SON-05). Eso no equivale a
«el sistema está probado de punta a punta»: lo manual y la resiliencia con el broker real siguen pendientes, y hay un
defecto mayor abierto (SEG-04, cuentas con hash MD5) con alternativa documentada. El detalle está en el
[registro de defectos](REGISTRO-DE-DEFECTOS.md).

## 9. Cambios posteriores a la medición: resiliencia del broker (PR #24, 08/11/2026)

Una auditoría contra el eje C de los criterios encontró dos huecos (RES-01 y RES-02 del
[registro de defectos](REGISTRO-DE-DEFECTOS.md)), corregidos en la PR #24 (ADR-063 y ADR-064):

- **RES-01:** con el broker caído, el aviso de cambio de estado se perdía (solo un warning en el log), y con él el cobro
  contra entrega y la notificación. Ahora el aviso se guarda en `eventos_estado_pedido` en la misma transacción del
  cambio, se manda después del commit y un timer reenvía lo pendiente cada 15 s.
- **RES-02:** un mensaje ilegible se consumía y se perdía. Ahora el consumidor lo relanza, el broker reintenta 5 veces
  (2 s entre intentos) y lo manda a la DLQ.

Lo que se midió sobre el último commit de la PR (`9fb763e`):

| Medida | Resultado | Fuente |
|---|---|---|
| Pruebas unitarias | 1042 ejecutadas, 0 fallos, 0 omitidas (eran 1022: 20 nuevas) | `mvn verify`, local y CI |
| Integración (`*IT`) | 51 de 51 | [CI](https://github.com/feliforte14/Rabbit/actions/runs/42522549921) |
| Cobertura de la capa de negocio | 97,8 % de líneas (3728 de 3811) y 91,8 % de ramas (1927 de 2099) | JaCoCo local |
| SonarCloud sobre el código nuevo | Quality Gate **OK**, cobertura del código nuevo 97,5 % | PR #24 |
| Workflows CI, Seguridad y Carga | Los tres en verde | [CI](https://github.com/feliforte14/Rabbit/actions/runs/42522549921) · [Seguridad](https://github.com/feliforte14/Rabbit/actions/runs/42522549922) · [Carga](https://github.com/feliforte14/Rabbit/actions/runs/42522550004) |

La primera versión de la PR (`ce08854`) falló el Quality Gate de SonarCloud por la cobertura del código nuevo (78,8 %
contra el 80 % mínimo: faltaba probar el repositorio de la bandeja). Se agregó `EventoEstadoRepositoryTest` y pasó.

Prueba manual con la aplicación levantada con Docker (no automatizada): se forzó que el tópico rechazara envíos
(dirección llena) y se dieron de alta pedidos: se crearon igual, sus avisos quedaron pendientes con los reintentos
contados y, al restaurar el tópico, el timer los publicó y llegaron las notificaciones. Un mensaje roto enviado a la
cola tuvo exactamente 5 intentos y quedó en la DLQ con su cola de origen; `scripts/dlq.sh reprocesar` lo devolvió a la
cola y volvió a la DLQ por seguir roto. Uno roto en el tópico llegó a la DLQ desde las suscripciones de Notificaciones
(la de Pagos filtra por estado y no lo recibe).

**Pendiente de medir sobre este código:** la mutación (PIT), que el CI corre solo en `main` y en la PR se saltea. Los
números de los puntos 1 a 8 de este informe son los de `b696023`, salvo que se indique otra cosa en esta sección.
