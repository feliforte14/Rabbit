# Pruebas y comandos

Todo lo necesario para levantar Rabbit, apagarlo y probarlo de punta a
punta antes de la entrega. La primera parte son los comandos; la segunda,
la lista de pruebas en orden, con una casilla para ir marcando.

Las contraseñas no van en este documento (el repositorio es público):
donde dice `<contraseña>`, pedísela al equipo. El detalle de cada paso de
negocio está en [FLUJO-DE-PRUEBAS.md](FLUJO-DE-PRUEBAS.md) y la
instalación desde cero en el [README](../../README.md#cómo-levantar-el-sistema).

Todos los comandos asumen:

```bash
export WILDFLY_HOME=~/wildfly
cli() { "$WILDFLY_HOME/bin/jboss-cli.sh" --connect "$@"; }
```

---

## Parte 1. Comandos

### Mapa de los sistemas

| Sistema | Dónde corre | Puerto | Hace falta para |
|---|---|---|---|
| WildFly 41 (Rabbit + broker JMS + banco y transportistas simulados) | Local | 8080 (app), 9990 (management) | Todo |
| Base PostgreSQL | Supabase (remota, siempre prendida) | 5432 (Session Pooler) | Todo |
| Banco legado en Node.js | Local, opcional | 8090 | Solo la demo de heterogeneidad |

El broker (ActiveMQ Artemis), el banco en Java y los dos transportistas
simulados viven dentro de WildFly: se prenden y se apagan con él.

### WildFly

| Qué | Comando |
|---|---|
| Prender (en esta terminal; se apaga con Ctrl+C) | `$WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml` |
| Prender en segundo plano | `nohup $WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml > ~/wildfly-consola.log 2>&1 &` |
| ¿Está prendido? | `cli --command=":read-attribute(name=server-state)"` → `"running"` |
| Apagar | `cli --command=":shutdown"` |
| Reiniciar el proceso | `cli --command=":shutdown(restart=true)"` |
| Recargar la configuración (sin reiniciar el proceso) | `cli --command=":reload"` |
| Ver el log en vivo | `tail -f $WILDFLY_HOME/standalone/log/server.log` |

Siempre con **`standalone-full.xml`**: con el perfil por defecto no hay
JMS y la app no despliega.

Prefijos útiles para filtrar el log (`grep`):

| Prefijo | Qué muestra |
|---|---|
| `[Pedidos][JMS]` | La cola de pedidos del ERP |
| `[Pagos][Circuito]` | Cambios de estado del circuit breaker |
| `[Banco legado]` | Cobros y reversas del banco simulado |
| `[Transportistas]` | Envíos derivados y cada novedad del seguimiento |
| `[Esquema]` | Restricciones CHECK realineadas al desplegar |

### La aplicación

Desde la raíz del repositorio:

| Qué | Comando |
|---|---|
| Compilar y desplegar (o redesplegar) | `mvn -q clean package wildfly:deploy` |
| Quitar el despliegue | `mvn -q wildfly:undeploy` |
| ¿Está desplegada? | `cli --command="deployment-info --name=Rabbit.war"` → `OK` |
| Redesplegar el WAR ya subido (tras cambiar una system property) | `cli --command="/deployment=Rabbit.war:redeploy"` |
| Abrir | http://localhost:8080/Rabbit |

Usá siempre `clean`: una compilación incremental ya dejó pasar un import
faltante.

### Base de datos (Supabase)

No se prende ni se apaga: es remota. Desde WildFly:

| Qué | Comando |
|---|---|
| Probar la conexión | `cli --command="/subsystem=datasources/data-source=RabbitDS:test-connection-in-pool"` |
| Vaciar el pool (si aparece `This connection has been closed`) | `cli --command="/subsystem=datasources/data-source=RabbitDS:flush-all-connection-in-pool"` |

Si responde `EAUTHQUERY ... connection to database not available`, el
proyecto de Supabase está pausado: reactivarlo desde su dashboard. Si
responde `EMAXCONNSESSION`, hay más de 15 conexiones abiertas en todo el
proyecto (por ejemplo, dos integrantes con WildFly prendido y la prueba de
escalabilidad corriendo): apagar alguno.

Para dejar la base vacía después de ensayar:
`Rabbit-limpieza-datos-de-prueba.sql` (fuera del repo, pedíselo al equipo),
en el SQL Editor de Supabase.

### Banco legado en Node.js (opcional)

| Qué | Comando |
|---|---|
| Prender | `cd banco-legado && npm install && npm start` (escucha en el 8090) |
| Apagar | Ctrl+C en su terminal, o `kill $(lsof -ti :8090)` |
| Conectar Rabbit a este banco | `cli --command="/system-property=rabbit.banco.wsdl:add(value=http://localhost:8090/BancoLegadoService?wsdl)"` y redesplegar |
| Volver al banco en Java | `cli --command="/system-property=rabbit.banco.wsdl:remove"` y redesplegar |
| Simular la caída | `curl -X POST "http://localhost:8090/admin/caida?activa=true"` |
| Terminar la caída | `curl -X POST "http://localhost:8090/admin/caida?activa=false"` |

Si Rabbit apunta al banco en Node y el banco está apagado, los pedidos
prepago no se pueden confirmar (el resto funciona). Antes de apagarlo,
volvé al banco en Java.

### Perillas para las demos (system properties)

Se agregan con `cli --command="/system-property=<nombre>:add(value=<valor>)"`
y se quitan con `cli --command="/system-property=<nombre>:remove"`.

| Propiedad | Por defecto | Qué hace | ¿Hay que redesplegar? |
|---|---|---|---|
| `rabbit.banco.simular.caida` | sin definir | `true`: el banco en Java tarda 10 s y no responde a tiempo | No |
| `rabbit.banco.wsdl` | el banco en Java | Apunta Rabbit a otro banco (por ejemplo, el de Node) | Sí |
| `rabbit.banco.cb.umbral` | `3` | Fallas seguidas que abren el circuito | Sí |
| `rabbit.banco.cb.espera.ms` | `30000` | Cuánto espera el circuito abierto antes de probar | Sí |
| `rabbit.transportista.simulador.segundos` | `20` | Cada cuántos segundos avanza un envío simulado. Menos de 15 hace que el pedido salte de Confirmado a Entregado | No |
| `rabbit.cola.consumidores` | `1` | Consumidores de la cola de pedidos del ERP | Sí |
| `rabbit.sincronizador.pausado` | sin definir | `true`: pausa el timer de respaldo de la cola (lo usa la prueba de escalabilidad) | No |

Para ver cuáles están definidas:
`cli --command="/system-property=*:read-resource"`.

### Secuencias completas

**Prender todo para la demo:**

```bash
nohup $WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml > ~/wildfly-consola.log 2>&1 &
# esperar a que responda "running"
cli --command=":read-attribute(name=server-state)"
cli --command="/subsystem=datasources/data-source=RabbitDS:test-connection-in-pool"
mvn -q clean package wildfly:deploy
# opcional: banco en Node, en otra terminal
cd banco-legado && npm start
```

**Apagar todo:**

```bash
cli --command="/system-property=rabbit.banco.wsdl:remove"          # si apuntaba al banco en Node
cli --command="/system-property=rabbit.banco.simular.caida:remove"  # si quedó una caída simulada
kill $(lsof -ti :8090)                                               # banco en Node, si estaba prendido
cli --command=":shutdown"
```

Los `:remove` responden error si la propiedad no existía: no importa.

---

## Parte 2. Pruebas

Hacelas en este orden: cada bloque usa lo que dejó el anterior. Con la
base vacía, el recorrido completo lleva alrededor de una hora.

**Estado de partida.** La base se limpió el 29/09/2026: no hay
comercios, depósitos, repartidores, pedidos, transportistas ni zonas.
Quedan estas cuentas:

| Cuenta | Rol | Dónde vive | Para qué |
|---|---|---|---|
| `claude-cb-admin` | `ADMINISTRADOR` | Realm de WildFly | Todo el bloque del personal |
| `claude-cb-erp` | `ERP` | Realm de WildFly | La API REST |
| `prueba.comercio` | `COMERCIO` | Base y realm | Sin comercio asociado: sirve para la prueba S5 |
| `prueba.repartidor` | `REPARTIDOR` | Base y realm | Sin repartidor asociado |

Las cuentas `demo.comercio` y `demo.repartidor` se crean en el bloque A.

### A. Arranque y datos base (como `claude-cb-admin`)

- [ ] **A1. Login.** Entrar a http://localhost:8080/Rabbit → cae en
  `personal/pedidos.xhtml`, vacío y sin errores; el menú marca "Pedidos".
- [ ] **A2. Comercio.** Comercios → registrar uno (CUIT `XX-XXXXXXXX-X`).
  Aparece Activo.
- [ ] **A3. Punto de picking.** Desde la fila del comercio, "Puntos de
  picking" → agregar uno.
- [ ] **A4. Depósito y stock.** Depósitos y stock → registrar un depósito
  → "Ver stock" → cargar stock del comercio. Anotar el ID del ítem.
- [ ] **A5. Repartidores.** Registrar dos.
- [ ] **A6. Cuentas.** Usuarios → crear `demo.comercio` (Comercio, el de
  A2) y `demo.repartidor` (Repartidor, uno de A5). Al elegir el tipo de
  cuenta aparece el desplegable del comercio o del repartidor.
- [ ] **A7. Validaciones de alta.** Un CUIT mal formado, un usuario
  repetido y un repartidor que ya tiene cuenta se rechazan con un mensaje
  claro.

### B. Pedidos del ERP: REST + cola punto a punto

- [ ] **B1. Alta por la API.** Con los IDs de A2 y A4:

  ```bash
  curl -i -u claude-cb-erp:<contraseña> -X POST \
    http://localhost:8080/Rabbit/api/pedidos-externos \
    -H "Content-Type: application/json" \
    -d '{"idComercio":<id>,"origen":"STOCK_CONSIGNADO","lineas":[{"idItem":<id>,"cantidad":1}],
         "importe":2500,"medioPago":"PREPAGO","direccionEntrega":"Av. Corrientes 1234, CABA",
         "codigoPostalEntrega":"1043"}'
  ```

  Responde `201` con `idPedidoExterno` y `Location`.
- [ ] **B2. Sincronización por la cola.**
  `curl -u claude-cb-erp:<contraseña> http://localhost:8080/Rabbit/api/pedidos-externos/<idPedidoExterno>`
  → pasa de `Pendiente` a `Sincronizado` con el `idPedido`. En el log:
  `[Pedidos][JMS] Pedido externo N sincronizado en tiempo real`.
- [ ] **B3. Rechazos de la API.** Sin `direccionEntrega` → `400`; con
  `"codigoPostalEntrega":"12"` → `400`; sin credenciales → `401`; con
  `claude-cb-admin` (no es ERP) → `403`.
- [ ] **B4. Pedido descartado.** Un pedido con más cantidad que el stock
  disponible queda `Descartado`, con el motivo.
- [ ] **B5. Seguimiento público.**
  `curl http://localhost:8080/Rabbit/api/seguimiento/<idPedido>` devuelve
  el estado sin pedir login.
- [ ] **B6. Simulación desde la web.** En Pedidos, "Simular un pedido
  del ERP" con origen punto de picking: aparece en la tabla al recargar.

### C. Confirmación: transacción + SOAP + tópico pub/sub

- [ ] **C1. Confirmar un prepago.** Pedidos → Confirmar el pedido de B1:
  se cobra en el banco (Cobro "Cobrado") y se asigna un repartidor; queda
  Confirmado.
- [ ] **C2. Banco rechaza.** Un pedido prepago de más de $500.000 → el
  banco lo rechaza, el pedido sigue Pendiente y no queda repartidor
  tomado.
- [ ] **C3. Reversa compensatoria.** Con los repartidores ocupados,
  confirmar un prepago → se cobra, falla la asignación, se deshace todo y
  en el log aparece `[Banco legado] Reversado ...`.
- [ ] **C4. Contra entrega.** Confirmar uno contra entrega: Cobro "A
  cobrar"; al entregarlo (bloque D) pasa a "Cobrado" (lo acredita el
  suscriptor de Pagos del tópico).
