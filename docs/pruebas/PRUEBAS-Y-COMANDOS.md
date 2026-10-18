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
| WildFly 41 (Rabbit + broker JMS + banco y transportistas simulados) | Local | 8443 (app, HTTPS), 8080 (simuladores), 9990 (management) | Todo |
| Base PostgreSQL | Supabase (remota, siempre prendida) | 5432 (Session Pooler) | Todo |

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
| Abrir | https://localhost:8443/Rabbit (certificado autofirmado: aceptar la advertencia) |

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

Para limpiar la base después de ensayar:
[`scripts/Rabbit-limpieza-datos-de-prueba.sql`](../../scripts/Rabbit-limpieza-datos-de-prueba.sql),
en el SQL Editor de Supabase. Tiene tres opciones: **A**, solo los pedidos
de las pruebas automáticas (`[TEST]` y `[CARGA]`); **C**, todo menos las
cuentas (conserva los usuarios y los comercios, repartidores y zonas a los
que apuntan); **B**, vaciar todo, cuentas incluidas. Cada una corre primero
como ensayo y se confirma con el `COMMIT` del final. La base es compartida:
avisar al equipo antes de la C o la B.

### Colección de Postman

[`Rabbit.postman_collection.json`](Rabbit.postman_collection.json):
**Import → File** en Postman. Trae 46 requests ordenados por prueba (los
códigos A1, A4, J1... de esta guía y de FLUJO-DE-PRUEBAS):

| Carpeta | Qué prueba |
|---|---|
| 1 · API del ERP | Alta, idempotencia, punto de picking, sin stock, cancelación y comercio dado de baja. En orden: cada alta guarda su id y la consulta de al lado lo usa |
| 2 · Validaciones y seguridad | 400, 422, 401, 403, 404, 404 y 302 |
| 3 · Seguimiento público | 200 y 404, sin login |
| 4 · Pedidos de apoyo | Pedidos para seguir probando en la web (banco que rechaza, ruteo por zona) |
| 5 · Transportistas | REST y SOAP legado, llamados directo |
| 6 · Banco legado | Llamado directo: autorizar, rechazo y reversa |

Antes de usarla: en **Settings**, apagar *SSL certificate verification*
(el certificado es autofirmado), y en las variables de la colección
completar `erpClave` y `adminClave` en *Current value* (no se exportan).
Los pasos que piden hacer algo en la web lo dicen en la descripción
(ANTES / DESPUÉS).

### Perillas para las demos (system properties)

Se agregan con `cli --command="/system-property=<nombre>:add(value=<valor>)"`
y se quitan con `cli --command="/system-property=<nombre>:remove"`.

| Propiedad | Por defecto | Qué hace | ¿Hay que redesplegar? |
|---|---|---|---|
| `rabbit.banco.simular.caida` | sin definir | `true`: el banco en Java tarda 10 s y no responde a tiempo | No |
| `rabbit.banco.wsdl` | el banco en Java | Apunta Rabbit a otro banco con el mismo WSDL | Sí |
| `rabbit.banco.cb.umbral` | `3` | Fallas seguidas que abren el circuito | Sí |
| `rabbit.banco.cb.espera.ms` | `30000` | Cuánto espera el circuito abierto antes de probar | Sí |
| `rabbit.transportista.simulador.segundos` | `20` | Cada cuántos segundos avanza un envío simulado. Menos de 15 hace que el pedido salte de Confirmado a Entregado | No |
| `rabbit.cola.consumidores` | `1` | Consumidores de la cola de pedidos del ERP | Sí |
| `rabbit.sincronizador.pausado` | sin definir | `true`: pausa el timer de respaldo de la cola (lo usa la prueba de escalabilidad) | No |
| `rabbit.ruteo.hora-corte` | `07:00` | `HH:mm`: desde esa hora los pedidos que se confirman van a la lista de espera para el día siguiente (ADR-037), y los que llegan reciben fecha prometida para mañana (ADR-042). Para probarlo de día, `00:01`; para una demo después de las 21, `ninguno` | No |
| `rabbit.entregas.max-intentos` | `2` | Visitas fallidas antes de devolver el pedido al comercio (ADR-041). Con `1`, la primera visita fallida ya lo devuelve | No |
| `rabbit.ruteo.minutos-por-parada` | `20` | Minutos por parada para la franja horaria del seguimiento (ADR-043) | No |
| `rabbit.url-publica` | `https://localhost:8443/Rabbit` | Base del link al seguimiento en los avisos a quien recibe (ADR-040) | No |

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
```

**Apagar todo:**

```bash
cli --command="/system-property=rabbit.banco.simular.caida:remove"  # si quedó una caída simulada
cli --command=":shutdown"
```

Los `:remove` responden error si la propiedad no existía: no importa.

---

## Parte 2. Pruebas

Hacelas en este orden: cada bloque usa lo que dejó el anterior. Con la
base vacía, el recorrido completo lleva alrededor de una hora.

**Estado de partida.** La base se limpió el 29/09/2026: no hay
comercios, depósitos, repartidores, pedidos, transportistas, zonas ni
cuentas en la tabla `usuarios`. La única cuenta es `claude-cb-admin`
(`ADMINISTRADOR`, en el realm de WildFly). `demo.comercio`,
`demo.repartidor` y `demo.erp` se crean en el bloque A.

### A. Arranque y datos base (como `claude-cb-admin`)

- [ ] **A1. Login.** Entrar a https://localhost:8443/Rabbit → cae en
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
- [ ] **A8. Cuenta del ERP.** Desde **Usuarios**: usuario `demo.erp`, tipo
  `ERP (API REST)`, asociada al comercio de A2. Solo usa la API y solo ve
  los pedidos de ese comercio; no puede entrar a la web.

### B. Pedidos del ERP: REST + cola punto a punto

- [ ] **B1. Alta por la API.** Con el ID del ítem de A4 (el comercio es
  el de la cuenta ERP, no va en el cuerpo):

  ```bash
  curl -ik -u demo.erp:<contraseña> -X POST \
    https://localhost:8443/Rabbit/api/v1/pedidos-externos \
    -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
    -d '{"origen":"STOCK_CONSIGNADO","lineas":[{"idItem":<id>,"cantidad":1}],
         "importe":2500,"medioPago":"PREPAGO","direccionEntrega":"Av. Corrientes 1234, CABA",
         "codigoPostalEntrega":"1043"}'
  ```

  Responde `201` con `idPedidoExterno` y `Location`.
- [ ] **B2. Sincronización por la cola.**
  `curl -k -u demo.erp:<contraseña> https://localhost:8443/Rabbit/api/v1/pedidos-externos/<idPedidoExterno>`
  → pasa de `Pendiente` a `Sincronizado` con el `idPedido`. En el log:
  `[Pedidos][JMS] Pedido externo N sincronizado en tiempo real`.
