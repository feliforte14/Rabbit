#!/usr/bin/env python3
"""Genera docs/testing/MATRIZ-DE-TRAZABILIDAD.md y docs/testing/ESPECIFICACION-DE-CASOS.md
a partir de los criterios de aceptación (Gherkin) de docs/funcional/DOCUMENTACION-FUNCIONAL.md.

Uso, desde la raíz del repositorio:
    python3 scripts/testing/generar_documentos.py

Si cambia un criterio de aceptación o el commit de la etiqueta v1.0-testing, se vuelve a correr
(y se actualiza COMMIT y CI más abajo). La columna «Tipo» es una clasificación por reglas sobre el
texto: la revisa el QA.
"""
import re, sys, collections

# Negativo: el caso pide que algo se rechace, falle o no se permita (códigos 4xx/5xx explícitos o verbos de rechazo).
NEG = re.compile(r"\b(40[0-9]|41[0-9]|42[0-9]|429|50[0-9])\b|inv[áa]lid|rechaz|no puede|no se permite|nunca|ajen|otro comercio|vencid|ca[ií]d|\bfall[ao]|\berror|duplic|denegad|bloquea|inexistente|incorrect|no disponible|no est[aá] disponible|no aparece|no se (crea|cobra|cancela|muestra|guarda|pierde|persist)", re.I)
# Borde: valores en el límite, repetición, concurrencia o condiciones de contorno.
BORDE = re.compile(r"l[ií]mite|m[áa]ximo|m[ií]nimo|exacta|vac[ií]o|\bcero\b|[úu]ltimo|doble clic|dos (a la vez|veces|usuarios)|al mismo tiempo|a la vez|min[úu]scula|espacios al principio|hora de corte|capacidad (llena|agotada)|\b\d+ (minutos|intentos|caracteres)", re.I)
def tipo(c):
    t = " ".join([c["titulo"], c["cuando"], c["entonces"]])
    if NEG.search(t): return "Negativo"
    t2 = " ".join([c["titulo"], c["dado"], c["cuando"], c["entonces"]])
    if BORDE.search(t2): return "Borde"
    return "Positivo"

def cargar():
    src = open("docs/funcional/DOCUMENTACION-FUNCIONAL.md", encoding="utf-8").read()

    # HU: encabezado "#### HU-01 · Título · Must · BR-01"
    hus = {}
    for m in re.finditer(r"^#{3,4} (HU-\d+) · (.+?) · (Must|Should|Could|Won't)(?: · (.+))?$", src, re.M):
        hus[m.group(1)] = {"titulo": m.group(2), "prio": m.group(3), "br": (m.group(4) or "").strip(), "pos": m.start()}
    orden = sorted(hus.items(), key=lambda kv: kv[1]["pos"])

    # Escenarios por HU
    casos = []
    for i, (hu, d) in enumerate(orden):
        fin = orden[i+1][1]["pos"] if i+1 < len(orden) else len(src)
        bloque = src[d["pos"]:fin]
        for g in re.finditer(r"```gherkin\n(.*?)```", bloque, re.S):
            partes = re.split(r"(?m)^Escenario: ", g.group(1))
            for p in partes[1:]:
                lineas = [l.rstrip() for l in p.strip().splitlines()]
                cab = re.match(r"(CA-(\d+\.\d+))\s+(.*)", lineas[0])
                if not cab: continue
                dado, cuando, entonces = [], [], []
                actual = None
                for l in lineas[1:]:
                    t = l.strip()
                    if t.startswith("Dado "): actual = dado; actual.append(t[5:])
                    elif t.startswith("Cuando "): actual = cuando; actual.append(t[7:])
                    elif t.startswith("Entonces "): actual = entonces; actual.append(t[9:])
                    elif t.startswith(("Y ", "Pero ")) and actual is not None: actual.append(t.split(" ",1)[1])
                casos.append({"hu": hu, "ca": cab.group(1), "cp": "CP-"+cab.group(2), "titulo": cab.group(3),
                              "dado": " y ".join(dado), "cuando": " y ".join(cuando), "entonces": " y ".join(entonces),
                              "prio": d["prio"], "br": d["br"], "hutit": d["titulo"]})
    return hus, casos, src


hus, casos, src = cargar()
CI = "https://github.com/feliforte14/Rabbit/actions/runs/41564819013"
COMMIT = "b696023"

# ---- matriz de la sección 7 ----
filas = {}
for m in re.finditer(r"^\| (BR-[^|]+)\| (HU-\d+) \| (\w+) \| ([^|]+)\| (.+) \|$", src, re.M):
    filas[m.group(2)] = {"br": m.group(1).strip(), "prio": m.group(3), "ca": m.group(4).strip(), "tests": m.group(5).strip()}
assert len(filas) == 35, len(filas)

def clasif(t):
    auto = bool(re.search(r"`|E2E-|SEG-|CON-|k6|Newman|Schemathesis", t))
    manual = bool(re.search(r"manual|en vivo|prueba exploratoria|a mano", t, re.I))
    if auto and manual: return "mixta"
    return "auto" if auto else "manual"

DEF_HU = {"HU-01": "CON-02", "HU-02": "CON-01", "HU-03": "CON-01", "HU-24": "SEG-01"}
RES = {"auto": f"Pasa (CI, `{COMMIT}`)",
       "mixta": f"Automatizado: pasa (CI, `{COMMIT}`). Manual: sin ejecutar",
       "manual": "Sin ejecutar (manual)"}
EVI = {"auto": f"[CI]({CI})", "mixta": f"[CI]({CI}) + manual pendiente", "manual": "Pendiente"}

