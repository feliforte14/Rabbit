# Informe de ejecución

Entregable 4 de los *Criterios de Testing* (Clase 15). Versión probada: etiqueta `v1.0-testing` (en `7ba59bf` desde el 10/11/2026; ver el punto 10). Los puntos 1 a 8 se midieron sobre `b696023` y se conservan como estaban; el punto 9 trae lo medido sobre `62f1fd1` (PR #24:
bandeja de salida y cola de mensajes muertos) y el punto 10 lo medido el 10/11/2026 sobre `7ba59bf` y `73094da`. **Lo vigente es
lo del punto de número más alto** y reemplaza a los anteriores donde difieren.
Los resultados salen de las corridas del CI, salvo que se indique otra fuente.

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
| Manual / exploratoria | **Parcial** (10/11/2026) | — | — | — | Ver el punto 10 |

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
todas, con una auditoría que fallaba, `errors-in-console` (OBS-01: era el pedido de `/favicon.ico`; cerrado el 10/11/2026, ver el registro de defectos).

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
«el sistema está probado de punta a punta»: lo manual y la resiliencia con el broker real siguen pendientes, y los dos
defectos que quedaban abiertos (SEG-04, cuentas con hash MD5, y OBS-01) se cerraron el 10/11/2026. El 12/11/2026 la prueba de punta a punta con Gmail como servidor de correo encontró uno menor, EXP-03 (un `ERROR` en el log cuando dos mails piden enviar la bandeja a la vez; los mails salían igual), que se corrigió ese mismo día: hoy no hay defectos abiertos. El detalle está en el
[registro de defectos](REGISTRO-DE-DEFECTOS.md).

## 9. Cambios posteriores a la medición: resiliencia del broker (PR #24, 08/11/2026)

Una auditoría contra el eje C de los criterios encontró dos huecos (RES-01 y RES-02 del
[registro de defectos](REGISTRO-DE-DEFECTOS.md)), corregidos en la PR #24 (ADR-063 y ADR-064):

- **RES-01:** con el broker caído, el aviso de cambio de estado se perdía (solo un warning en el log), y con él el cobro
  contra entrega y la notificación. Ahora el aviso se guarda en `eventos_estado_pedido` en la misma transacción del
  cambio, se manda después del commit y un timer reenvía lo pendiente cada 15 s.
- **RES-02:** un mensaje ilegible se consumía y se perdía. Ahora el consumidor lo relanza, el broker reintenta 5 veces
  (2 s entre intentos) y lo manda a la DLQ.

