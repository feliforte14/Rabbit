# /// script
# requires-python = ">=3.10"
# dependencies = ["requests==2.32.3"]
# ///
"""
Pruebas de seguridad en vivo contra Rabbit levantado (eje D de los criterios de
testing y matriz SEC de la Clase 11). Son las que se corrieron a mano el
02/11/2026, juntas en un solo script para repetirlas en cualquier máquina.

    # con Rabbit arriba (docker compose up) y las variables del .env cargadas:
    set -a; . ./.env; set +a
    uv run scripts/seguridad/pruebas_vivo.py            # todo
    uv run scripts/seguridad/pruebas_vivo.py cabeceras  # un grupo

Grupos: cabeceras, roles, sesion, fuerza, inyeccion, xss, errores, seguimiento.
Variables: BASE (por defecto https://localhost:8443/Rabbit), DEMO_PASSWORD
(obligatoria), PUNTO (id del punto de picking "Local Palermo"; si falta, el
grupo inyeccion lo busca con la API o se saltea).

No borra nada. Crea pedidos de prueba en el comercio "Almacén Demo 1" (con
"[TEST]" en la dirección) y el grupo "fuerza" deja bloqueado 15 minutos al
usuario inexistente "no-existe-seg" (no a una cuenta real).
Sale con código 1 si algún chequeo falla.
"""
import json
import os
import re
import sys
import time
import uuid

import requests
import urllib3

urllib3.disable_warnings()

BASE = os.environ.get("BASE", "https://localhost:8443/Rabbit").rstrip("/")
HTTP_BASE = BASE.replace("https://", "http://").replace(":8443", ":8080")
PW = os.environ.get("DEMO_PASSWORD")
RESULTADOS = []


def chequeo(grupo, nombre, ok, detalle=""):
    RESULTADOS.append((grupo, nombre, ok, detalle))
    print(f"  [{'OK ' if ok else 'FALLA'}] {nombre}" + (f" · {detalle}" if detalle else ""))


def sesion():
    s = requests.Session()
    s.verify = False
    s.trust_env = False  # localhost: sin proxy ni bundle de certificados del sistema
    return s


def login(usuario, clave):
    s = sesion()
    r = s.get(BASE + "/login.xhtml")
    vs = re.search(r'name="jakarta.faces.ViewState"[^>]*value="([^"]+)"', r.text).group(1)
    boton = re.search(r'name="(formLogin:j_idt\d+)"', r.text).group(1)
    r = s.post(BASE + "/login.xhtml", allow_redirects=False, data={
        "formLogin": "formLogin", "formLogin:username": usuario, "formLogin:password": clave,
        boton: "Ingresar", "jakarta.faces.ViewState": vs})
    return s, r


def erp(usuario="demo.erp"):
    s = sesion()
    s.auth = (usuario, PW)
    return s


# ---------------------------------------------------------------- grupos

