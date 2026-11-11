# Lecciones aprendidas

Entregable 8 de los *Criterios de Testing* (Clase 15): tres defectos que llegaron tarde, por qué no se detectaron antes y
qué prueba los habría atrapado. Los datos salen de los commits y documentos del repositorio; **la interpretación («por qué»)
es del redactor y el equipo tiene que poder defenderla o corregirla**.

## 1. SEG-01: XSS en el título del mapa

- **Qué fue:** el mapa ponía como HTML el nombre de un repartidor o la descripción de un depósito. Un nombre con
  `<img src=x onerror=...>` ejecutaba un script en el navegador de otro usuario del personal.
- **Cuándo llegó:** en la ronda de pruebas de seguridad del Eje D, a días de la entrega, no durante el desarrollo.
- **Por qué no se detectó antes:** el escape de HTML se había pensado y probado donde se ve: en las pantallas JSF, que
  escapan lo que muestran por defecto. Pero el mapa lo dibuja JavaScript (Leaflet), y su `bindTooltip` inserta como HTML lo
  que recibe. Las pruebas de servidor nunca ejecutan JavaScript, así que no tenían cómo verlo. Además, la política de
  seguridad del navegador (CSP) permite scripts en línea por una limitación de JSF, y no frenó el ataque.
- **Qué prueba lo habría atrapado:** una prueba punta a punta en un navegador real con una carga maliciosa en el dato de
  entrada. Hoy existe (E2E SEG-2, que falla con el código anterior). La regla que queda: toda entrada que llega a una
  pantalla se prueba **en el navegador**, no solo en el servidor.

## 2. CON-02: un número fuera de rango se guardaba cambiado

- **Qué fue:** `POST /v1/pedidos-externos` con un `idItem` fuera del rango de `long` respondía 201 y guardaba otro número:
  el desborde cambiaba el signo sin avisar. También aceptaba 0 y números negativos.
- **Cuándo llegó:** en una vuelta de la prueba de estabilidad, al fijar las semillas de Schemathesis, no en las pruebas de
  validación.
- **Por qué no se detectó antes:** las pruebas de validación de la API cubrían los errores comunes (campo faltante, texto donde
  va un número). No había pruebas de valores límite: ni el cero ni los negativos (el defecto muestra que `0` y `-5` también
  se aceptaban) ni un entero más grande que el máximo. La prueba que lo encontró fue de generación automática de casos, que no depende de que a alguien se le ocurra.
- **Qué prueba lo habría atrapado:** pruebas de contrato con generación automática de casos (Schemathesis) corriendo desde
  que se escribió el contrato, y una lista fija de **valores límite por tipo** (cero, negativo, máximo, máximo + 1) para
  cada campo numérico. Hoy existen `NumerosEstrictosTest` y el paso de Schemathesis corta el CI.

## 3. SES-01: «Algo salió mal» con la sesión vencida

- **Qué fue:** con la sesión vencida, tocar un botón de una página que ya estaba abierta terminaba en la pantalla de error
  genérica, con un 500.
- **Cuándo llegó:** en la prueba exploratoria del Eje A (sesión expirada), no antes.
- **Por qué no se detectó antes:** todas las pruebas automatizadas arrancaban con una sesión válida y la usaban enseguida.
  Ninguna dejaba pasar el tiempo ni vencía la sesión a propósito. Además, había una regla en `web.xml` para esa excepción
  que **parecía** resolver el caso: pero Mojarra envuelve la excepción en otra, y la regla no se aplicaba. La corrección
  estaba escrita y no funcionaba, y nada lo mostraba.
- **Qué prueba lo habría atrapado:** una prueba punta a punta que venza la sesión y luego actúe (hoy, E2E SEG-4), y la regla
  general de **verificar en ejecución que una configuración de error realmente se aplica**, no solo leerla.

## Un cuarto caso, de esta misma semana: SEG-02

El geocodificador escribía en el log las direcciones de entrega y sus coordenadas, y se vio **mirando el log de un
arranque con Docker**. Ya existía un script para revisar los logs (`scripts/seguridad/revisar_logs.sh`) y la
documentación afirmaba que la dirección de entrega no se escribía en ningún log: la afirmación se dio por cumplida sin una
prueba que la sostuviera. Ahora hay un test que captura el log y verifica que no aparezcan. Lección: **una garantía escrita
en la documentación sin una prueba que la verifique es una hipótesis**, no un resultado.

## Lo que cambió en cómo probamos (y lo que falta)

1. Cada entrada que llega a una pantalla se prueba también en un navegador, con una carga maliciosa.
2. Los campos numéricos tienen valores límite explícitos y generación automática de casos desde el contrato.
3. Hay pruebas con sesión vencida, no solo con sesión válida.
4. Un «cumple» sin prueba ejecutada se marca **no verificable** (así figura en el
   [informe de seguridad](../seguridad/INFORME-SEGURIDAD.md)).
5. **A adoptar:** revisar el log del servidor en cada arranque limpio. Dos defectos de esta entrega (SEG-02 y UI-01) salieron de ahí, y hoy esa revisión no está automatizada.
