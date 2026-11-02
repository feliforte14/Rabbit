// Seguridad en el navegador (eje D): lo que las pruebas de la API no ven.
// Un texto con HTML tiene que mostrarse como texto en cada pantalla, y la
// cookie de sesión tiene que salir con sus banderas.
const { test, expect } = require('@playwright/test');
const { cuentas, ingresar, pedidoDelErp } = require('./ayuda');

const XSS_IMG = '"><img src=x onerror="window.__xss=1">';
const XSS_SCRIPT = '<script>window.__xss=2</script>';

// Cualquier alert/confirm que aparezca es un script inyectado que corrió.
function vigilarDialogos(page) {
  const vistos = [];
  page.on('dialog', async (d) => { vistos.push(d.message()); await d.dismiss(); });
  return vistos;
}

test('SEG-1 · Un pedido con HTML en la dirección y el nombre se muestra como texto', async ({ page, request }) => {
  const pedido = await pedidoDelErp(request, {
    direccionEntrega: `[TEST] ${XSS_IMG} Av. Corrientes 1234`,
    nombreDestinatario: XSS_SCRIPT,
  });
  const dialogos = vigilarDialogos(page);

  await ingresar(page, cuentas.comercio());
  await page.goto('comercio/mis-pedidos.xhtml');
  const fila = page.locator('tr', { hasText: pedido.codigoSeguimiento }).first();
  await expect(fila).toContainText('<img src=x onerror=');
  await expect(fila).toContainText('<script>window.__xss=2</script>');

  expect(await page.evaluate(() => window.__xss)).toBeUndefined();
  expect(dialogos).toEqual([]);
});

// Leaflet pone el texto de un tooltip como HTML: mapa.js tiene que pasarle un
// nodo de texto. Se prueba el mapa.js desplegado, con un punto cuyo título
// trae HTML (el nombre de un repartidor o la descripción de un depósito).
test('SEG-2 · El mapa muestra el título de un punto como texto, no como HTML', async ({ page }) => {
  await page.goto('login.xhtml');
  await page.evaluate((titulo) => {
    const div = document.createElement('div');
    div.className = 'mapa';
    div.style.cssText = 'position:fixed;top:0;left:0;width:400px;height:300px;z-index:9999';
    div.setAttribute('data-puntos', JSON.stringify([{ lat: -34.6, lng: -58.4, titulo, detalle: titulo }]));
    document.body.appendChild(div);
  }, XSS_IMG + 'Juan');
  await page.addScriptTag({ url: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js' });
  await page.addScriptTag({ url: 'jakarta.faces.resource/mapa.js.xhtml?ln=rabbit' });

  // El tooltip se abre al pasar el mouse por el punto.
  await page.locator('.leaflet-interactive').dispatchEvent('mouseover');
  const tooltip = page.locator('.leaflet-tooltip');
  await expect(tooltip).toContainText('<img src=x onerror=');
  await expect(tooltip.locator('img')).toHaveCount(0);
  expect(await page.evaluate(() => window.__xss)).toBeUndefined();
});

test('SEG-3 · La cookie de sesión es Secure, HttpOnly y SameSite=Lax, y el logout la invalida', async ({ page, context, browser }) => {
  await ingresar(page, cuentas.comercio());
  await expect(page).toHaveURL(/comercio\/mis-pedidos\.xhtml/);
  const sesion = (await context.cookies()).find((c) => c.name === 'JSESSIONID');
  expect(sesion).toBeDefined();
  expect(sesion.secure).toBe(true);
  expect(sesion.httpOnly).toBe(true);
  expect(sesion.sameSite).toBe('Lax');
  // JavaScript no la puede leer.
  expect(await page.evaluate(() => document.cookie)).not.toContain('JSESSIONID');

  await page.getByTitle('Cerrar sesión').click();
  await expect(page).toHaveURL(/login\.xhtml/);

  // La misma cookie, usada desde otro navegador después del logout, ya no sirve.
  const otro = await browser.newContext({ ignoreHTTPSErrors: true });
  await otro.addCookies([sesion]);
  const intruso = await otro.newPage();
  await intruso.goto(new URL('comercio/mis-pedidos.xhtml', page.url()).href);
  await expect(intruso).toHaveURL(/login\.xhtml/);
  await otro.close();
});
