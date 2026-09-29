"""Prueba de escalabilidad del consumidor de cola.pedidos.externos.

Mide cuánto tarda Rabbit en sincronizar una carga de pedidos del ERP con
distinta cantidad de consumidores en paralelo (ver
docs/DESAFIOS-OPCIONALES.md, sección 2).

Requisitos:
  - Rabbit desplegado en un WildFly local, con jboss-cli accesible.
  - Un comercio activo con un punto de picking activo (los pedidos de la
    carga son de origen PUNTO_PICKING: no reservan stock).
  - Un usuario con rol ERP.

Variables de entorno:
  WILDFLY_HOME          instalación de WildFly (para jboss-cli y el log)
  RABBIT_ERP_USUARIO    usuario con rol ERP
  RABBIT_ERP_CLAVE      su contraseña
  RABBIT_ID_COMERCIO    comercio de la carga (por defecto 1)
  RABBIT_ID_PUNTO       punto de picking de la carga (por defecto 1)

Uso:
  python3 scripts/prueba_escalabilidad.py 1 4 8        # consumidores a probar
  python3 scripts/prueba_escalabilidad.py 1 4 8 --pedidos 100

Cada pedido de la carga lleva "[CARGA]" en la dirección de entrega, para
poder identificarlos después. Al terminar, el script vuelve a activar el
timer de respaldo y deja los consumidores en su valor por defecto.
"""
import argparse
import base64
import json
import os
import re
import subprocess
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

BASE = "http://localhost:8080/Rabbit/"
WILDFLY = os.environ["WILDFLY_HOME"]
CLI = os.path.join(WILDFLY, "bin", "jboss-cli.sh")
LOG = os.path.join(WILDFLY, "standalone", "log", "server.log")
COLA = "/subsystem=messaging-activemq/server=default/runtime-queue=jms.queue.cola.pedidos.externos"
USUARIO = os.environ["RABBIT_ERP_USUARIO"]
CLAVE = os.environ["RABBIT_ERP_CLAVE"]
ID_COMERCIO = int(os.environ.get("RABBIT_ID_COMERCIO", "1"))
ID_PUNTO = int(os.environ.get("RABBIT_ID_PUNTO", "1"))

# Pocos hilos al cargar: el pooler de Supabase admite 15 conexiones en total.
HILOS_CARGA = 4
SINCRONIZADO = re.compile(
    r"^(\S+ \S+) INFO .*?\((.*?)\) \[Pedidos\]\[JMS\] Pedido externo (\d+) sincronizado en tiempo real")
DESCARTADO = re.compile(r"\[Pedidos\]\[JMS\] Pedido externo (\d+) descartado por regla de negocio: (.*)")


def cli(comando):
    return subprocess.run([CLI, "--connect", "--command=" + comando], capture_output=True, text=True).stdout


def fijar_propiedad(nombre, valor):
    if '"outcome" => "success"' not in cli(f"/system-property={nombre}:write-attribute(name=value,value={valor})"):
        cli(f"/system-property={nombre}:add(value={valor})")


def quitar_propiedad(nombre):
    cli(f"/system-property={nombre}:remove")


def redesplegar():
    salida = cli("/deployment=Rabbit.war:redeploy")
    if '"outcome" => "success"' not in salida:
        raise RuntimeError("No se pudo redesplegar: " + salida)
    for _ in range(60):
        try:
            if urllib.request.urlopen(BASE + "login.xhtml").status == 200:
                return
        except OSError:
            pass
        time.sleep(1)
    raise RuntimeError("Rabbit no volvió a levantar")


def enviar(etiqueta, i):
    cuerpo = {
        "idComercio": ID_COMERCIO, "origen": "PUNTO_PICKING", "idPuntoPicking": ID_PUNTO,
        "lineas": [{"producto": "[CARGA] Caja", "cantidad": 1}],
        "importe": 100, "medioPago": "CONTRA_ENTREGA",
        "direccionEntrega": f"[CARGA] {etiqueta} #{i}",
    }
    credenciales = base64.b64encode(f"{USUARIO}:{CLAVE}".encode()).decode()
    pedido = urllib.request.Request(
        BASE + "api/pedidos-externos", data=json.dumps(cuerpo).encode(), method="POST",
        headers={"Content-Type": "application/json", "Authorization": "Basic " + credenciales})
    return json.loads(urllib.request.urlopen(pedido).read())["idPedidoExterno"]


