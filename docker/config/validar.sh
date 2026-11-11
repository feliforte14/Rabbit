#!/bin/sh
# =====================================================================
# Revisa el .env antes de levantar nada. Corre en el servicio "config" del
# compose: la base y WildFly esperan a que termine bien, así que un .env
# copiado sin completar corta "docker compose up" al principio, con un
# mensaje que dice qué corregir, y no minutos después.
#
# Junta todos los problemas y los muestra juntos. Nunca imprime una
# contraseña, solo el nombre de la variable.
# =====================================================================
set -u

problemas=0
problema() {
  echo "[Config] $1" >&2
  problemas=$((problemas + 1))
}

for variable in DB_PASSWORD ADMIN_USER ADMIN_PASSWORD DEMO_PASSWORD; do
  eval "valor=\${$variable:-}"
  case "$valor" in
    "")
      problema "Falta $variable en .env" ;;
    "<"*">")
      problema "$variable todavía tiene el marcador de .env.example: reemplazalo por un valor tuyo" ;;
    *)
      case "$variable" in
        *_PASSWORD)
          [ "${#valor}" -ge 8 ] || problema "$variable tiene que tener al menos 8 caracteres" ;;
      esac ;;
  esac
done

if [ "$problemas" -gt 0 ]; then
  echo "[Config] Corregí .env (ver .env.example y el README, sección \"Con Docker\") y volvé a correr docker compose up" >&2
  exit 1
fi
echo "[Config] .env completo"
