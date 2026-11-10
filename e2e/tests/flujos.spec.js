// Diez escenarios de punta a punta (eje A), cada uno desde la perspectiva de
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

// E2E-6, 7 y 8 nacen de la prueba exploratoria del 09/11/2026: tres formularios que parecían andar
// y no hacían lo que decían. Cubren lo que los tests de los beans no ven, que es JSF en el navegador.

test('E2E-6 · Quien recibe cambia su entrega desde el seguimiento y queda guardado', async ({ page, request }) => {
  const pedido = await pedidoDelErp(request);

  await page.goto(`seguimiento.xhtml?codigo=${pedido.codigoSeguimiento}`);
  await page.locator('#formOpciones\\:instrucciones').fill('Dejar en portería');
  await page.locator('#formOpciones\\:autorizado').fill('Marta Gómez');
  await page.getByRole('button', { name: 'Guardar cambios' }).click();

  // Antes el envío no hacía nada: la página volvía al buscador sin guardar.
  await expect(page.getByText('Listo: guardamos los cambios de tu entrega')).toBeVisible();
  await page.reload();
  await expect(page.locator('#formOpciones\\:instrucciones')).toHaveValue('Dejar en portería');
  await expect(page.locator('#formOpciones\\:autorizado')).toHaveValue('Marta Gómez');
});

test('E2E-7 · Mi stock: agregar otro producto no borra lo que el comercio ya escribió', async ({ page }) => {
  await ingresar(page, cuentas.comercio());
  await page.goto('comercio/mi-stock.xhtml');

  const deposito = page.locator('#formAnuncio\\:deposito');
  await deposito.selectOption({ index: 1 });
  await page.locator('input[id$=":producto"]').nth(0).fill('Café de prueba');
  await page.locator('input[id$=":cantidad"]').nth(0).fill('10');
  const antes = await page.locator('input[id$=":producto"]').count();

  await page.getByRole('button', { name: 'Agregar otro producto' }).click();

  // Antes se vaciaban el depósito, los nombres y las cantidades.
  await expect(page.locator('input[id$=":producto"]')).toHaveCount(antes + 1);
  await expect(deposito).not.toHaveValue('');
  await expect(page.locator('input[id$=":producto"]').nth(0)).toHaveValue('Café de prueba');
  await expect(page.locator('input[id$=":cantidad"]').nth(0)).toHaveValue('10');
});

test('E2E-8 · Quien recibe califica la entrega: se guarda, se agradece y no se vuelve a pedir', async ({ browser, page, request }) => {
  const pedido = await pedidoDelErp(request);
  const codigo = pedido.codigoSeguimiento;

  // El personal lo confirma (queda asignado al repartidor de la demo).
  await ingresar(page, cuentas.admin());
  await page.goto('personal/pedidos.xhtml');
  await page.locator('tr', { hasText: codigo }).getByRole('button', { name: 'Confirmar' }).click();
  await expect(page.locator('tr', { hasText: codigo })).toContainText('Confirmado');

  // El repartidor retira: pasa a EN_CAMINO.
  const contextoRepartidor = await browser.newContext();
  const repartidor = await contextoRepartidor.newPage();
  await ingresar(repartidor, cuentas.repartidor());
  await repartidor.goto('repartidor/mis-entregas.xhtml');
  await repartidor.locator('div.hoja-ruta', { has: repartidor.getByRole('heading', { name: new RegExp(`^Pedido ${pedido.idPedido} `) }) })
    .getByRole('button', { name: 'Ya retiré el pedido' }).click();
  await contextoRepartidor.close();

  // El administrador registra la entrega con la foto del remito (sin el código: es la contingencia).
  // El botón pide confirmación con un confirm() del navegador: hay que aceptarlo.
  page.on('dialog', (dialogo) => dialogo.accept());
  const unPixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
  await page.goto('personal/pedidos.xhtml');
  await page.locator('select[id$=":pedidoEntrega"]').selectOption(String(pedido.idPedido));
  await page.locator('input[id$=":fotoRemitoAdmin"]').setInputFiles({ name: 'remito.png', mimeType: 'image/png', buffer: unPixel });
  await page.getByRole('button', { name: 'Registrar entrega' }).click();
  await expect(page.locator('tr', { hasText: codigo })).toContainText('Entregado');

  // Quien recibe califica. Antes el envío no guardaba nada y volvía al buscador.
  await page.goto(`seguimiento.xhtml?codigo=${codigo}`);
  await page.locator('label[for="estrella5"]').click();
  await page.locator('#formCalificacion\\:comentario').fill('Todo bien, gracias');
  await page.getByRole('button', { name: 'Enviar calificación' }).click();

  await expect(page.getByText('¡Gracias por calificar tu entrega!')).toBeVisible();
  await expect(page.getByText('Tu calificación')).toBeVisible();
  await expect(page.locator('#formCalificacion')).toHaveCount(0);

  // Es una sola calificación por pedido: al volver, ya no ofrece el formulario.
  await page.reload();
  await expect(page.getByText('Tu calificación')).toBeVisible();
  await expect(page.locator('#formCalificacion')).toHaveCount(0);
});

