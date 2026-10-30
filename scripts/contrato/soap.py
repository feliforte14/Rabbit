"""Pruebas de contrato de los servicios SOAP de Rabbit (banco y transportista legado).

1. El WSDL que publica la app es igual al versionado en docs/integraciones/wsdl/
   (cambiar el contrato tiene que ser una decisión, no un efecto colateral).
2. Cada operación responde según el WSDL: zeep valida el pedido y la respuesta
   contra el XSD del WSDL, y los errores de negocio llegan como soap:Fault.

    uv run --with zeep scripts/contrato/soap.py [http://localhost:8080/Rabbit]
"""
import difflib
import pathlib
import re
import sys
import urllib.request
from decimal import Decimal

from zeep import Client
from zeep.exceptions import Fault

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/Rabbit").rstrip("/")
VERSIONADOS = pathlib.Path(__file__).resolve().parents[2] / "docs" / "integraciones" / "wsdl"
SERVICIOS = ["BancoLegadoService", "TransportistaLegadoService"]

resultados = []


def caso(nombre, funcion):
    try:
        funcion()
        resultados.append((nombre, True, ""))
    except Exception as e:  # noqa: BLE001 — se reporta cualquier falla del caso
        resultados.append((nombre, False, f"{type(e).__name__}: {e}"))


def normalizar(wsdl):
    # La dirección del servicio depende del host y del puerto donde corre.
    return re.sub(r'location="http://[^"]*/Rabbit/', 'location="http://localhost:8080/Rabbit/', wsdl)


def wsdl_sin_cambios(servicio):
    def prueba():
        vivo = normalizar(urllib.request.urlopen(f"{BASE}/{servicio}?wsdl", timeout=15).read().decode())
        versionado = (VERSIONADOS / f"{servicio}.wsdl").read_text()
        if vivo.strip() != versionado.strip():
            diff = "\n".join(difflib.unified_diff(versionado.splitlines(), vivo.splitlines(), "versionado", "publicado", lineterm="", n=1))
            raise AssertionError(f"el WSDL publicado cambió:\n{diff[:2000]}")
    return prueba


def esperar_fault(llamada):
    try:
        llamada()
    except Fault:
        return
    raise AssertionError("se esperaba un soap:Fault y la operación respondió normal")


def cliente(servicio):
    c = Client(f"{BASE}/{servicio}?wsdl")
    # Los WSDL de CXF anuncian la dirección del host que los sirvió.
    return c.create_service(next(iter(c.wsdl.bindings)), f"{BASE}/{servicio}")


for s in SERVICIOS:
    caso(f"{s}: WSDL igual al versionado", wsdl_sin_cambios(s))

banco = cliente("BancoLegadoService")
estado = {}


def autorizar():
    codigo = banco.autorizarPago(referencia="CONTRATO-1", importe=Decimal("2500.00"))
    assert codigo, "autorizarPago no devolvió código de autorización"
    estado["codigo"] = codigo


caso("autorizarPago aprobado devuelve codigoAutorizacion", autorizar)
caso("autorizarPago mayor a $500.000 es soap:Fault",
     lambda: esperar_fault(lambda: banco.autorizarPago(referencia="CONTRATO-2", importe=Decimal("600000.00"))))
caso("reversarPago de un cobro autorizado responde vacío", lambda: banco.reversarPago(codigoAutorizacion=estado["codigo"]))

transporte = cliente("TransportistaLegadoService")


def registrar():
    codigo = transporte.registrarEnvio(referencia="CONTRATO-3", direccionRetiro="Av. Santa Fe 3200, CABA",
                                       direccionEntrega="Av. Corrientes 1234, CABA", bultos=1)
    assert codigo, "registrarEnvio no devolvió código de seguimiento"
    estado["envio"] = codigo


caso("registrarEnvio devuelve codigoSeguimiento", registrar)
def consultar():
    assert transporte.consultarEnvio(codigoSeguimiento=estado["envio"]), "consultarEnvio devolvió un estado vacío"


caso("consultarEnvio devuelve un estado", consultar)
caso("anularEnvio responde vacío", lambda: transporte.anularEnvio(codigoSeguimiento=estado["envio"]))
caso("registrarEnvio con más de 50 bultos es soap:Fault",
     lambda: esperar_fault(lambda: transporte.registrarEnvio(referencia="CONTRATO-4", direccionRetiro="A",
                                                             direccionEntrega="B", bultos=51)))

fallas = [r for r in resultados if not r[1]]
md = ["### Contrato SOAP (WSDL)", "", "| Caso | Resultado |", "|---|---|"]
md += [f"| {n} | {'✅' if ok else '❌ ' + d.splitlines()[0][:150]} |" for n, ok, d in resultados]
print("\n".join(md))
for n, _, d in fallas:
    print(f"\n--- {n}\n{d}")
salida = pathlib.Path("target/contrato")
salida.mkdir(parents=True, exist_ok=True)
(salida / "soap.md").write_text("\n".join(md) + "\n")
sys.exit(1 if fallas else 0)
