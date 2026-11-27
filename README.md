# Rabbit — Qué se pide para el lunes y dónde estamos

**Entrega y presentación: lunes 30/11/2026.** Estado al viernes 27/11/2026, sobre el commit `4e27df1` de `main`.
Leyenda: ✅ listo y verificado · ⚠️ listo con una salvedad que hay que saber decir · ⏳ falta, y lo tiene que hacer una persona del equipo.

## En una frase
Todo lo técnico está terminado y medido. Lo que falta es **gestión** (Jira, QA, declaración de IA) y **defensa** (ensayo en vivo, tag final). Eso no lo puede cerrar una sola persona.

## 1. Lo que pide la consigna, punto por punto

### Integraciones y producto
| Se pide | Estado | Dónde mirarlo |
|---|---|---|
| Integración SOAP legado con Fault y protección XXE | ✅ XXE rechazado con Fault; el banco rechaza importes menores o iguales a cero | `docs/seguridad/INFORME-SEGURIDAD.md` |
| API REST con Idempotency-Key, ETag y Problem Details | ✅ Newman: 50 requests, 71 aserciones, 0 fallas | `docs/testing/` |
| Mensajería con cola, tópico y DLQ | ✅ implementada y probada | `docs/arquitectura/DECISIONES.md` |
| Resiliencia: timeout de 5 s y fallback | ✅ circuit breaker del banco; **falta mostrarlo en vivo** (ver G6) | ADR y `INFORME-DE-EJECUCION.md` |
| IA a través de un gateway propio | ⚠️ gateway con validación, timeout y fallback, todo probado. **La respuesta real del modelo no se verificó** (cuenta sin crédito) | `docs/consigna/USO-DE-IA.md`, `docs/testing/PRUEBAS-DE-IA.md` |
| QA designado en Jira | ⏳ | Jira |

### Umbrales de calidad (Ejes A–E)
| Se pide | Umbral | Resultado real | Estado |
|---|---|---|---|
| Cobertura del código de negocio | ≥80 % líneas, ≥70 % ramas | 98,2 % y 92,1 % | ✅ |
| Mutación (PIT) | ≥60 % | 89 % | ✅ |
| SonarCloud | Quality Gate aprobado | Aprobado, 0 bugs, 0 vulnerabilidades | ✅ |
| Rendimiento (k6) | p95 < 500 ms | p95 101 ms, 16.240 pedidos, 0 % de errores | ✅ |
| Seguridad (ZAP) | Sin alertas High | 0 High; las 2 Medium y la Low están justificadas | ✅ |
| Accesibilidad (Lighthouse) | ≥90 | 100 en las 9 pantallas medidas | ✅ |
| Defectos abiertos | Sin Blocker/Critical, hasta 3 Major con workaround | 0 abiertos | ✅ |
| Documentación completa | Plan de pruebas, trazabilidad, defectos, manual | Hecha; manual de usuario aparte del repo. Faltan los ids de Jira y el nombre del QA | ⏳ |
| Defensa individual | Cada integrante explica su parte | Depende de cada uno | ⏳ |

### Ensayo de entrega G1–G8
| Código | Se pide | Estado |
|---|---|---|
| G1 | Build reproducible con un solo comando | ✅ verificado en CI (job del `.env`) |
| G2 | Tag `v1.0-final` en `main` | ⏳ se crea al final, cuando el equipo lo confirme. Hoy solo existe `v1.0-testing` |
| G3 | CI en verde | ✅ ver nota abajo |
| G4 | Jira al día y QA designado | ⏳ |
| G5 | Sin secretos en el historial | ✅ gitleaks en CI |
| G6 | Demo en vivo | ⏳ falta ensayar |
| G7 | Declaración de uso de IA | ⏳ falta la parte de cada integrante |
| G8 | Informe honesto | ⏳ faltan los 31 ids de Jira |

**Nota sobre el CI:** la última corrida en `4e27df1` pasó en verde (CI, Carga, Seguridad y CodeQL). El job de E2E falló una vez porque Maven Central no respondió al bajar el driver de PostgreSQL. No tuvo que ver con el código y pasó al reintentar. Si pasa de nuevo, reintenten el job y no toquen nada.

## 2. Lo que falta, con responsable
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

## 3. Ensayo de la demo en vivo (G6)
Hay que mostrar y poder explicar cada una de estas cuatro cosas:
1. **Circuit breaker:** con el banco simulado caído, hacer 4 confirmaciones de pago y ver el fallback y el log `[Pagos][Circuito]`.
2. **Mensajería:** cola, tópico y DLQ, también con el broker apagado.
3. **Mail real:** necesita hotspot del celular. El wifi de la facultad bloquea SMTP.
4. **Repartidor:** pantalla "Mis entregas".

Entrar a `https://localhost:8443/Rabbit`. El navegador avisa del certificado: es autofirmado, hay que aceptarlo. Cada vez que se reconstruye WildFly el certificado cambia y se pierde la sesión.

## 4. Qué decir con honestidad en la defensa
- **La IA con respuesta real del modelo no se verificó.** La cuenta de Anthropic no tiene crédito. Está probado que la validación de entrada y salida funciona y que, si la IA falla, se muestra el texto original (fallback). No se probó una etiqueta devuelta por el modelo de verdad. Está dicho así en `USO-DE-IA.md` y en `PRUEBAS-DE-IA.md`. No afirmen lo contrario.
- El caso IA-12 (descartar respuesta vieja) está probado a nivel de la consulta, no con una carrera real entre dos hilos.
- **Cobertura total de Sonar: 71,3 %.** Subió desde 68,2 % con tests nuevos de las pantallas de pedidos y del repartidor. El umbral exigido es sobre el código de negocio (98,2 %) y sobre el código nuevo (93,5 %), y ambos se cumplen. Lo que sigue bajo son las pantallas (beans JSF), que se prueban de punta a punta con Playwright pero Sonar no cuenta esa cobertura.
- Sonar marca unas 225 observaciones de mantenimiento (code smells): ninguna bloquea el Quality Gate.

## 5. Reglas hasta el lunes
- **No hagan push a `main` sin avisar al equipo.** Cualquier cambio nuevo obliga a esperar de nuevo el CI completo.
- **No ensucien la base de Docker** antes de la demo, o resetéenla después con:
  `env -u ANTHROPIC_API_KEY docker compose down -v && env -u ANTHROPIC_API_KEY docker compose up --build -d`
  El `env -u` es obligatorio: si el shell tiene una `ANTHROPIC_API_KEY` propia, pisa la del `.env`.
- No pongan claves ni contraseñas en el repo, en chats ni en capturas.
- Si algo falla en la demo, decirlo y mostrar el fallback es mejor que improvisar.
- Cada integrante tiene que poder explicar su parte en la defensa individual.
