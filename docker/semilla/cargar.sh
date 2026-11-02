#!/bin/sh
# =====================================================================
# Carga los datos semilla cuando Rabbit ya creó las tablas.
#
# Las tablas las crea Hibernate al desplegar (hbm2ddl.auto=update), así
# que este contenedor espera a que exista "usuarios" y recién ahí corre
# semilla.sql. Es idempotente: correrlo de nuevo no duplica nada.
# =====================================================================
set -eu

export PGHOST="${DB_HOST:-db}" PGPORT="${DB_PORT:-5432}" PGDATABASE="${DB_NAME:-rabbit}" \
       PGUSER="${DB_USER:-rabbit}" PGPASSWORD="${DB_PASSWORD:?Falta DB_PASSWORD en .env}"

: "${ADMIN_USER:?Falta ADMIN_USER en .env}"
: "${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD en .env}"

# La contraseña del administrador sale del .env en cada arranque y se
# convierte en bcrypt dentro de la base: en el repo no queda ni la
# contraseña ni su hash. El .env ya lo revisó el servicio "config"
# (docker/config/validar.sh) antes de levantar la base.
: "${DEMO_PASSWORD:?Falta DEMO_PASSWORD en .env}"

echo "[Semilla] Esperando a que Rabbit cree las tablas..."
intentos=0
until psql -tAc "SELECT 1 FROM information_schema.columns WHERE table_name='usuarios' AND column_name='hash_clave'" 2>/dev/null | grep -q 1; do
  intentos=$((intentos + 1))
  if [ "$intentos" -ge 120 ]; then
    echo "[Semilla] Rabbit no creó las tablas en 10 minutos: revisá 'docker compose logs wildfly'" >&2
    exit 1
  fi
  sleep 5
done

psql -v ON_ERROR_STOP=1 -q \
     -v admin_user="$ADMIN_USER" -v admin_password="$ADMIN_PASSWORD" \
     -v demo_password="$DEMO_PASSWORD" \
     -f /semilla/semilla.sql
echo "[Semilla] Listo: entrá a https://localhost:8443/Rabbit con ${ADMIN_USER}"
if [ -n "$DEMO_PASSWORD" ]; then
  echo "[Semilla] Cuentas demo (con DEMO_PASSWORD): demo.operador, demo.erp, demo.erp2, demo.comercio, demo.comercio2, demo.repartidor"
fi