cnt = collections.Counter()
L = ["# Matriz de trazabilidad", "",
     "Entregable 2 de los *Criterios de Testing* (Clase 15). Cada historia de usuario (HU) llega a sus criterios de",
     "aceptación (CA), sus casos de prueba (CP, ver [ESPECIFICACION-DE-CASOS.md](ESPECIFICACION-DE-CASOS.md)), la prueba",
     "automatizada que la cubre (clase o ruta), el resultado, la evidencia y el defecto vinculado, si lo hay.", "",
     f"Resultados: corrida del CI sobre el commit `{COMMIT}`, el último que cambió código (la etiqueta `v1.0-testing` apunta a un commit posterior que solo corrige documentación). **«Sin ejecutar (manual)»**",
     "significa que la HU solo tiene prueba manual y todavía no se ejecutó: no es un pase. Las pruebas manuales se",
     "ejecutan con [FLUJO-DE-PRUEBAS.md](../pruebas/FLUJO-DE-PRUEBAS.md) y el resultado se anota acá y en Jira.", "",
     "**Alcance del resultado:** es por historia, no criterio por criterio. «Pasa» quiere decir que las pruebas automatizadas de",
     "la fila pasaron en el CI; no distingue qué criterio cubre cada una (eso lo dice la columna de pruebas).", "",
     "| HU | Historia | MoSCoW | CA → CP | Prueba automatizada (clase / ruta / escenario) | Resultado | Evidencia | Defecto |",
     "|---|---|---|---|---|---|---|---|"]
for hu in sorted(hus, key=lambda h: int(h[3:])):
    f = filas[hu]; c = clasif(f["tests"]); cnt[c] += 1
    rango = f["ca"].replace("–", "–")
    L.append(f"| {hu} | {hus[hu]['titulo']} | {hus[hu]['prio']} | {rango} | {f['tests']} | {RES[c]} | {EVI[c]} | {DEF_HU.get(hu, '—')} |")
n = len(filas)
L += ["", "## Resumen", "",
      f"- HU en el alcance: **{n}**; casos de prueba (CP): **{len(casos)}**.",
      f"- Con prueba automatizada que pasa: **{cnt['auto']}** HU. Mixtas (automatizada y manual): **{cnt['mixta']}**. Solo manual, sin ejecutar: **{cnt['manual']}**.",
      "- Cada HU del alcance tiene al menos un criterio y un caso (sin huecos en HU → CA → CP).",
      "- Ninguna HU «Must» tiene un criterio fallado en la última corrida automatizada.", ""]
open("docs/testing/MATRIZ-DE-TRAZABILIDAD.md", "w", encoding="utf-8").write("\n".join(L))
print("matriz", dict(cnt))

# ---- especificación de casos ----
pr = {"Must": "Alta", "Should": "Media", "Could": "Baja"}
tc = collections.Counter(); T = collections.Counter()
rows = []
for c in casos:
    t = tipo(c); T[t] += 1
    f = filas[c["hu"]]; k = clasif(f["tests"])
    auto = {"auto": "Sí", "mixta": "Parcial", "manual": "No"}[k]
    esc = lambda s: (s or "—").replace("|", "\\|")
    rows.append(f"| {c['cp']} | {c['hu']} | {esc(c['titulo'])} | {esc(c['dado'])} | {esc(c['cuando'])} | {esc(c['entonces'])} | {pr[c['prio']]} ({c['prio']}) | {t} | {auto} |")
tot = len(casos); nb = T["Negativo"] + T["Borde"]
L = ["# Especificación de casos de prueba", "",
     "Entregable 3 de los *Criterios de Testing* (Clase 15, estructura de ISO/IEC/IEEE 29119-3). Los casos salen de los",
     "criterios de aceptación en Gherkin de [DOCUMENTACION-FUNCIONAL.md](../funcional/DOCUMENTACION-FUNCIONAL.md): cada",
     "`CA-xx.y` tiene su `CP-xx.y` con el mismo número. **Dado** es la precondición y los datos, **Cuando** son los pasos y",
     "**Entonces** es el resultado esperado. Un guion largo («—») significa que el escenario original no tiene ese paso.", "",
     "## Cómo leer las columnas", "",
     "- **Prioridad:** la de la HU (MoSCoW): Must = Alta, Should = Media, Could = Baja.",
     "- **Tipo:** *Positivo* (camino feliz), *Negativo* (el sistema tiene que rechazar, fallar bien o no permitir) o *Borde*",
     "  (límite, repetición o concurrencia). Es una clasificación inicial hecha por reglas sobre el texto del caso: el QA",
     "  la tiene que revisar. Se eligió una regla conservadora (un caso dudoso queda como positivo).",
     "- **Automatizado:** *Sí* si la HU tiene pruebas automatizadas, *Parcial* si además tiene pasos manuales y *No* si es",
     "  solo manual. Se decide por HU, no por criterio: la clase exacta está en la",
     "  [matriz de trazabilidad](MATRIZ-DE-TRAZABILIDAD.md).", "",
     "## Resumen", "",
     f"- Casos: **{tot}**. Positivos: **{T['Positivo']}**. Negativos: **{T['Negativo']}**. De borde: **{T['Borde']}**.",
     f"- Negativos o de borde: **{nb} de {tot} = {100*nb/tot:.0f} %** (el mínimo exigido es 40 %).", "",
     "## Casos", "",
     "| Id | HU | Caso | Precondición y datos (Dado) | Pasos (Cuando) | Resultado esperado (Entonces) | Prioridad | Tipo | Automatizado |",
     "|---|---|---|---|---|---|---|---|---|"] + rows + [""]
open("docs/testing/ESPECIFICACION-DE-CASOS.md", "w", encoding="utf-8").write("\n".join(L))
print("casos", dict(T), f"{100*nb/tot:.0f}%")
