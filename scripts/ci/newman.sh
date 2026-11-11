#!/bin/bash
# =====================================================================
# Corre la colección de Postman (docs/pruebas/Rabbit.postman_collection.json)
# contra Rabbit levantado con la semilla del compose. Lo usa el CI y sirve
# igual en local:
#
#   DEMO_PASSWORD=... ADMIN_USER=... ADMIN_PASSWORD=... \
#   ITEM=<id> PUNTO=<id> scripts/ci/newman.sh
#
# Solo las carpetas que no necesitan un paso manual en la web. Quedan
# afuera A7 (cancelar un pedido ya confirmado), A8 (lista de espera y
# devolución al depósito), H4 (comercio dado de baja) y la 4 (pedidos de
# apoyo para pruebas web).
# =====================================================================
set -euo pipefail

BASE="${BASE:-https://localhost:8443/Rabbit}"
HTTP="${HTTP:-http://localhost:8080/Rabbit}"
: "${DEMO_PASSWORD:?Falta DEMO_PASSWORD}" "${ADMIN_USER:?Falta ADMIN_USER}" "${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD}"
: "${ITEM:?Falta ITEM (id del ítem con stock de Almacén Demo 1)}" "${PUNTO:?Falta PUNTO (id del punto de picking)}"
cd "$(dirname "$0")/../.."
SALIDA="${SALIDA:-target/newman}"
mkdir -p "$SALIDA"
newman run docs/pruebas/Rabbit.postman_collection.json \
  --insecure --delay-request 300 --timeout-request 15000 \
  --env-var "baseUrl=$BASE" --env-var "httpUrl=$HTTP" \
  --env-var erpUsuario=demo.erp --env-var "erpClave=$DEMO_PASSWORD" \
  --env-var erp2Usuario=demo.erp2 --env-var "erp2Clave=$DEMO_PASSWORD" \
  --env-var comercioUsuario=demo.comercio2 --env-var "comercioClave=$DEMO_PASSWORD" \
  --env-var "adminUsuario=$ADMIN_USER" --env-var "adminClave=$ADMIN_PASSWORD" \
  --env-var "idItem=$ITEM" --env-var "idPuntoPicking=$PUNTO" \
  --folder "A1 · Alta con stock consignado" \
  --folder "A4 · Idempotencia" \
  --folder "A2 · Alta desde punto de picking" \
  --folder "A5 · Sin stock suficiente" \
  --folder "2 · Validaciones y seguridad de la API (A6)" \
  --folder "3 · Seguimiento público (J1)" \
  --folder "5 · Transportistas externos (llamados directos)" \
  --folder "6 · Banco legado SOAP (llamados directos)" \
  --folder "7 · Contrato, idempotencia y seguridad cruzada" \
  --reporters cli,junit --reporter-junit-export "$SALIDA/newman.xml"