- [ ] **C5. Cancelar.** Cancelar un pedido Pendiente (lo hace un
  operador) y uno Confirmado (solo `ADMINISTRADOR`, porque anula el
  cobro): el repartidor queda libre.

### D. Repartidor y comercio

- [ ] **D1. Hoja de ruta.** Entregas en curso muestra el pedido de C1 con
  retiro, entrega, repartidor y "Ver recorrido en el mapa".
- [ ] **D2. Vista del repartidor (en el celular, o con el navegador
  angosto).** Entrar como `demo.repartidor` → cae en
  `repartidor/mis-entregas.xhtml`: dónde retirar, dónde entregar y cuánto
  cobrar.
- [ ] **D3. Mapa.** "Ver recorrido en el mapa" abre Google Maps en otra
  pestaña, con el retiro como parada y la entrega como destino.
- [ ] **D4. Retiro y entrega.** "Ya retiré el pedido" → En camino;
  "Entregué el pedido" → Entregado; pasa al historial y el repartidor
  queda libre.
- [ ] **D5. Portal del comercio.** Entrar como `demo.comercio` → cae en
  `comercio/mis-pedidos.xhtml`: sus pedidos con estado, cobro y
  repartidor, y un aviso por cada cambio de estado (eso prueba el
  tópico). "Mi stock" muestra lo de A4; "Mis puntos de picking" permite
  agregar, dar de baja y reactivar.

