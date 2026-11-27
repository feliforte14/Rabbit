#!/bin/bash
# Busca datos sensibles y stack traces en el log de WildFly (SEC-09 y "logs sin
# datos personales ni stack traces" del eje D). Correrlo DESPUÉS de pruebas_vivo.py, que carga pedidos con datos de
# quien recibe y provoca errores.
#
#   set -a; . ./.env; set +a
#   scripts/seguridad/revisar_logs.sh                 # usa docker compose logs wildfly
#   scripts/seguridad/revisar_logs.sh ruta/server.log # o un archivo
#
# Sale con 1 si encuentra algo. Nunca imprime el valor de una contraseña: solo
# cuántas veces aparece.
set -uo pipefail
cd "$(dirname "$0")/../.."
LOG=$(mktemp)
trap 'rm -f "$LOG"' EXIT
if [ $# -ge 1 ]; then cp "$1" "$LOG"; else docker compose logs --no-color wildfly > "$LOG" 2>/dev/null; fi
echo "Líneas revisadas: $(wc -l < "$LOG")"

encontrado=0
revisar() {  # nombre patrón [fijo]
  local n
  if [ "${3:-}" = fijo ]; then n=$(grep -cF -- "$2" "$LOG"); else n=$(grep -cE -- "$2" "$LOG"); fi
  if [ "$n" -gt 0 ]; then echo "  [FALLA] $1: $n líneas"; encontrado=1; else echo "  [OK ] $1"; fi
}
for var in DEMO_PASSWORD ADMIN_PASSWORD DB_PASSWORD; do
  valor="${!var:-}"
  [ -n "$valor" ] && revisar "Contraseña ($var)" "$valor" fijo
done
revisar "Cabecera Authorization / Basic" 'Authorization:|Basic [A-Za-z0-9+/=]{8,}'
revisar "Hash bcrypt" '\$2[aby]\$[0-9]{2}\$'
revisar "Cookie de sesión" 'JSESSIONID=[A-Za-z0-9_-]{10,}'
revisar "Email completo de prueba" 'seg@correo\.test|privado@correo\.test|cliente@correo\.test'
revisar "Datos de quien recibe de prueba" 'Calle Privada|Nombre Privado|1155550000'
revisar "Cargas de inyección" "DROP TABLE|onerror=alert|<script>alert"
# Stack traces: las fallas esperadas (banco caído, 403, conflicto de stock, mensaje
# ilegible) se loguean en una línea. Una traza acá es una falla inesperada: hay que
# mirarla, no acostumbrarse a verlas. Se cuentan las líneas "<tab>at paquete.Clase(".
revisar "Stack traces" '^([A-Za-z0-9_.-]+ +\| )?[[:space:]]+at [^[:space:]]+\('
exit $encontrado