def cabeceras():
    print("\n== Cabeceras, cookie, CORS y TLS (SEC-05)")
    s = sesion()
    r = s.get(HTTP_BASE + "/login.xhtml", allow_redirects=False)
    chequeo("cabeceras", "HTTP redirige a HTTPS", r.status_code in (301, 302) and r.headers.get("Location", "").startswith("https://"),
            f"{r.status_code} {r.headers.get('Location', '')}")
    r = s.get(BASE + "/login.xhtml")
    h = {k.lower(): v for k, v in r.headers.items()}
    chequeo("cabeceras", "HSTS", h.get("strict-transport-security", "").startswith("max-age="), h.get("strict-transport-security", "falta"))
    chequeo("cabeceras", "CSP con frame-ancestors 'none'", "frame-ancestors 'none'" in h.get("content-security-policy", ""))
    chequeo("cabeceras", "X-Content-Type-Options: nosniff", h.get("x-content-type-options") == "nosniff")
    chequeo("cabeceras", "X-Frame-Options: DENY", h.get("x-frame-options") == "DENY")
    chequeo("cabeceras", "Sin Server ni X-Powered-By", "x-powered-by" not in h and "server" not in h, h.get("server", ""))
    cookie = h.get("set-cookie", "")
    for bandera in ("secure", "httponly", "samesite=lax"):
        chequeo("cabeceras", f"Cookie de sesión con {bandera}", bandera in cookie.lower(), cookie.split(";")[0][:20] + "…")
    r = s.options(BASE + "/api/v1/pedidos-externos", headers={"Origin": "https://otro-sitio.example",
                  "Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "authorization"})
    chequeo("cabeceras", "CORS: un origen ajeno no recibe permiso", "access-control-allow-origin" not in {k.lower() for k in r.headers})


def roles():
    print("\n== Acceso por rol en las pantallas (BFLA) y en la API (SEC-04)")
    base = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "webapp")
    paginas = []
    for carpeta in ("personal", "comercio", "repartidor"):
        d = os.path.join(base, carpeta)
        if os.path.isdir(d):
            paginas += [f"/{carpeta}/{f}" for f in sorted(os.listdir(d)) if f.endswith(".xhtml")]
    permitido = {"demo.operador": ("/personal/", "/comercio/puntos-picking", "/comercio/visitas"),
                 "demo.comercio": ("/comercio/",), "demo.repartidor": ("/repartidor/",)}
    anon = sesion()
    expuestas = [p for p in paginas if anon.get(BASE + p, allow_redirects=False).status_code == 200]
    chequeo("roles", f"Sin sesión, ninguna de las {len(paginas)} pantallas responde 200", not expuestas, ", ".join(expuestas))
    for usuario, prefijos in permitido.items():
        s, _ = login(usuario, PW)
        indebidas = []
        for p in paginas:
            if p == "/personal/usuarios.xhtml":
                deberia = False  # solo administrador
            else:
                deberia = p.startswith(prefijos)
            if s.get(BASE + p, allow_redirects=False).status_code == 200 and not deberia:
                indebidas.append(p)
        chequeo("roles", f"{usuario} solo entra a lo suyo", not indebidas, ", ".join(indebidas))
    s = sesion()
    s.auth = ("demo.comercio2", PW)
    r = s.get(BASE + "/api/v1/pedidos-externos/1")
    chequeo("roles", "API del ERP con rol COMERCIO → 403", r.status_code == 403, str(r.status_code))
    r = sesion().get(BASE + "/api/v1/pedidos-externos/1")
    chequeo("roles", "API del ERP sin credenciales → 401", r.status_code == 401, str(r.status_code))


def sesion_web():
    print("\n== Sesión: fixation, cookie adulterada, logout (equivalente a JWT)")
    s = sesion()
    s.get(BASE + "/login.xhtml")
    antes = s.cookies.get("JSESSIONID", "")
    s, r = login("demo.operador", PW)
    despues = s.cookies.get("JSESSIONID", "")
    chequeo("sesion", "El id de sesión cambia al iniciar sesión", antes.split(".")[0] != despues.split(".")[0])
    ident, _, ruta = despues.partition(".")
    for nombre, valor in [("inventada", uuid.uuid4().hex), ("con un carácter del id cambiado", ("A" if ident[5] != "A" else "B").join([ident[:5], ident[6:]]) + "." + ruta)]:
        a = sesion()
        a.cookies.set("JSESSIONID", valor, path="/Rabbit")
        r = a.get(BASE + "/personal/inicio.xhtml", allow_redirects=False)
        chequeo("sesion", f"Cookie {nombre} → login", r.status_code == 302 and "login" in r.headers.get("Location", ""), str(r.status_code))
    r = s.get(BASE + "/personal/inicio.xhtml")
    enlace = re.search(r'<a id="([^"]+)"[^>]*title="Cerrar sesión"', r.text)
    vs = re.findall(r'name="jakarta.faces.ViewState"[^>]*value="([^"]+)"', r.text)
    if enlace and vs:
        lid = enlace.group(1)
        s.post(BASE + "/personal/inicio.xhtml", allow_redirects=False,
               data={lid.split(":")[0]: lid.split(":")[0], lid: lid, "jakarta.faces.ViewState": vs[0]})
        a = sesion()
        a.cookies.set("JSESSIONID", despues, path="/Rabbit")
        r = a.get(BASE + "/personal/inicio.xhtml", allow_redirects=False)
        chequeo("sesion", "Después del logout la cookie vieja no entra", r.status_code == 302, str(r.status_code))
    else:
        chequeo("sesion", "Logout", False, "no se encontró el enlace Cerrar sesión")
    for clave in ("Bearer eyJhbGciOiJub25lIn0." + "e30.", "Basic !!!", "Digest x"):
        r = sesion().get(BASE + "/api/v1/pedidos-externos/1", headers={"Authorization": clave})
        chequeo("sesion", f"API con '{clave[:14]}…' → 401", r.status_code == 401, str(r.status_code))


def fuerza():
    print("\n== Fuerza bruta en el login web (SEC-01)")
    usuario = "no-existe-seg"
    codigos = []
    for i in range(6):
        _, r = login(usuario, f"mala-{i}")
        codigos.append(r.status_code)
    chequeo("fuerza", "El 6.º intento responde 429", codigos[-1] == 429, str(codigos))
    r = sesion()
    r.auth = (usuario, "x")
    for _ in range(6):
        res = r.get(BASE + "/api/v1/pedidos-externos/1")
    chequeo("fuerza", "La API del ERP también bloquea con 429", res.status_code == 429, str(res.status_code))


def _punto():
    if os.environ.get("PUNTO"):
        return int(os.environ["PUNTO"])
    return None


def inyeccion():
    print("\n== Inyección SQL / NoSQL / XSS / EL en cada campo de texto (API)")
    punto = _punto()
    if not punto:
        chequeo("inyeccion", "Inyección en campos", True, "salteado: falta PUNTO (id de 'Local Palermo')")
        return
    cargas = ["x' OR '1'='1' --", "x'); DROP TABLE pedidos; --", "\"><img src=x onerror=alert('seg')>",
              "<script>alert('seg')</script>", "{\"$gt\": \"\"}", "#{7*7}${7*7}"]
    campos = ["direccionEntrega", "codigoPostalEntrega", "nombreDestinatario", "telefonoDestinatario", "emailDestinatario", "producto"]
    s = erp()
    errores = []
    for campo in campos:
        for carga in cargas:
            cuerpo = {"origen": "PUNTO_PICKING", "idPuntoPicking": punto,
                      "lineas": [{"producto": "[TEST] Caja", "cantidad": 1}], "importe": 1500,
                      "medioPago": "CONTRA_ENTREGA", "direccionEntrega": "[TEST] Av. Siempreviva 742",
                      "emailDestinatario": "seg@correo.test"}
            if campo == "producto":
                cuerpo["lineas"][0]["producto"] = "[TEST] " + carga
            else:
                cuerpo[campo] = ("[TEST] " + carga) if campo == "direccionEntrega" else carga
            r = s.post(BASE + "/api/v1/pedidos-externos", json=cuerpo, headers={"Idempotency-Key": str(uuid.uuid4())})
            if r.status_code not in (201, 400, 422):
                errores.append(f"{campo}:{r.status_code}")
    chequeo("inyeccion", f"{len(campos) * len(cargas)} combinaciones sin 500", not errores, ", ".join(errores))
    r = s.post(BASE + "/api/v1/pedidos-externos", headers={"Idempotency-Key": str(uuid.uuid4())},
               json={"origen": "PUNTO_PICKING", "idPuntoPicking": punto, "lineas": [{"producto": "[TEST] Caja", "cantidad": 1}],
                     "importe": 1500, "medioPago": "CONTRA_ENTREGA", "direccionEntrega": "[TEST] ok", "emailDestinatario": "seg@correo.test"})
    chequeo("inyeccion", "La base sigue funcionando (otra alta da 201)", r.status_code == 201, str(r.status_code))


def xss():
    print("\n== Lo inyectado se muestra escapado en las pantallas")
    peligroso = [r"<script>alert\('seg'\)", r"<img src=x onerror=alert\('seg'\)"]
    for usuario, paginas in [("demo.operador", ["/personal/pedidos.xhtml", "/personal/ruteo.xhtml", "/personal/entregas.xhtml"]),
                             ("demo.comercio", ["/comercio/mis-pedidos.xhtml"])]:
        s, _ = login(usuario, PW)
        for p in paginas:
            html = s.get(BASE + p).text
            crudo = [x for x in peligroso if re.search(x, html)]
            chequeo("xss", f"{p} ({usuario}) sin HTML crudo", not crudo, ", ".join(crudo))


def errores():
    print("\n== Errores sin detalles internos (SEC-06)")
    fuga = re.compile(r"Exception|\bat (com|org|java|jakarta)\.|org\.jboss|WFLY|Hibernate|PSQLException|\.java:\d+", re.I)
    s = erp()
    casos = [
        ("JSON roto", lambda: s.post(BASE + "/api/v1/pedidos-externos", data="{\"origen\": ", headers={"Content-Type": "application/json", "Idempotency-Key": str(uuid.uuid4())})),
        ("XML", lambda: s.post(BASE + "/api/v1/pedidos-externos", data="<a/>", headers={"Content-Type": "application/xml", "Idempotency-Key": str(uuid.uuid4())})),
        ("número 1e400", lambda: s.post(BASE + "/api/v1/pedidos-externos", data="{\"importe\": 1e400}", headers={"Content-Type": "application/json", "Idempotency-Key": str(uuid.uuid4())})),
        ("método no permitido", lambda: s.delete(BASE + "/api/v1/pedidos-externos/1")),
        ("id enorme", lambda: s.get(BASE + "/api/v1/pedidos-externos/99999999999999999999")),
        ("path traversal", lambda: s.get(BASE + "/api/v1/seguimiento/..%2F..%2Fetc%2Fpasswd")),
        ("WEB-INF", lambda: sesion().get(BASE + "/WEB-INF/web.xml")),
    ]
    for nombre, f in casos:
        r = f()
        m = fuga.search(r.text)
        chequeo("errores", f"{nombre} → {r.status_code} sin detalles", r.status_code < 500 and not m and "<web-app" not in r.text, m.group(0) if m else "")


def seguimiento():
    print("\n== Seguimiento público sin datos personales (SEC-10)")
    punto = _punto()
    s = erp()
    if punto:
        r = s.post(BASE + "/api/v1/pedidos-externos", headers={"Idempotency-Key": str(uuid.uuid4())},
                   json={"origen": "PUNTO_PICKING", "idPuntoPicking": punto, "lineas": [{"producto": "[TEST] Caja", "cantidad": 1}],
                         "importe": 1500, "medioPago": "CONTRA_ENTREGA", "direccionEntrega": "[TEST] Calle Privada 123",
                         "nombreDestinatario": "Nombre Privado", "telefonoDestinatario": "1155550000", "emailDestinatario": "privado@correo.test"})
        loc = r.headers.get("Location")
        cod = None
        for _ in range(30):
            cod = s.get(loc).json().get("codigoSeguimiento")
            if cod:
                break
            time.sleep(1)
        cuerpo = sesion().get(BASE + "/api/v1/seguimiento/" + cod).text
        datos = [d for d in ("Calle Privada", "Nombre Privado", "1155550000", "privado@correo.test") if d in cuerpo]
        chequeo("seguimiento", "La respuesta pública no trae datos de quien recibe", not datos, ", ".join(datos))
    r = sesion().get(BASE + "/api/v1/seguimiento/RB-NOEXISTE00")
    chequeo("seguimiento", "Código inexistente → 404", r.status_code == 404, str(r.status_code))


GRUPOS = {"cabeceras": cabeceras, "roles": roles, "sesion": sesion_web, "fuerza": fuerza,
          "inyeccion": inyeccion, "xss": xss, "errores": errores, "seguimiento": seguimiento}

if __name__ == "__main__":
    if not PW:
        sys.exit("Falta DEMO_PASSWORD (set -a; . ./.env; set +a)")
    elegidos = sys.argv[1:] or list(GRUPOS)
    print(f"Rabbit en {BASE}")
    for g in elegidos:
        GRUPOS[g]()
    fallas = [r for r in RESULTADOS if not r[2]]
    print(f"\n{len(RESULTADOS) - len(fallas)} de {len(RESULTADOS)} chequeos OK")
    with open("resultado-seguridad.json", "w") as f:
        json.dump([{"grupo": g, "chequeo": n, "ok": o, "detalle": d} for g, n, o, d in RESULTADOS], f, ensure_ascii=False, indent=2)
    print("Detalle en resultado-seguridad.json")
    sys.exit(1 if fallas else 0)
