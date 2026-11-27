# Rabbit — Situación frente a la entrega final

**Entrega y presentación: lunes 30/11/2026.** Este documento es el estado al viernes 27/11/2026.
Léanlo completo antes de ensayar: dice qué está listo, qué falta y quién lo tiene que hacer.

## En una frase
El código, las pruebas y la seguridad están terminados y verificados con números reales. Lo que falta es casi todo **gestión y defensa**: Jira, declaración de uso de IA, ensayo en vivo y tag final. Nada de eso lo puede hacer una sola persona por el equipo.

## Qué está listo (verificado)
Último commit en `main`: `901c386`. Las cuatro automatizaciones de GitHub (CI, Carga, Seguridad, CodeQL) están en verde, y llevan siete corridas seguidas así.

| Criterio | Umbral | Resultado real |
|---|---|---|
| Cobertura de negocio | ≥80 % líneas, ≥70 % ramas | 98,2 % líneas, 92,1 % ramas |
| Mutación (PIT) | ≥60 % | 89 % |
| SonarCloud | Quality Gate | Aprobado, 0 bugs, 0 vulnerabilidades |
| Rendimiento (k6) | p95 < 500 ms | p95 101 ms, 16.240 pedidos, 0 % de errores |
| Seguridad (ZAP) | Sin alertas High | 0 High (las 2 Medium y la Low están justificadas en `docs/seguridad/INFORME-SEGURIDAD.md`) |
| Accesibilidad (Lighthouse) | ≥90 | 100 en las 9 pantallas medidas |
| Contratos de integración | Newman, SOAP, REST | 50 requests y 71 aserciones sin fallas; XXE rechazado con Fault |
| Defectos | Sin Blocker/Critical abiertos | 0 abiertos en el registro |

Qué quedó hecho además: tests de la IA (`docs/testing/PRUEBAS-DE-IA.md`), ADR-073, READMEs actualizados, justificación de las alertas de ZAP, validación de importes en el banco simulado.

## Estado de las verificaciones G1–G8
| Código | Qué pide | Estado |
|---|---|---|
| G1 | Build reproducible con un solo comando | Verificado en CI (job del `.env`) |
| G2 | Tag `v1.0-final` en `main` | **Pendiente.** Se crea al final, solo cuando el equipo lo confirme. Hoy existe solo `v1.0-testing` |
| G3 | CI en verde | Listo |
| G4 | Jira al día y QA designado | **Pendiente** |
| G5 | Sin secretos en el historial | Verificado por gitleaks en CI |
| G6 | Demo en vivo | **Pendiente: falta ensayo** |
| G7 | Declaración de uso de IA | **Pendiente: falta la parte de cada integrante** |
| G8 | Informe honesto | **Pendiente: faltan los ids de Jira** |

## Lo que falta, con responsable
### 🔴 Críticos
| # | Pendiente | Quién |
|---|---|---|
| 1 | Poner Jira al día y designar al QA (G4) | Equipo, con quien lleve Jira |
| 2 | Reemplazar los 31 "(completar)" de `docs/testing/REGISTRO-DE-DEFECTOS.md` por ids de Jira (G8) | QA |
| 3 | Completar la declaración de uso de IA **de cada integrante** en `docs/consigna/USO-DE-IA.md` (G7). Cada uno escribe lo suyo, con honestidad | **Todos** |
| 4 | Crear el tag `v1.0-final` en `main` (G2) | Felipe, al final |

### 🟠 Importantes
| # | Pendiente | Quién |
|---|---|---|
| 5 | Confirmar que el Grupo es el 9 (salió de la colección de Postman) y escribir el nombre del QA responsable en `docs/testing/PLAN-DE-PRUEBAS.md` | Equipo |
| 6 | Ensayo en vivo (ver abajo) | Todos los que presenten |

### 🟡 UX
| # | Pendiente |
|---|---|
| 7 | Mirar `comercio/mis-liquidaciones` y `comercio/mis-indicadores` entrando como `demo.comercio` |
| 8 | Mirar desde un celular que los montos con `$` no se partan y que los gráficos se vean con datos |

### 🟢 Menores
| # | Pendiente |
|---|---|
| 9 | Dejar la base limpia justo antes de presentar (comando abajo). Cualquier prueba la ensucia |
| 10 | Borrar la rama `desafio-heterogeneidad` cuando el equipo lo decida |

## Ensayo de la demo en vivo (G6)
Hay que mostrar y poder explicar cada una de estas cuatro cosas:
1. **Circuit breaker:** con el banco simulado caído, hacer 4 confirmaciones de pago y ver el fallback y el log `[Pagos][Circuito]`.
2. **Mensajería:** cola, tópico y DLQ, también con el broker apagado.
3. **Mail real:** necesita hotspot del celular. El wifi de la facultad bloquea SMTP.
4. **Repartidor:** pantalla "Mis entregas".

Entrar a `https://localhost:8443/Rabbit`. El navegador va a avisar del certificado: es autofirmado, hay que aceptarlo. Cada vez que se reconstruye WildFly el certificado cambia y se pierde la sesión.

## Qué decir con honestidad en la defensa
- **La IA con respuesta real del modelo no se verificó.** La cuenta de Anthropic no tiene crédito. Está probado que la validación de entrada y salida funciona y que, si la IA falla, se muestra el texto original (fallback). No se probó una etiqueta devuelta por el modelo de verdad. Está dicho así en `USO-DE-IA.md` y en `PRUEBAS-DE-IA.md`. No afirmen lo contrario.
- El caso IA-12 (descartar respuesta vieja) está probado a nivel de la consulta, no con una carrera real entre dos hilos.
- Cobertura total del proyecto en Sonar: 68 %. El umbral exigido es sobre el código de negocio (98,2 %) y sobre el código nuevo (93,5 %).
- Sonar marca 225 code smells: ninguno bloquea el Quality Gate.

## Reglas hasta el lunes
- **No hagan push a `main` sin avisar al equipo.** Cualquier cambio nuevo obliga a esperar de nuevo el CI completo.
- **No ensucien la base de Docker** antes de la demo, o resetéenla después con:
  `env -u ANTHROPIC_API_KEY docker compose down -v && env -u ANTHROPIC_API_KEY docker compose up --build -d`
  El `env -u` es obligatorio: si el shell tiene una `ANTHROPIC_API_KEY` propia, pisa la del `.env`.
- No pongan claves ni contraseñas en el repo, en chats ni en capturas.
- Si algo falla en la demo, decirlo y mostrar el fallback es mejor que improvisar.
- Cada integrante tiene que poder explicar su parte en la defensa individual.
