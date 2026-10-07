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

El WSDL queda en `http://localhost:8090/BancoLegadoService?wsdl`. Para otro
puerto: `PUERTO=9000 npm start` (y cambiar `soap:address` en `banco.wsdl`).

## Conectar Rabbit a este banco

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.banco.wsdl:add(value=http://localhost:8090/BancoLegadoService?wsdl)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/deployment=Rabbit.war:redeploy"
```

Para volver al banco en Java: `/system-property=rabbit.banco.wsdl:remove` y
redesplegar.

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
