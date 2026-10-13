#!/usr/bin/env python3
"""
Transportista moderno, como servicio APARTE de Rabbit (otra empresa).

El mismo contrato REST que el simulado dentro del WAR
(integracion.transportistas.simulador.TransportistaRestSimuladoResource):

  POST   /cotizaciones        -> 200 {"precio", "plazoHoras"} | 422 {"error"}
  POST   /envios              -> 201 {"codigoSeguimiento"}     | 422 {"error"}
  GET    /envios/{codigo}     -> 200 {"codigoSeguimiento", "estado"} | 404
  DELETE /envios/{codigo}     -> 204 | 404 | 409 (ya entregado)

Mismas reglas: rechaza más de 50 bultos; cotiza $2.500 + $350 por bulto
(+$500 si hay que cobrar al entregar), entrega en 24 h; un envío avanza
solo con el tiempo (SOLICITADO -> EN_TRANSITO -> ENTREGADO).

Rabbit sigue el estado de cada envío consultándolo (polling cada 15 s).

Variables: PUERTO (8095), PASO_SEGUNDOS (20, cuánto dura cada estado).

Solo biblioteca estándar de Python 3: no hace falta instalar nada.
    python3 transportista-moderno/servidor.py
"""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PUERTO = int(os.environ.get("PUERTO", "8095"))
PASO = max(1, int(os.environ.get("PASO_SEGUNDOS", "20")))
MAX_BULTOS = 50

envios = {}            # codigo -> {"referencia", "creado", "cancelado"}
candado = threading.Lock()
prefijo = "TM-" + format(int(time.time()), "X")[-5:] + "-"
secuencia = 0


def log(*partes):
    print(time.strftime("%H:%M:%S"), "[Transportista moderno]", *partes, flush=True)


def estado(envio):
    if envio["cancelado"]:
        return "CANCELADO"
    pasos = int((time.time() - envio["creado"]) // PASO)
    return ("SOLICITADO", "EN_TRANSITO")[pasos] if pasos < 2 else "ENTREGADO"


def motivo_de_rechazo(bultos):
    if bultos < 1:
        return "El envío no tiene bultos"
    if bultos > MAX_BULTOS:
        return f"Excede la capacidad del vehículo ({MAX_BULTOS} bultos)"
    return None


class Api(BaseHTTPRequestHandler):
    def responder(self, codigo, cuerpo=None):
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else b""
        self.send_response(codigo)
        if cuerpo is not None:
            self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def leer(self):
        try:
            datos = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"null")
            return datos if isinstance(datos, dict) else None
        except ValueError:
            return None

    def do_POST(self):
        global secuencia
        pedido = self.leer()
        if pedido is None:
            return self.responder(400, {"error": "Cuerpo JSON inválido"})
        bultos = pedido.get("bultos", 0) if isinstance(pedido.get("bultos"), int) else 0
        motivo = motivo_de_rechazo(bultos)
        if self.path.rstrip("/").endswith("/cotizaciones"):
            if motivo:
                return self.responder(422, {"error": motivo})
            precio = 2500 + 350 * bultos + (500 if "cobrarAlEntregar" in pedido else 0)
            return self.responder(200, {"precio": precio, "plazoHoras": 24})
        if self.path.rstrip("/").endswith("/envios"):
            if motivo:
                return self.responder(422, {"error": motivo})
            with candado:
                secuencia += 1
                codigo = f"{prefijo}{secuencia}"
                envios[codigo] = {"referencia": pedido.get("referencia", "?"), "creado": time.time(),
                                  "cancelado": False}
            log("envío", codigo, "tomado:", pedido.get("referencia"))
            return self.responder(201, {"codigoSeguimiento": codigo})
        self.responder(404, {"error": "Recurso inexistente"})

    def do_GET(self):
        codigo = self.path.rstrip("/").rsplit("/", 1)[-1]
        envio = envios.get(codigo) if "/envios/" in self.path else None
        if envio is None:
            return self.responder(404, {"error": "Envío inexistente"})
        self.responder(200, {"codigoSeguimiento": codigo, "estado": estado(envio)})

    def do_DELETE(self):
        codigo = self.path.rstrip("/").rsplit("/", 1)[-1]
        with candado:
            envio = envios.get(codigo) if "/envios/" in self.path else None
            if envio is None:
                return self.responder(404, {"error": "Envío inexistente"})
            if estado(envio) == "ENTREGADO":
                return self.responder(409, {"error": "El envío ya fue entregado: no se puede cancelar"})
            envio["cancelado"] = True
        log("envío", codigo, "cancelado")
        self.responder(204)

    def log_message(self, *argumentos):
        pass


if __name__ == "__main__":
    log(f"escuchando en http://localhost:{PUERTO} (paso de {PASO} s)")
    ThreadingHTTPServer(("0.0.0.0", PUERTO), Api).serve_forever()
