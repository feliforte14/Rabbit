// Cinco escenarios de punta a punta (eje A), cada uno desde la perspectiva de
// un actor distinto. Los pedidos se dan de alta por la API del ERP, como en
// producción; todo lo demás se hace por la pantalla.
const { test, expect } = require('@playwright/test');
const { cuentas, ingresar, pedidoDelErp } = require('./ayuda');

test('E2E-1 · Login con contraseña incorrecta: avisa y no entra', async ({ page }) => {
  await ingresar(page, { usuario: cuentas.admin().usuario, clave: 'no-es-la-clave' });
  await expect(page.getByText('Usuario o contraseña incorrectos')).toBeVisible();
  await expect(page).toHaveURL(/login\.xhtml/);

  // Y sin sesión, una pantalla del personal manda al login.
  await page.goto('personal/pedidos.xhtml');
  await expect(page).toHaveURL(/login\.xhtml/);
});

test('E2E-2 · Pedido del ERP: el personal lo ve y lo confirma', async ({ page, request }) => {
  const pedido = await pedidoDelErp(request);

  await ingresar(page, cuentas.admin());
  await expect(page).toHaveURL(/personal\/inicio\.xhtml/);
  await page.goto('personal/pedidos.xhtml');

  const fila = page.locator('tr', { hasText: pedido.codigoSeguimiento });
  await expect(fila).toContainText('Pendiente');
  await fila.getByRole('button', { name: 'Confirmar' }).click();

  const confirmada = page.locator('tr', { hasText: pedido.codigoSeguimiento });
  await expect(confirmada).toContainText('Confirmado');
  await expect(confirmada.getByRole('button', { name: 'Confirmar' })).toHaveCount(0);
});

test('E2E-3 · Quien recibe sigue su pedido sin login', async ({ page, request }) => {
  const pedido = await pedidoDelErp(request);

  await page.goto(`seguimiento.xhtml?codigo=${pedido.codigoSeguimiento}`);
  await expect(page.locator('.seguimiento-codigo')).toContainText(pedido.codigoSeguimiento);
  // El seguimiento público no muestra datos personales (ADR-039).
  await expect(page.getByText('e2e@demo.test')).toHaveCount(0);

  await page.goto('seguimiento.xhtml?codigo=RB-NO-EXISTE');
  await expect(page.locator('.seguimiento-codigo')).toHaveCount(0);
});

test('E2E-4 · El comercio ve los pedidos de su ERP', async ({ page, request }) => {
  const pedido = await pedidoDelErp(request);

  await ingresar(page, cuentas.comercio());
  await expect(page).toHaveURL(/comercio\/mis-pedidos\.xhtml/);
  await expect(page.locator('tr', { hasText: pedido.codigoSeguimiento })).toBeVisible();
});

test('E2E-5 · Otro comercio no ve ese pedido ni entra a pantallas del personal (BOLA/BFLA)', async ({ page, request }) => {
  const pedido = await pedidoDelErp(request);

  await ingresar(page, cuentas.comercio2());
  await expect(page).toHaveURL(/comercio\/mis-pedidos\.xhtml/);
  await expect(page.getByText(pedido.codigoSeguimiento)).toHaveCount(0);

  // Una pantalla del personal con sesión de comercio: no se muestra (403,
  // o la app lo devuelve a su propio inicio).
  const r = await page.goto('personal/pedidos.xhtml');
  expect(r.status() === 403 || !/\/personal\//.test(page.url()), `respondió ${r.status()} en ${page.url()}`).toBeTruthy();
  await expect(page.getByText(pedido.codigoSeguimiento)).toHaveCount(0);
});