### E. Transportistas (REST y SOAP legado)

- [ ] **E1. Alta.** Transportistas → registrar uno REST
  (`http://localhost:8080/Rabbit/api/simulador/transportista-rest`) y uno
  SOAP (`http://localhost:8080/Rabbit/TransportistaLegadoService?wsdl`).
- [ ] **E2. Derivar.** Pedidos → "Derivar a un transportista": devuelve un
  código de seguimiento; el pedido queda Confirmado con el transportista.
- [ ] **E3. Seguimiento automático.** Sin tocar nada: a los ~20 s En
  camino, a los ~40 s Entregado (el log muestra cada novedad con
  `[Transportistas]`). Con el SOAP, el legado dice `EN_VIAJE` y Rabbit
  muestra "En tránsito".
- [ ] **E4. Rechazo.** Derivar un pedido de más de 50 unidades → el
  transportista lo rechaza y el pedido sigue Pendiente.
- [ ] **E5. Cancelar un derivado.** Cancelarlo desde Pedidos → en el log,
  el envío se cancela en el transportista.
- [ ] **E6. Lo ve el comercio.** En Mis pedidos, el derivado muestra el
  transportista y su código de seguimiento.

### F. Ruteo por zona

- [ ] **F1. Zonas.** Ruteo por zona → registrar "Norte" 1400-1499
  (repartidores propios, respaldo: el SOAP) y "Sur" 1800-1899
  (transportista: el REST).
