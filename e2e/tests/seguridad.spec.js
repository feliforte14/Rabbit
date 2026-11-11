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

// Prueba exploratoria de la cátedra: "sesión expirada". Con la página abierta,
// la sesión vence (acá se borra la cookie, que es lo que ve el servidor) y el
// usuario aprieta un botón: tiene que volver al login, no a "Algo salió mal".
test('SEG-4 · Con la sesión vencida, un botón de la página abierta lleva al login', async ({ page, context }) => {
  await ingresar(page, cuentas.comercio());
  await expect(page).toHaveURL(/comercio\/mis-pedidos\.xhtml/);
  await context.clearCookies();

  await page.evaluate(() => document.querySelector('form[method=post]').submit());
  await expect(page).toHaveURL(/login\.xhtml/);
  await expect(page.getByText('Algo salió mal')).toHaveCount(0);
});

// Hallado en la prueba exploratoria del 10/11: un byte nulo (%00) en el código de la página pública de
// seguimiento llegaba a PostgreSQL y daba un error 500. Un código que no se parece a uno real es "no encontrado".
test('SEG-5 · Un código de seguimiento con un byte nulo o larguísimo no da error del servidor', async ({ request, page }) => {
  for (const codigo of ['RB-%00X', 'A'.repeat(6000), "RB-ABC'%3B--"]) {
    const r = await request.get(`api/v1/seguimiento/${codigo}`);
    expect(r.status(), `API con ${codigo.slice(0, 12)}`).toBe(404);
  }
  const respuesta = await page.goto('seguimiento.xhtml?codigo=RB-%00X');
  expect(respuesta.status()).toBe(200);
  await expect(page.getByText('Algo salió mal')).toHaveCount(0);
});

// CSRF (condición de la cátedra para usar sesión en vez de JWT, ADR-067). El formulario de JSF lleva un
// token por sesión y por vista (jakarta.faces.ViewState). Un sitio ajeno puede hacer que el navegador de
// la víctima mande el POST con su cookie, pero no conoce ese token. Acá se captura el POST real del botón
// "Confirmar" (sin dejarlo llegar) y se lo reenvía con la cookie válida de la víctima en las tres formas
// en que lo podría armar un atacante: sin token, con un token inventado y con el token de SU propia sesión.
// Ninguna confirma el pedido. Al final, el mismo POST con el token correcto sí lo confirma: eso prueba que
// lo único que frenaba a los otros tres era el token.
test('SEG-6 · CSRF: un POST con la cookie válida pero sin el token de la vista no confirma el pedido', async ({ page, request, browser, baseURL }) => {
  const pedido = await pedidoDelErp(request);
  const TOKEN = 'jakarta.faces.ViewState';

  // El POST que arma el navegador al apretar "Confirmar", capturado y cortado antes de salir.
  async function capturarConfirmar(pagina) {
    await pagina.goto('personal/pedidos.xhtml');
    let capturado;
    await pagina.route('**/personal/pedidos.xhtml', async (ruta) => {
      const pedidoHttp = ruta.request();
      if (pedidoHttp.method() !== 'POST') return ruta.continue();
      capturado = { url: pedidoHttp.url(), cuerpo: new URLSearchParams(pedidoHttp.postData()), cabeceras: pedidoHttp.headers() };
      return ruta.abort();
    });
    await pagina.locator('tr', { hasText: pedido.codigoSeguimiento }).getByRole('button', { name: 'Confirmar' }).click();
    await expect.poll(() => capturado, { message: 'el botón Confirmar tiene que mandar un POST' }).toBeTruthy();
    await pagina.unroute('**/personal/pedidos.xhtml');
    return capturado;
  }

  // El estado se lee por el seguimiento público: no depende de la sesión que se está probando.
  const estado = async () => (await (await request.get(`api/v1/seguimiento/${pedido.codigoSeguimiento}`)).json()).estado;

  // La víctima: un administrador con la sesión abierta.
  await ingresar(page, cuentas.admin());
  await expect(page).toHaveURL(/personal\/inicio\.xhtml/);
  const legitimo = await capturarConfirmar(page);
  expect(legitimo.cuerpo.get(TOKEN), 'el formulario lleva el token de la vista').toBeTruthy();

  // El atacante: otra cuenta del personal, en otro navegador, con su propio token válido.
  const otroNavegador = await browser.newContext({ ignoreHTTPSErrors: true, baseURL });
  const paginaAtacante = await otroNavegador.newPage();
  await ingresar(paginaAtacante, cuentas.operador());
  await expect(paginaAtacante).toHaveURL(/personal\/inicio\.xhtml/);
  const delAtacante = await capturarConfirmar(paginaAtacante);
  await otroNavegador.close();
  expect(delAtacante.cuerpo.get(TOKEN)).not.toBe(legitimo.cuerpo.get(TOKEN));

  // Reenvía el POST con la cookie de la víctima (page.request la manda sola) y el token que se indique.
  const cabeceras = { 'content-type': legitimo.cabeceras['content-type'] };
  if (legitimo.cabeceras['faces-request']) cabeceras['faces-request'] = legitimo.cabeceras['faces-request'];
  async function reenviar(token) {
    const cuerpo = new URLSearchParams(legitimo.cuerpo);
    if (token === null) cuerpo.delete(TOKEN); else cuerpo.set(TOKEN, token);
    return page.request.post(legitimo.url, { headers: cabeceras, data: cuerpo.toString(), maxRedirects: 0 });
  }

  const ataques = [
    ['sin token', null],
    ['con un token inventado', '-1234567890123456789:9876543210987654321'],
    ['con el token de la sesión del atacante', delAtacante.cuerpo.get(TOKEN)],
  ];
  for (const [nombre, token] of ataques) {
    const respuesta = await reenviar(token);
    expect(respuesta.status(), `${nombre}: no es un error del servidor`).toBeLessThan(500);
    expect(await estado(), `${nombre}: el pedido no se confirma`).toBe('PENDIENTE');
  }

  // Control: el mismo POST, con el token de la propia vista, sí confirma.
  await reenviar(legitimo.cuerpo.get(TOKEN));
  await expect.poll(estado, { message: 'con el token correcto el pedido se confirma' }).toBe('CONFIRMADO');
});
