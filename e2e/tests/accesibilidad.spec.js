// Accesibilidad (eje A): axe-core sobre las pantallas principales, sin
// violaciones serias ni críticas de WCAG 2.1 A/AA, y sin scroll horizontal
// a 360, 768 y 1280 px de ancho (responsive).
const { test, expect } = require('@playwright/test');
const AxeBuilder = require('@axe-core/playwright').default;
const fs = require('fs');
const { cuentas, ingresar } = require('./ayuda');

const ANCHOS = [360, 768, 1280];
const SALIDA = '../target/e2e/axe';

async function revisar(page, nombre) {
  const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const graves = axe.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  // Un archivo por pantalla, antes de la aserción: un test que falla reinicia el worker.
  fs.mkdirSync(SALIDA, { recursive: true });
  fs.writeFileSync(`${SALIDA}/${nombre.replace(/\//g, '_')}.json`, JSON.stringify({
    pantalla: nombre,
    violaciones: axe.violations.map((v) => ({ id: v.id, impacto: v.impact, nodos: v.nodes.length, ayuda: v.help, ejemplo: v.nodes[0] && v.nodes[0].html.slice(0, 160) })),
  }, null, 2));
  expect.soft(graves.map((v) => `${v.id} (${v.impact}, ${v.nodes.length} nodos): ${v.help}`), `${nombre}: violaciones graves de axe`).toEqual([]);

  for (const ancho of ANCHOS) {
    await page.setViewportSize({ width: ancho, height: 800 });
    const desborde = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect.soft(desborde, `${nombre} a ${ancho}px: scroll horizontal de ${desborde}px`).toBeLessThanOrEqual(1);
  }
  await page.setViewportSize({ width: 1280, height: 800 });
}

const PANTALLAS = [
  { ruta: 'login', cuenta: null },
  { ruta: 'seguimiento', cuenta: null },
  { ruta: 'personal/inicio', cuenta: 'admin' },
  { ruta: 'personal/pedidos', cuenta: 'admin' },
  { ruta: 'personal/comercios', cuenta: 'admin' },
  { ruta: 'personal/repartidores', cuenta: 'admin' },
  { ruta: 'comercio/mis-pedidos', cuenta: 'comercio' },
  { ruta: 'comercio/mi-stock', cuenta: 'comercio' },
];

for (const { ruta, cuenta } of PANTALLAS) {
  test(`Accesibilidad · ${ruta}`, async ({ page }) => {
    if (cuenta) await ingresar(page, cuentas[cuenta]());
    await page.goto(`${ruta}.xhtml`);
    await revisar(page, ruta);
  });
}