test('E2E-9 · La etiqueta con QR: cualquiera ve el seguimiento y el repartidor marca el retiro', async ({ browser, page, request }) => {
  const pedido = await pedidoDelErp(request);
  const codigo = pedido.codigoSeguimiento;

  await ingresar(page, cuentas.admin());
  await page.goto('personal/pedidos.xhtml');
  await page.locator('tr', { hasText: codigo }).getByRole('button', { name: 'Confirmar' }).click();
  await expect(page.locator('tr', { hasText: codigo })).toContainText('Confirmado');

  // La etiqueta lleva un QR que apunta al escaneo de ese pedido.
  await page.goto(`personal/etiquetas.xhtml?pedido=${pedido.idPedido}`);
  await expect(page.locator('.etiqueta-qr').first()).toHaveAttribute('data-url', new RegExp(`escaneo\\.xhtml\\?codigo=${codigo}$`));

  // Quien escanea sin sesión cae en el seguimiento público.
  const contextoAnonimo = await browser.newContext();
  const anonimo = await contextoAnonimo.newPage();
  await anonimo.goto(`escaneo.xhtml?codigo=${codigo}`);
  await expect(anonimo).toHaveURL(/seguimiento\.xhtml\?codigo=/);
  await contextoAnonimo.close();

  // El repartidor con sesión llega a su pantalla y marca el retiro. Antes el escaneo comparaba contra el
  // código del transportista (vacío en un repartidor propio) y nunca encontraba el pedido.
  const contextoRepartidor = await browser.newContext();
  const repartidor = await contextoRepartidor.newPage();
  await ingresar(repartidor, cuentas.repartidor());
  await repartidor.goto(`escaneo.xhtml?codigo=${codigo}`);
  await expect(repartidor).toHaveURL(/repartidor\/escaneo\.xhtml/);
  await repartidor.getByRole('button', { name: 'Marcar como retirado' }).click();
  await expect(repartidor.getByText('ya está en camino').first()).toBeVisible();
  await contextoRepartidor.close();
});

test('E2E-10 · Las pantallas declaran su ícono: el navegador no cae a /favicon.ico (OBS-01)', async ({ page, request }) => {
  // Sin <link rel="icon"> el navegador pide /favicon.ico en la raíz del servidor, que da 404, y Lighthouse lo
  // cuenta como error de consola en cada pantalla (best-practices 96 en vez de 100).
  async function tieneIconoQueResponde(ruta) {
    await page.goto(ruta);
    const href = await page.locator('link[rel~="icon"]').first().getAttribute('href');
    expect(href, `${ruta} no declara su ícono`).toBeTruthy();
    const respuesta = await request.get(href);
    expect(respuesta.status(), `${ruta}: el ícono ${href}`).toBe(200);
    expect(respuesta.headers()['content-type'], ruta).toContain('image/');
  }

  await tieneIconoQueResponde('login.xhtml');
  await tieneIconoQueResponde('seguimiento.xhtml?codigo=RB-NO-EXISTE');
  // Las pantallas con sesión usan la plantilla.
  await ingresar(page, cuentas.admin());
  await tieneIconoQueResponde('personal/inicio.xhtml');
});
