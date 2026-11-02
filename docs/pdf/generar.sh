#!/bin/bash
# Genera los PDF de los documentos de docs/ (Markdown -> HTML con pandoc -> PDF con Chromium).
#   docs/pdf/generar.sh
# Necesita pandoc y Node con Playwright (el de e2e/).
set -euo pipefail
cd "$(dirname "$0")/../.."
SALIDA=docs/pdf
for par in "docs/negocio/BRD.md:Rabbit-BRD" "docs/funcional/DOCUMENTACION-FUNCIONAL.md:Rabbit-Documentacion-Funcional"; do
  fuente=${par%%:*}; nombre=${par##*:}
  pandoc "$fuente" -f markdown -t html5 --standalone --css=estilo.css --metadata lang=es \
    --syntax-highlighting=none -o "$SALIDA/$nombre.html" 2>/dev/null || \
  pandoc "$fuente" -f markdown -t html5 --standalone --css=estilo.css --metadata lang=es \
    --no-highlight -o "$SALIDA/$nombre.html"
  node -e '
    const { chromium } = require(process.cwd() + "/e2e/node_modules/playwright");
    (async () => {
      const opciones = process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {};
      const b = await chromium.launch(opciones);
      const p = await b.newPage();
      await p.goto("file://" + process.cwd() + "/" + process.argv[1]);
      await p.pdf({ path: process.argv[2], format: "A4", printBackground: true,
        displayHeaderFooter: true, headerTemplate: "<span></span>",
        footerTemplate: "<div style=\"font-size:7pt;width:100%;text-align:center;color:#777\">Rabbit · Grupo 9 · " + process.argv[3] + " · página <span class=pageNumber></span> de <span class=totalPages></span></div>",
        margin: { top: "16mm", bottom: "18mm", left: "14mm", right: "14mm" } });
      await b.close();
    })();' "$SALIDA/$nombre.html" "$SALIDA/$nombre.pdf" "$nombre"
  rm "$SALIDA/$nombre.html"
  echo "$SALIDA/$nombre.pdf"
done