- [ ] **F2. Rechazos.** Un rango superpuesto con Norte, uno invertido
  (1499-1400) y una zona de transportista sin transportista.
- [ ] **F3. Repartidor con zona.** Repartidores → "Cambiar la zona" de
  uno a Norte, y dejar ocupado al otro (o sin zona).
- [ ] **F4. Pedidos.** Simular cuatro: CP 1414, CP 1426, uno con dirección
  "Calle 12 1846, Adrogué (1846)" sin CP y uno sin CP en ningún lado.
- [ ] **F5. Tablero.** Norte tiene dos, Sur uno (el CP salió de la
  dirección) y "Sin zona" uno, sin botón Despachar.
- [ ] **F6. Despachar.** "Despachar toda la zona" en Norte → el primero
  con el repartidor de la zona, el segundo derivado al respaldo. Despachar
  el de Sur → derivado al REST. La tabla "Resultado del despacho" lo
  explica pedido por pedido.

### G. Inventario: reserva de stock (EJB stateful)

- [ ] **G1. Reservar.** Reserva de stock → elegir comercio y depósito →
  reservar una cantidad: aparece la reserva vigente, con 5 minutos.
- [ ] **G2. Extender, confirmar y liberar.** "Extender" suma 5 minutos;
  "Confirmar" descuenta el stock; en otra reserva, "Liberar" lo devuelve.
- [ ] **G3. Vencimiento.** Reservar y no hacer nada: a los 5 minutos
  (más hasta 1 minuto del barredor) queda vencida y el stock vuelve.
- [ ] **G4. Historial.** Historial de reservas muestra cada una con su
  estado final.