- [ ] **B3. Rechazos de la API.** Sin `direccionEntrega` → `400`; con
  `"codigoPostalEntrega":"12"` → `422` (regla de negocio); sin credenciales → `401`; con
  `claude-cb-admin` (no es ERP) → `403`.
- [ ] **B4. Pedido descartado.** Un pedido con más cantidad que el stock
  disponible queda `Descartado`, con el motivo.
- [ ] **B5. Seguimiento público.**
  `curl -k https://localhost:8443/Rabbit/api/v1/seguimiento/<codigoSeguimiento>`
  devuelve el estado sin pedir login (el código lo ve el comercio en Mis
  pedidos).
- [ ] **B6. Simulación desde la web.** En Pedidos, "Simular un pedido
  del ERP" con origen punto de picking: aparece en la tabla al recargar.
- [ ] **B7. Idempotencia.** Repetir B1 con la **misma** `Idempotency-Key`
  → `201` con el mismo `idPedidoExterno`; en Pedidos hay uno solo.
- [ ] **B8. Cancelación desde el ERP.** `POST .../pedidos-externos/<id>/cancelacion`
  → `200` (`Sincronizado` con `estadoPedido: CANCELADO`, o `Cancelado` si
  todavía no había salido de la cola); repetirlo da `200` igual; con el
  pedido ya `CONFIRMADO` → `409 cancelacion-no-permitida`.
