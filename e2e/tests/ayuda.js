// Utilidades compartidas: datos del entorno, login por la pantalla y alta de
// pedidos por la API del ERP (lo mismo que haría el sistema del comercio).
const { expect } = require('@playwright/test');

const env = (nombre) => {
  const v = process.env[nombre];
  if (!v) throw new Error(`Falta la variable de entorno ${nombre}`);
  return v;
};

const cuentas = {
  admin: () => ({ usuario: env('ADMIN_USER'), clave: env('ADMIN_PASSWORD') }),
  comercio: () => ({ usuario: 'demo.comercio', clave: env('DEMO_PASSWORD') }),   // Almacén Demo 1
  comercio2: () => ({ usuario: 'demo.comercio2', clave: env('DEMO_PASSWORD') }), // Almacén Demo 2
  erp: () => ({ usuario: 'demo.erp', clave: env('DEMO_PASSWORD') }),             // ERP de Almacén Demo 1
};

async function ingresar(page, { usuario, clave }) {
  await page.goto('login.xhtml');
  await page.locator('#formLogin\\:username').fill(usuario);
  await page.locator('#formLogin\\:password').fill(clave);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

// Alta por la API del ERP y espera a que la cola JMS lo convierta en pedido.
async function pedidoDelErp(request, extra = {}) {
  const { usuario, clave } = cuentas.erp();
  const auth = 'Basic ' + Buffer.from(`${usuario}:${clave}`).toString('base64');
  const alta = await request.post('api/v1/pedidos-externos', {
    headers: { Authorization: auth, 'Idempotency-Key': `e2e-${Date.now()}-${Math.random().toString(36).slice(2)}` },
    data: {
      origen: 'STOCK_CONSIGNADO',
      lineas: [{ idItem: Number(env('ITEM')), cantidad: 1 }],
      importe: 2500,
      medioPago: 'CONTRA_ENTREGA',
      direccionEntrega: 'Av. Corrientes 1234, CABA',
      codigoPostalEntrega: '1043',
      emailDestinatario: 'e2e@demo.test',
      ...extra,
    },
  });
  expect(alta.status(), await alta.text()).toBe(201);
  const { idPedidoExterno } = await alta.json();
  let pedido;
  await expect.poll(async () => {
    const r = await request.get(`api/v1/pedidos-externos/${idPedidoExterno}`, { headers: { Authorization: auth } });
    pedido = await r.json();
    return pedido.resultado;
  }, { timeout: 30_000 }).toBe('Sincronizado');
  return pedido; // { idPedido, codigoSeguimiento, estadoPedido, ... }
}

module.exports = { cuentas, ingresar, pedidoDelErp, env };
