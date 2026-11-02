# Entrega de testing (Clase 15)

Documentos que pide la cátedra en los *Criterios de Testing y Aseguramiento de Calidad* (sección 3), con el estado de cada
uno. El PDF único que se sube a Teams (`Clase15_Grupo<N>_<Dominio>_Testing.pdf`) se arma con estos documentos.

| # | Entregable | Documento | Estado |
|---|---|---|---|
| 1 | Plan y estrategia de pruebas | [PLAN-DE-PRUEBAS.md](PLAN-DE-PRUEBAS.md) | Redactado; falta Grupo y QA responsable |
| 2 | Matriz de trazabilidad | [MATRIZ-DE-TRAZABILIDAD.md](MATRIZ-DE-TRAZABILIDAD.md) | Generada de la documentación funcional; las pruebas manuales siguen sin ejecutar |
| 3 | Especificación de casos | [ESPECIFICACION-DE-CASOS.md](ESPECIFICACION-DE-CASOS.md) | Generada; el QA revisa la columna «Tipo» |
| 4 | Informe de ejecución | [INFORME-DE-EJECUCION.md](INFORME-DE-EJECUCION.md) | Con los números del CI; falta la captura del Quality Gate y repetir Estabilidad sobre `main` |
| 5 | Registro de defectos | [REGISTRO-DE-DEFECTOS.md](REGISTRO-DE-DEFECTOS.md) | Redactado; faltan los Id de Jira y confirmar severidades |
| 6 | Informe de seguridad | [../seguridad/INFORME-SEGURIDAD.md](../seguridad/INFORME-SEGURIDAD.md) | Hecho |
| 7 | Declaración y plan de IA | [../consigna/USO-DE-IA.md](../consigna/USO-DE-IA.md) | **Pendiente** (en borrador) |
| 8 | Lecciones aprendidas | [LECCIONES-APRENDIDAS.md](LECCIONES-APRENDIDAS.md) | Redactado; el equipo revisa la interpretación |

## Reglas de este directorio

- Los números salen de corridas reales del CI sobre el commit `8b97ec6` (etiqueta `v1.0-testing`). Si se mueve la etiqueta,
  hay que actualizar los resultados.
- `MATRIZ-DE-TRAZABILIDAD.md` y `ESPECIFICACION-DE-CASOS.md` se generan desde
  [DOCUMENTACION-FUNCIONAL.md](../funcional/DOCUMENTACION-FUNCIONAL.md) con
  `python3 scripts/testing/generar_documentos.py` (desde la raíz). Si cambia un criterio de aceptación, se vuelve a correr;
  lo que se edite a mano en esos dos archivos se pierde.
