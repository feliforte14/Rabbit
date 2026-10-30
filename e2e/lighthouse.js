// Lighthouse (accesibilidad y buenas prácticas) sobre las pantallas principales,
// también las que piden login: Playwright entra y Lighthouse usa esa cookie.
//
//   BASE=... ADMIN_USER=... ADMIN_PASSWORD=... DEMO_PASSWORD=... node lighthouse.js
//
// Falla si alguna pantalla queda debajo de MINIMO (90 por defecto) en accesibilidad.
const { chromium } = require('@playwright/test');
const { execFileSync } = require('child_process');
const fs = require('fs');
const { cuentas, ingresar } = require('./tests/ayuda');

const BASE = (process.env.BASE || 'https://localhost:8443/Rabbit') + '/';
const MINIMO = Number(process.env.MINIMO || 90);
const SALIDA = '../target/lighthouse';

const PANTALLAS = [
  { ruta: 'login', cuenta: null },
  { ruta: 'seguimiento', cuenta: null },
  { ruta: 'personal/inicio', cuenta: 'admin' },
  { ruta: 'personal/pedidos', cuenta: 'admin' },
  { ruta: 'comercio/mis-pedidos', cuenta: 'comercio' },
];

async function cookieDe(cuenta) {
  const navegador = await chromium.launch();
  const contexto = await navegador.newContext({ baseURL: BASE, ignoreHTTPSErrors: true });
  const page = await contexto.newPage();
  await ingresar(page, cuentas[cuenta]());
  await page.waitForURL((u) => !u.pathname.endsWith('login.xhtml'));
  const cookies = await contexto.cookies();
  await navegador.close();
  return cookies.map((c) => `${c.name}=${c.value}`).join('; ');
}

(async () => {
  fs.mkdirSync(SALIDA, { recursive: true });
  const sesiones = {};
  const filas = [];
  let debajo = 0;
  for (const { ruta, cuenta } of PANTALLAS) {
    if (cuenta && !sesiones[cuenta]) sesiones[cuenta] = await cookieDe(cuenta);
    const archivo = `${SALIDA}/${ruta.replace(/\//g, '_')}.json`;
    const args = [
      '--yes', 'lighthouse@12', `${BASE}${ruta}.xhtml`,
      '--only-categories=accessibility,best-practices',
      '--chrome-flags=--headless=new --ignore-certificate-errors --no-sandbox',
      '--output=json', `--output-path=${archivo}`, '--quiet',
    ];
    if (cuenta) args.push(`--extra-headers=${JSON.stringify({ Cookie: sesiones[cuenta] })}`);
    execFileSync('npx', args, { stdio: ['ignore', 'ignore', 'inherit'] });
    const r = JSON.parse(fs.readFileSync(archivo, 'utf8'));
    if (!r.finalDisplayedUrl.includes(ruta)) throw new Error(`${ruta}: Lighthouse terminó en ${r.finalDisplayedUrl} (¿sin sesión?)`);
    const acc = Math.round(r.categories.accessibility.score * 100);
    const bp = Math.round(r.categories['best-practices'].score * 100);
    const fallan = Object.entries(r.audits)
      .filter(([, a]) => a.score === 0 && a.scoreDisplayMode === 'binary')
      .map(([id]) => id);
    if (acc < MINIMO) debajo++;
    filas.push(`| ${ruta} | ${acc}${acc < MINIMO ? ' ❌' : ''} | ${bp} | ${fallan.join(', ') || '—'} |`);
  }
  const md = [
    `### Lighthouse (mínimo de accesibilidad: ${MINIMO})`, '',
    '| Pantalla | Accesibilidad | Buenas prácticas | Auditorías que fallan |', '|---|---|---|---|',
    ...filas, '',
  ].join('\n');
  fs.writeFileSync(`${SALIDA}/resumen.md`, md);
  console.log(md);
  process.exit(debajo ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
