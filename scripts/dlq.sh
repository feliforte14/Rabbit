#!/usr/bin/env bash
# Cola de mensajes muertos (DLQ) de Rabbit: ver y reprocesar lo que el broker
# no pudo entregar después de los reintentos (docker/wildfly/configurar.cli:
# 5 intentos, 2 s entre uno y otro).
#
#   scripts/dlq.sh listar                      cuántos hay y de qué cola vienen
#   scripts/dlq.sh reprocesar <cola-origen>    los devuelve a su cola de origen
#   scripts/dlq.sh descartar                   borra todos (después de revisarlos)
#
# <cola-origen> es la columna "origen" de "listar", por ejemplo
# jms.queue.cola.pedidos.externos o rabbit-pagos.pagos-estados-pedido.
# Si el mensaje sigue siendo ilegible, vuelve a la DLQ después de los reintentos:
# primero hay que corregir la causa (o descartarlo).
#
# Con Docker Compose levantado. Para un WildFly propio, JBOSS_CLI apunta a su
# jboss-cli.sh (con JBOSS_CLI="/ruta/jboss-cli.sh --connect").
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
RQ="/subsystem=messaging-activemq/server=default/runtime-queue=jms.queue.DLQ"

cli() {
  if [ -n "${JBOSS_CLI:-}" ]; then
    $JBOSS_CLI --command="$1"
  else
    (cd "$RAIZ" && docker compose exec -T wildfly /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="$1")
  fi
}

case "${1:-}" in
  listar)
    echo "Mensajes en la DLQ: $(cli "$RQ:read-attribute(name=message-count)" | tr -d '\n ' | sed 's/.*"result"=>\([0-9]*\)L.*/\1/')"
    cli "$RQ:list-messages" | grep -E '"messageID"|"_AMQ_ORIG_QUEUE"|"timestamp"' | sed 's/^ *//; s/_AMQ_ORIG_QUEUE/origen/'
    ;;
  reprocesar)
    origen="${2:?Falta la cola de origen (ver: scripts/dlq.sh listar)}"
    cli "$RQ:move-messages(other-queue-name=\"$origen\", filter=\"_AMQ_ORIG_QUEUE='$origen'\")"
    ;;
  descartar)
    cli "$RQ:remove-messages"
    ;;
  *)
    sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