### H. Resiliencia (desafío opcional)

- [ ] **H1. Circuit breaker.** `rabbit.banco.simular.caida=true` →
  confirmar 4 prepagos seguidos: los 3 primeros tardan ~5 s, el 4.º falla
  al instante. En el log, `[Pagos][Circuito]` CERRADO → ABIERTO.
- [ ] **H2. Recuperación.** Quitar la propiedad, esperar 30 s y confirmar
  otro: SEMIABIERTO → CERRADO.
- [ ] **H3. Polling de respaldo.** Mostrar que el timer
  `SincronizadorDePedidos` corre cada minuto (a los 30 s de cada minuto):
  si un mensaje de la cola se pierde, el pedido se sincroniza igual en
  menos de un minuto (ADR-001). Perder un mensaje a propósito exige tocar
  el broker, así que se explica con el código.

### I. Heterogeneidad y escalabilidad (desafíos opcionales)

- [ ] **I1. Banco en Node.** Prenderlo, apuntar `rabbit.banco.wsdl`,
  redesplegar y confirmar un prepago: la autorización aparece en la
  consola de Node. Probar también su caída simulada con el circuit
  breaker. Al terminar, volver al banco en Java.
- [ ] **I2. Escalabilidad.** Con un comercio y un punto de picking activos:

  ```bash
  export RABBIT_ERP_USUARIO=claude-cb-erp RABBIT_ERP_CLAVE=<contraseña>
  export RABBIT_ID_COMERCIO=<id> RABBIT_ID_PUNTO=<id>
  python3 scripts/prueba_escalabilidad.py 1 4 8 --pedidos 100
  ```

  Imprime la tabla de tiempos por cantidad de consumidores. Deja 300
  pedidos de carga: limpiar la base después.

### S. Seguridad y vistas por tipo de usuario

- [ ] **S1. Páginas ajenas.** Como `demo.comercio`, abrir
  `personal/usuarios.xhtml` y `repartidor/mis-entregas.xhtml` → lo
  devuelve a Mis pedidos. Como `demo.repartidor`, abrir
  `personal/pedidos.xhtml` → lo devuelve a Mis entregas.
- [ ] **S2. Solo administrador.** Como operador (crear uno en Usuarios):
  no ve "Usuarios", no puede eliminar comercios ni cancelar un pedido
  confirmado.
- [ ] **S3. Datos ajenos.** Como `demo.comercio`, pegar
  `comercio/puntos-picking.xhtml?idComercio=<otro id>` → el parámetro se
  ignora y ve solo los suyos.
- [ ] **S4. ERP sin web.** Entrar a la web con `claude-cb-erp` → el login
  lo rechaza ("no tiene acceso a la aplicación web").
- [ ] **S5. Cuenta sin asociar.** Entrar con `prueba.comercio`: el menú
  dice "Cuenta sin asociar" y "Mis puntos de picking" muestra "Tu cuenta
  no está asociada a un comercio activo. Contactá a un administrador.", no
  un error interno.
- [ ] **S6. URLs viejas y plantilla.** `http://localhost:8080/Rabbit/pedidos.xhtml`
  y `.../template.xhtml` → página de error, sin detalles internos.
- [ ] **S7. Sin sesión.** Cerrar sesión y abrir `personal/pedidos.xhtml`
  → vuelve al login.
- [ ] **S8. Eliminar comercio.** Eliminar un comercio con pedidos o
  cuentas → se rechaza; darlo de baja primero es obligatorio.
- [ ] **S9. Reactivar una cuenta.** Usuarios → dar de baja `demo.repartidor`
  → ya no puede entrar. "Reactivar una cuenta" con una contraseña nueva →
  entra con la nueva y no con la vieja. Reactivar `prueba.comercio` se
  rechaza: su comercio ya no existe.

### Cierre

- [ ] Volver al banco en Java y quitar las system properties de las demos
  (`cli --command="/system-property=*:read-resource"` no debería listar
  ninguna de `rabbit.`).
- [ ] Revisar el log: sin `ERROR` nuevos salvo los provocados a propósito
  (banco caído, rechazos).
- [ ] Si fue un ensayo: limpiar la base con el script de limpieza.
