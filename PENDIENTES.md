# Pendientes — Rabbit, entrega final (lunes 30/11/2026)

Estado al 27/11/2026. Último commit verificado en `main`: `901c386`, con CI, Carga, Seguridad y CodeQL en verde.

## 🔴 Críticos
| # | Pendiente | Quién |
|---|---|---|
| 1 | Jira al día y QA designado (G4) | Felipe |
| 2 | Completar los 31 "(completar)" de `docs/testing/REGISTRO-DE-DEFECTOS.md` con ids de Jira (G8) | Felipe / QA |
| 3 | Declaración de uso de IA por integrante en `docs/consigna/USO-DE-IA.md` (G7) | Felipe y equipo |
| 4 | Crear el tag `v1.0-final` en `main` (G2), al final y solo cuando Felipe lo pida | Claude |

## 🟠 Importantes
| # | Pendiente | Quién |
|---|---|---|
| 5 | Confirmar el Grupo 9 y completar el QA responsable en `docs/testing/PLAN-DE-PRUEBAS.md` | Felipe |
| 6 | Ensayo en vivo: circuit breaker (4 confirmaciones con el banco caído), mensajería con el broker apagado (cola, tópico, DLQ), mail real con hotspot, pantalla "Mis entregas" del repartidor | Los dos |

## 🟡 Mejoras de UX
| # | Pendiente | Quién |
|---|---|---|
| 7 | Revisar `comercio/mis-liquidaciones` y `comercio/mis-indicadores` visualmente (entrar como `demo.comercio`) | Felipe, Claude revisa |
| 8 | Ver desde el celular el arreglo del `$` partido y los gráficos con datos | Felipe |

## 🟢 Menores
| # | Pendiente |
|---|---|
| 9 | Dejar Docker limpio justo antes de entregar: `env -u ANTHROPIC_API_KEY docker compose down -v && env -u ANTHROPIC_API_KEY docker compose up --build -d` |
| 10 | Borrar la rama `desafio-heterogeneidad` cuando Felipe lo indique |

## Para decir con honestidad en la defensa
La respuesta real del modelo de IA al etiquetar instrucciones de entrega no se verificó (la cuenta no tiene crédito). Solo están probados la validación y el fallback.
