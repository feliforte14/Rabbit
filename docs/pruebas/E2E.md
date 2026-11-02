# Pruebas de punta a punta y accesibilidad

Corren en el CI (job **E2E y accesibilidad**) contra Rabbit levantado con el
compose y las cuentas demo de la semilla. El código está en [`e2e/`](../../e2e).

```bash
cd e2e && npm ci && npx playwright install chromium
BASE=https://localhost:8443/Rabbit ADMIN_USER=<admin> ADMIN_PASSWORD=<contraseña> \
DEMO_PASSWORD=<contraseña demo> ITEM=<id de "Yerba mate 1 kg"> npx playwright test
node lighthouse.js        # mismas variables
```

## Escenarios E2E (bloquean el CI)

Los pedidos se dan de alta por la API del ERP, igual que en producción, y
todo lo demás se hace por la pantalla.

| Id | Actor | Escenario |
|---|---|---|
| E2E-1 | Cualquiera | Con una contraseña incorrecta, la app avisa y no deja entrar. Sin sesión, una pantalla del personal manda al login. |
| E2E-2 | Personal | Un pedido que llegó del ERP aparece Pendiente, se confirma y pasa a Confirmado. |
| E2E-3 | Quien recibe | Sigue su pedido con el código, sin login y sin ver datos personales. Un código que no existe no muestra nada. |
| E2E-4 | Comercio | Ve en "Mis pedidos" el pedido que mandó su ERP. |
| E2E-5 | Otro comercio | No ve ese pedido (BOLA) y no puede entrar a una pantalla del personal (BFLA). |

## Accesibilidad y responsive (bloquean el CI)

- **axe-core** (WCAG 2.2 A y AA, que incluye 2.0 y 2.1) sobre 8 pantallas: login, seguimiento,
  4 del personal y 2 del comercio. Se considera falla toda violación de
  impacto *serious* o *critical*.
- **Responsive:** en cada una de esas pantallas, sin scroll horizontal a
  360, 768 y 1280 px. Hoy pasa en las 8.
- **Lighthouse** (accesibilidad y buenas prácticas) sobre 5 pantallas,
  también las que piden login. El mínimo de accesibilidad es 90.

Resultado del 29/10/2026:

| Pantalla | Lighthouse (accesibilidad) | Buenas prácticas |
|---|---|---|
| login | 96 | 100 |
| seguimiento | 95 | 100 |
| personal/inicio | 95 | 100 |
| personal/pedidos | **88** | 100 |
| comercio/mis-pedidos | 95 | 96 |

### Defectos encontrados (A11Y-01 a 04 corregidos en la PR #15; A11Y-05 en la PR #17)

| Id | Regla (impacto) | Dónde | Corrección |
|---|---|---|---|
| A11Y-01 | `html-has-lang` (serious) | Las 8 pantallas: `WEB-INF/plantillas/template.xhtml`, `login.xhtml`, `seguimiento.xhtml` | `lang="es"` en `<html>` |
| A11Y-02 | `label-content-name-mismatch` (serious) | Campana de notificaciones de la cabecera (6 pantallas) | El `aria-label` incluye el texto visible del botón |
| A11Y-03 | `label` (critical) | `personal/pedidos.xhtml`, alta manual: campo "cantidad" de cada línea | Etiqueta accesible |
| A11Y-04 | `select-name` (critical) | `personal/pedidos.xhtml`, alta manual: select de producto de cada línea | Etiqueta accesible |
| A11Y-05 | `target-size` (serious, WCAG 2.2 · 2.5.8) | Menú lateral de 6 pantallas, en ventanas de hasta 740 px de alto: cada opción medía 23 px | `min-height: 24px` en `.sidebar-nav a` (`estilos.css`). Apareció al sumar las reglas de WCAG 2.2 a axe |

Desde entonces axe y Lighthouse cortan el CI: una violación grave nueva o
una pantalla debajo de 90 lo dejan en rojo.

## Seguridad en el navegador (bloquean el CI)

En [`e2e/tests/seguridad.spec.js`](../../e2e/tests/seguridad.spec.js), paso propio del job.

| Id | Escenario |
|---|---|
| SEG-1 | Un pedido del ERP con HTML en la dirección y en el nombre de quien recibe se muestra como texto en el portal del comercio: no corre ningún script ni aparece ningún diálogo. |
| SEG-2 | El `mapa.js` desplegado muestra como texto el título de un punto con HTML (nombre de un repartidor o descripción de un depósito). Falla con el `mapa.js` anterior: era un XSS (defecto SEG-01, corregido en la PR #17). |
| SEG-3 | La cookie de sesión es `Secure`, `HttpOnly` y `SameSite=Lax`, JavaScript no la lee, y después del logout la misma cookie no entra desde otro navegador. |
| SEG-4 | Con la sesión vencida, un botón de la página abierta lleva al login y no a la página de error (defecto SES-01, corregido en la PR #17). |