def cargar(etiqueta, cantidad):
    with ThreadPoolExecutor(max_workers=HILOS_CARGA) as pool:
        return list(pool.map(lambda i: enviar(etiqueta, i), range(cantidad)))


def esperar(ids, desde_byte, timeout):
    """Hora y hilo en que se sincronizó cada pedido, leídos del log."""
    pendientes, vistos = set(ids), {}
    limite = time.time() + timeout
    while pendientes and time.time() < limite:
        time.sleep(1)
        with open(LOG, errors="ignore") as f:
            f.seek(desde_byte)
            for linea in f:
                m = SINCRONIZADO.match(linea)
                if m and int(m.group(3)) in pendientes:
                    vistos[int(m.group(3))] = (datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S,%f"), m.group(2))
                    pendientes.discard(int(m.group(3)))
                d = DESCARTADO.search(linea)
                if d and int(d.group(1)) in pendientes:
                    raise RuntimeError(f"Pedido {d.group(1)} descartado: {d.group(2)}")
    return vistos, pendientes


def correr(consumidores, cantidad):
    fijar_propiedad("rabbit.cola.consumidores", consumidores)
    redesplegar()

    byte = os.path.getsize(LOG)
    _, faltan = esperar(cargar(f"calentamiento c={consumidores}", 10), byte, timeout=120)
    if faltan:
        raise RuntimeError("El calentamiento no terminó: revisar el log del servidor")

    cli(COLA + ":pause")
    try:
        ids = cargar(f"escalabilidad c={consumidores}", cantidad)
        time.sleep(2)
        byte = os.path.getsize(LOG)
        inicio = datetime.now()
    finally:
        cli(COLA + ":resume")
    vistos, faltan = esperar(ids, byte, timeout=900)
    if faltan:
        raise RuntimeError(f"{len(faltan)} pedidos no se sincronizaron")

    esperas = sorted((hora - inicio).total_seconds() for hora, _ in vistos.values())
    duracion = esperas[-1]
    return {
        "consumidores": consumidores,
        "pedidos": cantidad,
        "duracion_s": round(duracion, 2),
        "pedidos_por_s": round(cantidad / duracion, 2),
        "espera_media_s": round(sum(esperas) / len(esperas), 2),
        "espera_p95_s": round(esperas[int(len(esperas) * 0.95) - 1], 2),
        "hilos": len({hilo for _, hilo in vistos.values()}),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("consumidores", nargs="+", type=int)
    parser.add_argument("--pedidos", type=int, default=100)
    args = parser.parse_args()

    fijar_propiedad("rabbit.sincronizador.pausado", "true")
    resultados = []
    try:
        for c in args.consumidores:
            r = correr(c, args.pedidos)
            print(json.dumps(r, ensure_ascii=False), flush=True)
            resultados.append(r)
    finally:
        quitar_propiedad("rabbit.sincronizador.pausado")
        quitar_propiedad("rabbit.cola.consumidores")
        redesplegar()

    base = resultados[0] if resultados else None
    print("\n| Consumidores | Tiempo total | Pedidos/s | Espera media | Espera p95 | Hilos | Mejora |")
    print("|---|---|---|---|---|---|---|")
    for r in resultados:
        mejora = f"x{r['pedidos_por_s'] / base['pedidos_por_s']:.1f}"
        print(f"| {r['consumidores']} | {r['duracion_s']} s | {r['pedidos_por_s']} | "
              f"{r['espera_media_s']} s | {r['espera_p95_s']} s | {r['hilos']} | {mejora} |")


if __name__ == "__main__":
    main()
