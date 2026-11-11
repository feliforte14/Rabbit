# Banco legado en Node.js

El sistema externo con el que Rabbit cobra los pedidos prepago, implementado
en **Node.js** con [`soap`](https://www.npmjs.com/package/soap). Publica el
mismo contrato ([`banco.wsdl`](banco.wsdl)) que el banco simulado en Java que
vive dentro de Rabbit, así que Rabbit lo consume sin cambiar código. Es el
desafío de heterogeneidad tecnológica: ver
[DESAFIOS-OPCIONALES.md](../docs/consigna/DESAFIOS-OPCIONALES.md#4-heterogeneidad-tecnológica).

## Levantarlo

Requiere Node.js 18 o superior.

```bash
cd banco-legado
npm install
npm start          # escucha en http://localhost:8090/BancoLegadoService
```

El WSDL queda en `http://localhost:8090/BancoLegadoService?wsdl`.

| Variable | Para qué | Por defecto |
|---|---|---|
| `PUERTO` | Puerto HTTP | `8090` |
| `URL_PUBLICA` | La dirección con la que Rabbit llega a este banco. Va en el `soap:address` del WSDL publicado, y Rabbit llama a esa dirección | `http://localhost:<PUERTO>` |

## Conectar Rabbit a este banco

Rabbit lee el WSDL de `rabbit.banco.wsdl` al desplegar y después llama a la
dirección que dice ese WSDL. Por eso el banco tiene que publicar una
dirección a la que Rabbit llegue.

**Con WildFly instalado en la misma máquina:**

```bash
npm start
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.banco.wsdl:add(value=http://localhost:8090/BancoLegadoService?wsdl)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/deployment=Rabbit.war:redeploy"
```

**Con Rabbit en Docker** (el `docker compose` del README): dentro del
contenedor, `localhost` es el propio contenedor. La máquina se llama
`host.docker.internal` (Docker Desktop y OrbStack lo traen; en Linux hace
falta `extra_hosts: ["host.docker.internal:host-gateway"]` en el servicio
`wildfly`):

```bash
URL_PUBLICA=http://host.docker.internal:8090 npm start
docker compose exec -T wildfly /opt/jboss/wildfly/bin/jboss-cli.sh --connect \
  --command="/system-property=rabbit.banco.wsdl:add(value=http://host.docker.internal:8090/BancoLegadoService?wsdl)"
docker compose exec -T wildfly /opt/jboss/wildfly/bin/jboss-cli.sh --connect \
  --command="/deployment=Rabbit.war:redeploy"
```

Para volver al banco en Java: `/system-property=rabbit.banco.wsdl:remove` y
redesplegar.

Ojo al cambiar de banco con pedidos PREPAGO ya cobrados: si después se
cancela uno, la reversa le llega al banco configurado en ese momento, no
al que lo autorizó (ver Limitaciones en
[DESAFIOS-OPCIONALES.md](../docs/consigna/DESAFIOS-OPCIONALES.md)).

## Reglas

- `autorizarPago`: más de $500.000 se rechaza con un `soap:Fault`
  `PagoRechazado` (Rabbit lo recibe como `PagoRechazadoException`, con el
  motivo); cualquier otro importe se autoriza con un código `AUT-n`.
- `reversarPago`: devuelve la plata de una autorización (idempotente).
- Los movimientos viven en memoria: se pierden al reiniciar.

## Caída simulada (para mostrar el circuit breaker de Rabbit)

```bash
curl -X POST "http://localhost:8090/admin/caida?activa=true"    # cada llamada tarda 10 s y falla
curl -X POST "http://localhost:8090/admin/caida?activa=false"
```