Lo que se midió sobre `62f1fd1` (el merge de la PR #24 en `main`):

| Medida | Resultado | Fuente |
|---|---|---|
| Pruebas unitarias | 1042 ejecutadas, 0 fallos, 0 omitidas (eran 1022: 20 nuevas) | `mvn verify`, local y CI |
| Integración (`*IT`) | 51 de 51 | [CI](https://github.com/feliforte14/Rabbit/actions/runs/42524223722) |
| Cobertura de la capa de negocio | 97,8 % de líneas (3728 de 3811) y 91,8 % de ramas (1927 de 2099) | JaCoCo |
| Mutación (PIT) | **88 %**: 2286 de 2599 mutantes detectados (mínimo 60 %); test strength 90 %; cobertura de líneas de las clases mutadas 97 % (4396 de 4549); 47 mutantes sin cobertura | job «Mutación (PIT)» del [CI](https://github.com/feliforte14/Rabbit/actions/runs/42524223722) |
| SonarCloud, todo el proyecto | Quality Gate **OK**; 0 bugs; 0 vulnerabilidades; ratings A de confiabilidad, seguridad y mantenibilidad; duplicación 0,7 %; hotspots revisados 100 %; 0 issues Blocker o Critical | SonarCloud, análisis de `62f1fd1` |
| Workflows CI, Seguridad y Carga | Los tres en verde, todos los jobs | [CI](https://github.com/feliforte14/Rabbit/actions/runs/42524223722) · [Seguridad](https://github.com/feliforte14/Rabbit/actions/runs/42524223692) · [Carga](https://github.com/feliforte14/Rabbit/actions/runs/42524223781) |

Los code smells abiertos pasaron de 206 a **210**: las cuatro clases nuevas inyectan por campo (`java:S6813`), la misma
convención de Jakarta EE que explica los otros 201 del punto 4. Son de severidad menor y quedan a propósito.

La primera versión de la PR (`ce08854`) falló el Quality Gate de SonarCloud por la cobertura del código nuevo (78,8 %
contra el 80 % mínimo: faltaba probar el repositorio de la bandeja). Se agregó `EventoEstadoRepositoryTest` y pasó.

Prueba manual con la aplicación levantada con Docker (no automatizada): se forzó que el tópico rechazara envíos
(dirección llena) y se dieron de alta pedidos: se crearon igual, sus avisos quedaron pendientes con los reintentos
contados y, al restaurar el tópico, el timer los publicó y llegaron las notificaciones. Un mensaje roto enviado a la
cola tuvo exactamente 5 intentos y quedó en la DLQ con su cola de origen; `scripts/dlq.sh reprocesar` lo devolvió a la
cola y volvió a la DLQ por seguir roto. Uno roto en el tópico llegó a la DLQ desde las suscripciones de Notificaciones
(la de Pagos filtra por estado y no lo recibe).

## 10. Cambios y mediciones del 10/11/2026

Después del punto 9 entraron a `main` las PR #26 (mails HTML, avisos de cambio de entrega, vencimiento de retiros, reintento
de mails), #27 (días gratis y recargo por stock antiguo, ADR-065), #28 (correcciones de la prueba exploratoria) y #29
(gateway de IA, ADR-066, y `session-timeout` explícito). La etiqueta `v1.0-testing` apunta a `7ba59bf` (PR #28).
El 11/11 el gateway de IA salió de `dev` para trabajarse en la rama `AI-GateWay` (el `session-timeout` queda): sin
sus tests, `dev` corre **1147** pruebas unitarias, 0 fallos.

| Medida | Resultado | Fuente |
|---|---|---|
| Pruebas unitarias | **1169** ejecutadas, 0 fallos, 0 omitidas (eran 1042) | `mvn verify`, local |
| Cobertura de la capa de negocio | **98,1 %** de líneas (4103 de 4181 sobre `7ba59bf`) y **92,1 %** de ramas | JaCoCo, local |
| Punta a punta (Playwright) | **24 de 24**: 11 flujos (E2E-1 a 11), 5 de seguridad (SEG-1 a 5) y 8 pantallas con axe | local, contra el compose |
| CI, Seguridad, Carga y Estabilidad sobre `7ba59bf` | Los cuatro en verde, todos los jobs: [CI](https://github.com/feliforte14/Rabbit/actions/runs/42872802861) · [Seguridad](https://github.com/feliforte14/Rabbit/actions/runs/42872808400) · [Carga](https://github.com/feliforte14/Rabbit/actions/runs/42872815161) · [Estabilidad](https://github.com/feliforte14/Rabbit/actions/runs/42872820718) | GitHub Actions |
| CI, Seguridad y Carga sobre `73094da` (con el gateway de IA) | Los tres en verde: [CI](https://github.com/feliforte14/Rabbit/actions/runs/42885588643) · [Seguridad](https://github.com/feliforte14/Rabbit/actions/runs/42885594490) | GitHub Actions |

Los jobs de mutación (PIT) y SonarCloud pasaron su compuerta en esas corridas; **los porcentajes exactos no se volvieron a leer
después del punto 9** (88 % de mutación y 94,6 % de cobertura nueva en Sonar eran los vigentes sobre `5480583`): hay que
releerlos de la corrida antes de citarlos en el informe final.

**Prueba exploratoria y manual del 10/11/2026** (contra el sistema levantado con Docker; los pasos están en
[FLUJO-DE-PRUEBAS.md](../pruebas/FLUJO-DE-PRUEBAS.md)):

- **Demo de punta a punta:** ERP → pedido → confirmación → repartidor → entrega → seguimiento y calificación; con el banco
  caído (tres fallas lentas y la cuarta al instante por el circuito abierto, luego recuperación) y con un prepago que el banco
  rechaza (más de $500.000).
- **Exploratoria:** doble clic en Confirmar, dos administradores confirmando el mismo pedido a la vez, volver atrás tras cerrar
  sesión, recargar tras confirmar, ocho pedidos simultáneos sobre un producto con tres unidades (3 sincronizados y 5 descartados,
  sin stock negativo) e inyección (XSS, SQL, 6000 caracteres y unicode) en 13 pantallas. Salieron **EXP-01** y **EXP-02**
  (ver el registro de defectos), corregidos el mismo día.
- **Flujo manual:** secciones 1, 1b, 2.1, 2.4, 6b, 6c, 7, 7b, 8, 10 (remito, liquidación de un mes cerrado, eliminar), 11
  (devolución a depósito y hora de corte), 12 (validaciones de la API, fecha prometida, indicadores), 13 (los diez motivos,
  validaciones y evidencia de la visita) y 14 (geocodificación y hoja de ruta por distancia). Ningún defecto más.
- **Sigue sin ejecutar:** la alta de stock y de recepciones por pantalla (2.2 y 2.3), los puntos de picking del comercio (3), peso y
  capacidad del vehículo (10.1 y 10.2), el mapa y el horario estimado desde el celular (10.4 y 12.5), la segunda visita
  fallida, y apagar el broker de verdad para la cola de mensajes muertos (hay prueba manual del 08/11, punto 9).
- **IA:** el gateway se probó con un servidor HTTP propio (respuesta válida, inválida, caído y lento) y en vivo con un proveedor
  falso; **no se probó contra la API real de Claude** por falta de clave.
