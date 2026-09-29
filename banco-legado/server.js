/*
 * BANCO LEGADO SIMULADO — implementación en Node.js.
 *
 * Es el sistema externo con el que Rabbit cobra los pedidos PREPAGO por
 * SOAP. Implementa el mismo contrato (banco.wsdl) que el banco simulado en
 * Java que vive dentro del WAR de Rabbit (BancoLegadoServiceImpl): Rabbit no
 * sabe ni le importa en qué tecnología está el banco, solo habla el WSDL.
 * Es el desafío de heterogeneidad tecnológica (docs/DESAFIOS-OPCIONALES.md).
 *
 * Reglas (las mismas que el banco en Java):
 *   - autorizarPago: un pago de más de $500.000 se rechaza con un
 *     soap:Fault PagoRechazado (con el motivo); cualquier otro se autoriza
 *     y devuelve un código AUT-n.
 *   - reversarPago: devuelve la plata de una autorización. Idempotente.
 *   - Los movimientos viven en memoria: se pierden al reiniciar.
 *
 * Caída simulada, para mostrar el circuit breaker de Rabbit: con la caída
 * prendida, cada llamada tarda 10 s (más que el timeout de 5 s de Rabbit)
 * y termina en error sin procesar nada. Se prende y apaga en caliente:
 *   curl -X POST http://localhost:8090/admin/caida?activa=true
 *   curl -X POST http://localhost:8090/admin/caida?activa=false
 *
 * Uso: npm install && npm start   (puerto 8090; PUERTO=... para cambiarlo)
 */

const fs = require('fs');
const http = require('http');
const path = require('path');
const soap = require('soap');

const PUERTO = Number(process.env.PUERTO || 8090);
const LIMITE = 500000;
const DEMORA_CAIDA_MS = 10000;
const RUTA_SERVICIO = '/BancoLegadoService';

let secuencia = 0;
let caida = false;
// código de autorización -> { referencia, importe, estado: 'AUTORIZADO' | 'REVERSADO' }
const movimientos = new Map();

function log(mensaje) {
    console.log(`${new Date().toISOString()} [Banco legado Node] ${mensaje}`);
}

// Error SOAP de negocio: node-soap lo convierte en un soap:Fault con el
// detalle tipado del WSDL (PagoRechazado), que el cliente JAX-WS de Rabbit
// traduce a PagoRechazadoException.
function rechazo(motivo) {
    return {
        Fault: {
            faultcode: 'soap:Server',
            faultstring: motivo,
            detail: { 'tns:PagoRechazado': { motivo } },
            statusCode: 500,
        },
    };
}

async function simularCaidaSiCorresponde() {
    if (!caida) {
        return;
    }
    log(`Caída simulada: se demora ${DEMORA_CAIDA_MS} ms y no procesa nada`);
    await new Promise((resolver) => setTimeout(resolver, DEMORA_CAIDA_MS));
    throw { Fault: { faultcode: 'soap:Server', faultstring: 'Banco fuera de servicio (caída simulada)', statusCode: 500 } };
}

const servicio = {
    BancoLegadoService: {
        BancoLegadoPort: {
            async autorizarPago({ referencia, importe }) {
                await simularCaidaSiCorresponde();
                const monto = Number(importe);
                if (importe === undefined || Number.isNaN(monto) || monto > LIMITE) {
                    const motivo = `El importe supera el límite de $${LIMITE}`;
                    log(`Rechazado el pago de ${referencia}: ${motivo}`);
                    throw rechazo(motivo);
                }
                const codigo = `AUT-${++secuencia}`;
                movimientos.set(codigo, { referencia, importe: monto, estado: 'AUTORIZADO' });
                log(`Autorizado ${codigo}: ${referencia} $${monto.toFixed(2)}`);
                return { codigoAutorizacion: codigo };
            },

            async reversarPago({ codigoAutorizacion }) {
                await simularCaidaSiCorresponde();
                const movimiento = movimientos.get(codigoAutorizacion);
                if (movimiento && movimiento.estado === 'AUTORIZADO') {
                    movimiento.estado = 'REVERSADO';
                    log(`Reversado ${codigoAutorizacion} (${movimiento.referencia} $${movimiento.importe.toFixed(2)})`);
                }
                return {};
            },
        },
    },
};

// Todo lo que no es el servicio SOAP: el interruptor de la caída simulada.
const servidor = http.createServer((pedido, respuesta) => {
    const url = new URL(pedido.url, `http://localhost:${PUERTO}`);
    if (pedido.method === 'POST' && url.pathname === '/admin/caida') {
        caida = url.searchParams.get('activa') === 'true';
        log(`Caída simulada ${caida ? 'PRENDIDA' : 'apagada'}`);
        respuesta.writeHead(200, { 'Content-Type': 'application/json' });
        respuesta.end(JSON.stringify({ caida }));
        return;
    }
    respuesta.writeHead(404);
    respuesta.end();
});

const wsdl = fs.readFileSync(path.join(__dirname, 'banco.wsdl'), 'utf8');
servidor.listen(PUERTO, () => {
    soap.listen(servidor, RUTA_SERVICIO, servicio, wsdl, () => {
        log(`Escuchando en http://localhost:${PUERTO}${RUTA_SERVICIO} (WSDL en ?wsdl)`);
    });
});
