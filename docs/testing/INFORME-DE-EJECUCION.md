# Informe de ejecución

Entregable 4 de los *Criterios de Testing* (Clase 15). Versión probada: etiqueta `v1.0-testing`, commit `8b97ec6` en
`main`. Los resultados salen de las corridas del CI sobre ese commit, salvo que se indique otra fuente.

Evidencia (corridas sobre `8b97ec6`):
[CI](https://github.com/feliforte14/Rabbit/actions/runs/41496170001) ·
[Seguridad](https://github.com/feliforte14/Rabbit/actions/runs/41496169996) ·
[Carga](https://github.com/feliforte14/Rabbit/actions/runs/41496169964) ·
[CodeQL](https://github.com/feliforte14/Rabbit/actions/runs/41496169702)

## 1. Totales por nivel

| Nivel | Ejecutados | Pasados | Fallados | Bloqueados | Observaciones |
|---|---|---|---|---|---|
| Unitario (JUnit 5) | 963 | 963 | 0 | 0 | 0 omitidos |
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
| Cobertura de líneas, capa de negocio | **97,9 %** (3649 de 3727) | 80 % | Cumple |
| Cobertura de ramas, capa de negocio | **91,9 %** (1919 de 2089) | 70 % | Cumple |
| Cobertura de líneas, todo el código | 63,6 % (5578 de 8774) | — | Informativo: incluye vistas, DTO y simuladores |

## 3. Mutación (PIT)

Sobre `com.rabbit.*.negocio.*` y `com.rabbit.*.datos.model.*`, en el job «Mutación (PIT)» del CI:

| Métrica | Valor |
|---|---|
| Mutantes generados | 2566 |
| Mutantes detectados | **2259 (88 %)**, mínimo exigido 60 % |
| Mutantes sin cobertura | 47 |
| Test strength | 90 % |
| Cobertura de líneas de las clases mutadas | 97 % |
| Duración | 5 min 33 s |

## 4. Análisis estático

CodeQL en verde sobre el commit (Java, JavaScript, Python y Actions). SonarCloud, **medido el 03/11/2026 sobre el
análisis de `26f3995`** (el commit que incorporó las correcciones de la rama `fix/sonar-analisis-estatico`):

| Criterio de la cátedra | Umbral | Medido sobre **todo** el proyecto | Resultado |
|---|---|---|---|
| Quality Gate | Aprobado | **OK** | Cumple |
| Bugs Blocker o Critical | 0 | **0 bugs** abiertos | Cumple |
| Vulnerabilidades | 0 | **0** | Cumple |
| Rating de confiabilidad / seguridad | A / A | **A / A** (mantenibilidad: A) | Cumple |
| Duplicación | ≤ 3 % | 0,7 % | Cumple |
| Security hotspots revisados | 100 % | 100 % | Cumple |

**Antes de las correcciones** (medido el 02/11/2026 sobre `8b97ec6`, y esto quedó registrado para que se vea el cambio): 15
bugs (3 Blocker y 7 Critical), 13 vulnerabilidades, ratings E de confiabilidad y C de seguridad. El Quality Gate daba OK
igual, porque el gate por defecto de SonarCloud solo evalúa el *código nuevo* de cada cambio. Las correcciones son
SON-01 a SON-05 del [registro de defectos](REGISTRO-DE-DEFECTOS.md).

**Pendiente menor.** El nuevo análisis dejó un *code smell* de severidad Blocker (`S1845`, dos nombres que se diferencian
solo por las mayúsculas); no es un bug ni una vulnerabilidad, y se corrigió después (SON-06).

**Cobertura en SonarCloud.** El PR de las correcciones falló el gate por `new_coverage` (2,8 % contra 80 %): subir de versión
la carpeta `resources/rabbit` (1_22 a 1_23) hizo que 1330 líneas de JavaScript contaran como código nuevo, y SonarCloud
no mide cobertura de JavaScript. Se resolvió con dos cambios: se **excluyó `src/main/webapp/resources/**` solo de la
métrica de cobertura** (sigue analizándose para bugs y vulnerabilidades) y se agregaron 13 pruebas unitarias para el código
Java que había cambiado (`ServletsDeImagenTest`, `AlineadorDeRestriccionesEnumTest`, `PedidoRepositoryTest` y una de
`BancoClientTest`). La cobertura de la capa de negocio
(punto 2) la mide JaCoCo y no cambia.

**Nota.** El Quality Gate por defecto de SonarCloud evalúa el código nuevo, y por eso salió verde en cada PR. Los criterios
de la cátedra piden los umbrales sobre el proyecto, y ahí hay incumplimientos. El detalle de cada issue está en el
tablero de SonarCloud (proyecto `feliforte14_Rabbit`). *(Adjuntar la captura del tablero, que los criterios piden como
evidencia.)*

## 5. Rendimiento (k6, 50 usuarios, 5 minutos)

| Endpoint | Mediana (ms) | p95 (ms) | Máximo (ms) |
|---|---|---|---|
| Alta del ERP (POST) | 103 | 117 | 145 |
| Consulta del ERP (GET) | 95 | 103 | 129 |
| Seguimiento público (GET) | 2 | 5 | 36 |
| Todos | 3 | 96 | 711 |

16 218 requests (46,7 por segundo) y **0,00 % de errores**. Umbrales: p95 < 500 ms y errores < 1 %. Cumple.

Accesibilidad (Lighthouse, 5 pantallas): **100** en accesibilidad en todas, mínimo exigido 90. «Buenas prácticas»: 96 en
todas, con una auditoría que falla, `errors-in-console` (ver OBS-01 en el registro de defectos).

## 6. Tiempos y estabilidad

| Medida | Resultado | Umbral |
|---|---|---|
| Workflow CI | 6 min | pipeline ≤ 15 min |
| Workflow Seguridad | 4 min (con la base del NVD en caché; 11,5 min sin ella) | |
| Workflow Carga | 8 min | |
| Pruebas inestables | 0 en las 3 corridas del workflow Estabilidad | 0 |

El workflow Estabilidad corre la suite 3 veces seguidas y compara cada prueba. Sus últimas corridas fueron en verde, pero
**sobre commits anteriores a la etiqueta** (02/11, en el PR): hay que volver a correrlo sobre `main` antes de la entrega
(`Actions → Estabilidad → Run workflow`).

## 7. Qué falta ejecutar

- **Pruebas manuales** de las 8 HU con pasos manuales (ver la [matriz](MATRIZ-DE-TRAZABILIDAD.md)) y de
  [FLUJO-DE-PRUEBAS.md](../pruebas/FLUJO-DE-PRUEBAS.md): sin ejecutar al momento de redactar este informe.
- **Prueba exploratoria** (sesión expirada, doble clic, volver atrás, recarga en medio de una operación, concurrencia entre
  dos usuarios): se hizo en parte (generó SES-01); falta una pasada completa registrada.
- **Resiliencia con el broker** (apagarlo y volver a levantarlo, mensaje inválido a la cola de mensajes muertos): no hay
  prueba automatizada; se hace en vivo en la demo.
- **Formularios JSF**: inyección y XSS a mano (la API ya está cubierta por pruebas automatizadas).

## 8. Conclusión

Sobre lo automatizado, **cumplen todos los umbrales medidos**: cobertura, mutación, rendimiento, tiempos, estabilidad y
análisis estático sobre todo el proyecto (SonarCloud, punto 4, tras las correcciones SON-01 a SON-05). Eso no equivale a
«el sistema está probado de punta a punta»: lo manual y la resiliencia con el broker real siguen pendientes, y hay un
defecto mayor abierto (SEG-04, cuentas con hash MD5) con alternativa documentada. El detalle está en el
[registro de defectos](REGISTRO-DE-DEFECTOS.md).
