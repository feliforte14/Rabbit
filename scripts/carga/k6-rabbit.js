// =====================================================================
// Prueba de carga de Rabbit (eje C): 50 usuarios virtuales durante 5 minutos
// sobre tres endpoints, con p95 < 500 ms y menos de 1% de errores.
//
//   k6 run -e ERP_USUARIO=demo.erp -e ERP_CLAVE=... -e ITEM=<id> scripts/carga/k6-rabbit.js
//
// Opcionales: -e BASE=https://localhost:8443/Rabbit -e VUS=50 -e DURACION=5m
// Contra el compose (base local), no contra Supabase: la latencia a la base
// remota se come el p95.
//
// Endpoints:
//   1. POST /api/v1/pedidos-externos        alta del ERP (escritura + cola JMS)
//   2. GET  /api/v1/pedidos-externos/{id}   consulta del ERP
//   3. GET  /api/v1/seguimiento/{codigo}    seguimiento público, sin login
// =====================================================================
import http from 'k6/http';
import encoding from 'k6/encoding';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'https://localhost:8443/Rabbit';
const VUS = Number(__ENV.VUS || 50);
const DURACION = __ENV.DURACION || '5m';
const ITEM = Number(__ENV.ITEM);
const AUTH = 'Basic ' + encoding.b64encode(`${__ENV.ERP_USUARIO}:${__ENV.ERP_CLAVE}`);

const alta = new Trend('rabbit_alta_ms', true);
const consulta = new Trend('rabbit_consulta_ms', true);
const seguimiento = new Trend('rabbit_seguimiento_ms', true);

export const options = {
  insecureSkipTLSVerify: true, // certificado autofirmado de WildFly en local
  scenarios: {
    carga: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: VUS },
        { duration: DURACION, target: VUS },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    rabbit_alta_ms: ['p(95)<500'],
    rabbit_consulta_ms: ['p(95)<500'],
    rabbit_seguimiento_ms: ['p(95)<500'],
  },
};

export function setup() {
  if (!__ENV.ERP_USUARIO || !__ENV.ERP_CLAVE || !ITEM) {
    throw new Error('Faltan -e ERP_USUARIO, -e ERP_CLAVE o -e ITEM');
  }
  // Un pedido de referencia, para tener un código de seguimiento real.
  const r = registrar();
  if (r.status !== 201) throw new Error(`El alta de referencia respondió ${r.status}: ${r.body}`);
  const id = r.json('idPedidoExterno');
  for (let i = 0; i < 20; i++) {
    const c = http.get(`${BASE}/api/v1/pedidos-externos/${id}`, { headers: { Authorization: AUTH } });
    const codigo = c.json('codigoSeguimiento');
    if (codigo) return { idReferencia: id, codigo };
    sleep(1);
  }
  throw new Error('El pedido de referencia no se sincronizó en 20 s (¿cola JMS?)');
}

function registrar() {
  return http.post(
    `${BASE}/api/v1/pedidos-externos`,
    JSON.stringify({
      origen: 'STOCK_CONSIGNADO',
      lineas: [{ idItem: ITEM, cantidad: 1 }],
      importe: 2500,
      medioPago: 'CONTRA_ENTREGA',
      direccionEntrega: 'Av. Corrientes 1234, CABA',
      codigoPostalEntrega: '1043',
      emailDestinatario: 'carga@demo.test',
    }),
    {
      headers: {
        Authorization: AUTH,
        'Content-Type': 'application/json',
        'Idempotency-Key': `k6-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      },
      tags: { endpoint: 'alta' },
    },
  );
}

export default function (datos) {
  // Mezcla de lectura y escritura: 1 alta cada 5 iteraciones, el resto consultas.
  if (__ITER % 5 === 0) {
    const r = registrar();
    alta.add(r.timings.duration);
    check(r, { 'alta: 201': (x) => x.status === 201 });
  }

  const c = http.get(`${BASE}/api/v1/pedidos-externos/${datos.idReferencia}`, {
    headers: { Authorization: AUTH },
    tags: { endpoint: 'consulta' },
  });
  consulta.add(c.timings.duration);
  check(c, { 'consulta: 200': (x) => x.status === 200 });

  const s = http.get(`${BASE}/api/v1/seguimiento/${datos.codigo}`, { tags: { endpoint: 'seguimiento' } });
  seguimiento.add(s.timings.duration);
  check(s, { 'seguimiento: 200': (x) => x.status === 200 });

  sleep(1);
}

export function handleSummary(datos) {
  const m = (n) => datos.metrics[n] && datos.metrics[n].values;
  const fila = (nombre, n) => {
    const v = m(n);
    return v ? `| ${nombre} | ${v.med.toFixed(0)} | ${v['p(95)'].toFixed(0)} | ${v.max.toFixed(0)} |` : `| ${nombre} | — | — | — |`;
  };
  const req = m('http_reqs');
  const err = m('http_req_failed');
  const md = [
    `### Carga (k6): ${VUS} usuarios, ${DURACION}`,
    '',
    '| Endpoint | Mediana (ms) | p95 (ms) | Máx (ms) |',
    '|---|---|---|---|',
    fila('Alta del ERP (POST)', 'rabbit_alta_ms'),
    fila('Consulta del ERP (GET)', 'rabbit_consulta_ms'),
    fila('Seguimiento público (GET)', 'rabbit_seguimiento_ms'),
    fila('Todos', 'http_req_duration'),
    '',
    `Requests: ${req ? req.count : 0} (${req ? req.rate.toFixed(1) : 0}/s) · errores: ${err ? (100 * err.rate).toFixed(2) : 0}%`,
    '',
  ].join('\n');
  return {
    stdout: md + '\n',
    'target/k6/resumen.md': md,
    'target/k6/resumen.json': JSON.stringify(datos, null, 2),
  };
}