- [ ] **B9. Comercio dado de baja.** Darlo de baja y mandar un pedido →
  `201`, pero queda `Descartado` ("El comercio 1 no existe o está dado de
  baja"). **Reactivarlo después**: si queda de baja, todo lo que mande el
  ERP se descarta.

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
  retiro, entrega, repartidor y "Ver recorrido en el mapa" (esa vista es
  del personal; el repartidor ya no tiene ese botón).
- [ ] **D2. Vista del repartidor (en el celular, o con el navegador
  angosto).** Entrar como `demo.repartidor` → cae en
  `repartidor/mis-entregas.xhtml`: dónde retirar, dónde entregar y cuánto
  cobrar.
- [ ] **D3. Hoja de ruta recomendada (ADR-038).** Arriba de Mis
  entregas: retiros primero y entregas por cercanía de código postal;
  "Abrir en Google Maps" abre el recorrido (con más de 9 paradas, en
  tramos).
- [ ] **D4. Retiro y entrega.** "Ya retiré el pedido" → En camino;
  "Entregar pedido" abre una ventana para la foto del remito → Entregado;
  pasa al historial y el repartidor queda libre.
- [ ] **D4b. Devolver a depósito (ADR-037).** En un pedido Confirmado o
  En camino, "Devolver a depósito" con un motivo → vuelve a Pendiente en
  lista de espera y se le asigna solo a otro repartidor; no se cobra de
  nuevo.
- [ ] **D5. Portal del comercio.** Entrar como `demo.comercio` → cae en
  `comercio/mis-pedidos.xhtml`: sus pedidos con estado, cobro y
  repartidor, y un aviso por cada cambio de estado (eso prueba el
  tópico). "Mi stock" muestra lo de A4; "Mis puntos de picking" permite
  agregar, dar de baja y reactivar.

- [ ] **D7. No se cancela lo que salió (ADR-048).** Con un pedido En
  camino, en Pedidos no está el botón Cancelar ("Ya salió a reparto…").
  Devolverlo al depósito con "Cliente ausente": vuelve a Pendiente para
  otra visita y sigue sin poder cancelarse. Por la API, cancelarlo da 409.
- [ ] **D8. Email obligatorio (ADR-039).** Simular un pedido sin email de
  quien recibe: la pantalla lo pide. Por la API, 400 con
  `emailDestinatario` en `errores` (Postman, carpeta 2, paso 9).
- [ ] **D9. Editar y eliminar stock (ADR-049).** En Depósitos y stock →
  un depósito, "Editar" en una fila: cambiar el nombre, la cantidad y el
  peso. Una cantidad menor que la reservada o negativa da error. Con un
  pedido en curso de ese producto, "Eliminar" (solo administrador) lo
  impide y nombra el pedido; cancelado el pedido, se elimina y sus
  reservas siguen en el Historial.
- [ ] **D6. Lista de espera y hora de corte (ADR-037).** Sin lugar en
  ningún repartidor, Confirmar deja el pedido "En espera" sin cobrar; al
  liberarse lugar se asigna solo. Con `rabbit.ruteo.hora-corte=00:01`,
  todo lo que se confirma queda en espera aunque haya lugar; al quitarla
  (vuelve a las 07:00) todo sigue en espera hasta el día siguiente, salvo
  que se pruebe antes de las 7. **Para probar durante el día, `ninguno`.**

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

### I. Escalabilidad (desafío opcional)

La heterogeneidad tecnológica (banco en Node.js, transportista en Python)
se prueba en la [rama `desafio-heterogeneidad`](https://github.com/feliforte14/Rabbit/tree/desafio-heterogeneidad).

- [ ] **I2. Escalabilidad.** Con un comercio y un punto de picking activos:

  ```bash
  export RABBIT_ERP_USUARIO=demo.erp RABBIT_ERP_CLAVE=<contraseña>
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
  no ve "Usuarios", no puede eliminar comercios, repartidores ni zonas
  (no ve el botón), ni cancelar un pedido confirmado.
- [ ] **S3. Datos ajenos.** Como `demo.comercio`, pegar
  `comercio/puntos-picking.xhtml?idComercio=<otro id>` → el parámetro se
  ignora y ve solo los suyos.
- [ ] **S4. ERP sin web.** Entrar a la web con `demo.erp` → el login
  lo rechaza ("no tiene acceso a la aplicación web").
- [ ] **S5. Cuenta sin asociar.** Ya no se puede reproducir desde la app:
  eliminar un comercio que tiene cuentas se rechaza (S8), así que una
  cuenta nunca queda apuntando a un comercio inexistente. Si pasara (por
  ejemplo, por un borrado a mano en la base), el menú dice "Cuenta sin
  asociar" y "Mis puntos de picking" muestra "Tu cuenta no está asociada a
  un comercio activo" (`SesionBean`, `PuntoPickingBean`).
- [ ] **S6. URLs viejas y plantilla.** `https://localhost:8443/Rabbit/pedidos.xhtml`
  y `.../template.xhtml` → página de error, sin detalles internos.
- [ ] **S7. Sin sesión.** Cerrar sesión y abrir `personal/pedidos.xhtml`
  → vuelve al login.
- [ ] **S8. Eliminar comercio.** Darlo de baja primero es obligatorio.
  Con un pedido en curso o entregado, stock consignado o cuentas → se
  rechaza y queda dado de baja. Con solo pedidos cancelados → se elimina
  junto con ese historial (en el log, `[Pedidos] Comercio N eliminado: se
  borraron ...`, y lo mismo de Pagos, Notificaciones y Transportistas).
- [ ] **S9. Reactivar una cuenta.** Usuarios → dar de baja `demo.repartidor`
  → ya no puede entrar. "Reactivar una cuenta" con una contraseña nueva →
  entra con la nueva y no con la vieja. Dar de baja el comercio de
  `demo.comercio`, dar de baja la cuenta e intentar reactivarla → se
  rechaza porque su comercio no está activo.
- [ ] **S10. Eliminar usuario, repartidor y zona (ADR-036).** Como
  administrador: eliminar una cuenta de prueba → desaparece y no se puede
  reactivar; intentar eliminar la propia → no hay botón. Eliminar un
  repartidor con cuenta → "tiene una cuenta de usuario (eliminala primero
  desde Usuarios)"; uno recién creado → se elimina. Eliminar una zona →
  sus repartidores quedan "Sin zona fija".

### Cierre

- [ ] Volver al banco en Java y quitar las system properties de las demos
  (`cli --command="/system-property=*:read-resource"` no debería listar
  ninguna de `rabbit.`).
- [ ] Revisar el log: sin `ERROR` nuevos salvo los provocados a propósito
  (banco caído, rechazos).
- [ ] Si fue un ensayo: limpiar la base con el script de limpieza. En el
  SQL Editor de Supabase cada ejecución es una conexión aparte: el
  `COMMIT;` tiene que ir al final del mismo bloque que se ejecuta (correrlo
  después, solo, no confirma nada).
