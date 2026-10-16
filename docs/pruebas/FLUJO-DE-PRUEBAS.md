# Flujo de pruebas manual — Rabbit

Guía paso a paso para probar la app de punta a punta, con los cinco tipos
de usuario (`ADMINISTRADOR`, `OPERADOR`, `COMERCIO`, `REPARTIDOR`, `ERP`) y
todos los componentes. Sirve tanto para verificar que todo anda como para
la demo.

## Qué rol prueba cada sección

| Rol | Qué puede hacer | Dónde se prueba |
|---|---|---|
| `ADMINISTRADOR` | Todo lo del personal, **más** dar de alta/baja usuarios, eliminar (no solo dar de baja) comercios, usuarios, repartidores y zonas, liquidar el mes, registrar una entrega con remito como contingencia y anular el cobro de un pedido ya `CONFIRMADO` en adelante | 1, 2, 5, 6, 6b, 6c, 7b, 9, 10, 11, 12 |
| `OPERADOR` | Todo lo del personal **salvo** esas operaciones — no ve los botones y el EJB las rechaza con `EJBAccessException` | 1b, 10, 11, 12 |
| `COMERCIO` | Solo lo suyo: sus pedidos (con el remito de cada entrega), sus indicadores, su stock (solo lectura), sus puntos de picking y sus liquidaciones (y el aviso cuando un pedido vuelve al depósito o al comercio) | 3, 7, 10, 11, 12 |
| `REPARTIDOR` | Solo su hoja de ruta (la recomendada, con todas sus paradas): retirar, entregar con foto del remito, devolver al depósito con motivo y compartir su ubicación | 5 (paso 3), 10, 11, 12 |
| `ERP` | Solo la API REST, solo los pedidos de su comercio | 4, 12 |

Requisitos previos:
- WildFly corriendo con `standalone-full.xml` (necesario por JMS/MDBs):
  ```bash
  ~/wildfly/bin/standalone.sh -c standalone-full.xml
  ```
- App deployada: `mvn clean package wildfly:deploy` desde la raíz del repo.
- App disponible en `https://localhost:8443/Rabbit` (HTTPS obligatorio; por
  `http://localhost:8080` redirige solo). El certificado local es
  autofirmado: aceptar la advertencia del navegador y usar `curl -k`.

Estado de la base: puede tener datos de corridas de prueba anteriores (no
hace falta limpiarla para seguir esta guía — los pasos de alta validan
duplicados y, si el dato ya existe, usá el que está en vez de crear uno
nuevo). Si preferís arrancar de cero, los pasos 2.1 a 2.5 dan de alta todo
lo que hace falta.

Credenciales del realm de WildFly: `claude-cb-admin` (rol
`ADMINISTRADOR`). Si no tenés la contraseña (se perdió o nunca se generó),
reseteala vos mismo — es una cuenta de prueba, no de un usuario real:
```bash
~/wildfly/bin/add-user.sh -a -u claude-cb-admin -p '<una contraseña nueva>' -g ADMINISTRADOR -s
```
El resto de las cuentas (operador, comercio, repartidor, ERP) se crean
desde la propia app en los pasos 1b y 2.4/2.5 — no hace falta `add-user.sh`
para esas, y como quedan en la base compartida, entran también en el
WildFly de los demás (ADR-031).

**Cuentas ERP viejas:** las creadas a mano con `add-user.sh -g ERP` (por
ejemplo `claude-cb-erp`) no tienen comercio: la API les responde `403`
("Cuenta ERP sin comercio"). Hay que crear una nueva desde la app (2.5).

**Una línea de log que podés ignorar:** al cargar **Pedidos**
(`personal/pedidos.xhtml`) vas a ver un `SEVERE`
`parseBigDecimal="true" Unhandled by MetaTagHandler for type
jakarta.faces.convert.NumberConverter`. Es un wart conocido de esta
versión de Mojarra (el atributo igual se aplica correctamente — el campo
Importe sigue parseando a `BigDecimal` sin problema); no indica que algo
haya fallado. Está documentado como comentario en el propio
`pedidos.xhtml`.

---

## 1. Login como administrador

1. Entrá a `https://localhost:8443/Rabbit/login.xhtml`.
2. Ingresá con `claude-cb-admin`.
3. Deberías caer en `personal/pedidos.xhtml` (la pantalla de Pedidos).

**Qué mirar:** el menú lateral muestra "Administrador" como rol; no
aparece ningún error.

---

## 1b. Login como operador (permisos acotados)

El `OPERADOR` ve y usa las mismas pantallas que el `ADMINISTRADOR`
(Comercios, Depósitos y stock, Pedidos, Repartidores, Transportistas,
Ruteo) — **no** ve **Usuarios** (es solo `ADMINISTRADOR`) — y tiene
exactamente tres operaciones bloqueadas por rol, no por la vista:

1. Como administrador, en **Usuarios**, creá una cuenta:
   - Usuario: `demo.operador`
   - Tipo de cuenta: `Operador`
2. Cerrá sesión y entrá con `demo.operador`.
3. **Qué mirar en el menú:** no aparece la opción **Usuarios**. Si pegás
   la URL directo (`personal/usuarios.xhtml`), te redirige sin mostrar la
   pantalla (igual que el 7 para `demo.comercio`).
4. Las tres operaciones que el operador **no puede** hacer:
   - **Comercios** → en un comercio dado de baja, el botón **Eliminar**
     (no **Dar de baja**, que sí puede): falla con un mensaje de negocio
     (no el stack trace), porque el EJB rechaza con `EJBAccessException`
     antes de llegar a `eliminarComercio`.
   - **Pedidos** → cancelar un pedido que ya está `CONFIRMADO` (o más
     adelante): falla igual, porque cancelar desde ahí pasa por
     `PagoService.anularCobro` (solo `ADMINISTRADOR`). Cancelar un pedido
     todavía `PENDIENTE` sí funciona (no involucra un cobro para anular).
   - **Usuarios**: ni siquiera se ve el menú, y entrar por URL redirige.
5. Todo lo demás (alta de comercio/depósito/stock/repartidor, confirmar,
   despachar, entregar, derivar a un transportista, zonas y despacho)
   funciona igual que con `ADMINISTRADOR` — probalo con el mismo pedido
   del paso 2 para confirmar que el flujo normal no está restringido.

**Qué mirar:** el mensaje de error en los dos casos bloqueados es de
negocio ("no podés eliminar...", "no podés cancelar..."), nunca la
excepción cruda `EJBAccessException` ni una pantalla de error genérica.

---

## 2. Alta de datos base (como administrador u operador)

### 2.1 Crear un comercio
1. Ir a **Comercios** → formulario "Registrar nuevo comercio".
2. Cargar nombre, razón social, CUIT (formato `XX-XXXXXXXX-X`), email y
   teléfono. Botón **Registrar**.
3. Verificar que aparece en el listado con estado "Activo". Si la base ya
   tenía un comercio con ese CUIT, el alta rechaza con "Ya existe un
   comercio registrado con el CUIT ..." — usá ese comercio existente en
   vez de inventar otro CUIT.

### 2.2 Crear un depósito y algo de stock
1. Ir a **Depósitos y stock** → "Registrar nuevo depósito" (nombre,
   dirección, provincia, localidad, código postal).
2. En la fila del depósito, **Ver stock** → "Cargar stock": elegir el
   comercio dueño, producto y cantidad. Anotá el **ID** del ítem que
   aparece en la tabla: lo usa el pedido del paso 4.

### 2.3 Crear un repartidor
1. Ir a **Repartidores** → "Registrar repartidor".
2. Cargar nombre y teléfono (y, si ya hay zonas, su zona). Botón
   **Registrar**. Sin repartidores disponibles, un pedido no se puede
   confirmar: solo derivar a un transportista (6b).

### 2.4 Crear las cuentas de usuario (login) para comercio y repartidor
1. Ir a **Usuarios** (solo visible para `ADMINISTRADOR`).
2. Crear un usuario con:
   - Usuario: `demo.comercio` (o el que prefieras)
   - Contraseña: la que quieras
   - Tipo de cuenta: `Comercio`
   - Comercio: el que creaste (o reusaste) en 2.1
3. Repetir para el repartidor:
   - Usuario: `demo.repartidor`
   - Tipo de cuenta: `Repartidor`
   - Repartidor: el que creaste en 2.3

**Por qué una cuenta nueva y no reusar una vieja sin asociar:** una cuenta
cuyo comercio o repartidor se borró queda "sin asociar" (la app no permite
reasociarla). Dar de alta desde acá crea la cuenta (con su contraseña) y
la asociación en la base al mismo tiempo — sin eso, cualquier pantalla
del comercio o repartidor muestra "cuenta no asociada" (ver sección 8).

### 2.5 Cuenta del ERP (para la API REST)
Igual que las anteriores, desde **Usuarios**:
   - Usuario: `demo.erp`
   - Tipo de cuenta: `ERP (API REST)`
   - Comercio: el que creaste en 2.1

La cuenta ERP representa a ese comercio: por la API solo carga y ve sus
pedidos. No puede entrar a la web.

---

## 3. Portal del comercio

1. Cerrar sesión, loguearte con `demo.comercio`.
2. Caés en `comercio/mis-pedidos.xhtml`: debería estar vacío (o con los
   pedidos que ya tenga ese comercio), sin errores.
3. Ir a **Mis puntos de picking** → agregar uno (nombre, dirección).
4. Ir a **Mi stock** → confirmar que ves el stock cargado en 2.2. Es solo
   lectura: el stock lo carga el personal de Rabbit.

**Seguimiento del cliente final:** en **Mis pedidos**, cada pedido tiene
su código (`RB-...`) y el enlace **Ver lo que ve el cliente**, que abre
`seguimiento.xhtml` (pública, sin login) con el estado y una línea de
tiempo. También se llega desde el login ("Seguí tu pedido") escribiendo el
código; uno inexistente muestra "No encontramos ese código".

**En el celular:** las tablas del portal se ven como tarjetas (un dato por
renglón) y el menú queda arriba, compacto.

**Qué mirar:** el título dice "Mis puntos de picking" / "Mis pedidos", no
pide elegir comercio (a diferencia del personal, que sí lo elige de un
listado).

---

## 4. Entrada de pedidos por REST (simulando al ERP)

Con la cuenta `demo.erp` (Basic Auth):

```bash
curl -ik -u demo.erp:<contraseña> \
  -X POST https://localhost:8443/Rabbit/api/v1/pedidos-externos \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "origen": "STOCK_CONSIGNADO",
    "lineas": [{"idItem": 1, "cantidad": 1}],
    "importe": 2500,
    "medioPago": "PREPAGO",
    "direccionEntrega": "Av. Corrientes 1234, CABA",
    "codigoPostalEntrega": "1043"
  }'
```

- Ajustá `idItem` al ID real del stock del comercio (tabla de stock). El
  comercio no va en el cuerpo: es el de la cuenta `demo.erp`.
- `direccionEntrega` es obligatoria: sin ella la API responde `400` con
  `errores` indicando el campo.
- `codigoPostalEntrega` es opcional (4 dígitos o CPA); si falta, se toma
  de la dirección cuando la trae. Lo usa el Ruteo por zona (sección 6c).
  Con otro formato (`"12"`) responde `422`: el JSON está bien, pero no es
  un código postal argentino (regla de negocio, no de formato).
- Debería responder `201 Created` con `idPedidoExterno`, `Location` y
  `_links`.

Consultar cómo terminó:
```bash
curl -k -u demo.erp:<contraseña> \
  https://localhost:8443/Rabbit/api/v1/pedidos-externos/<idPedidoExterno>
```
Estados posibles: `Pendiente` → `Sincronizado` (con `idPedido`,
`estadoPedido` y `codigoSeguimiento`), `Descartado` (con motivo) o
`Cancelado` (solo si se canceló antes de salir de la cola; si ya era un
pedido, queda `Sincronizado` con `estadoPedido: CANCELADO`). La conversión es asincrónica (cola JMS), así que puede tardar
un instante en pasar de Pendiente a Sincronizado.

### 4.1 Lo que tiene que mostrar la API

| Prueba | Cómo | Esperado |
|---|---|---|
| Idempotencia | Repetir el mismo `curl` con la **misma** `Idempotency-Key` (fijala en una variable: `K=$(uuidgen)`) | `201` con el **mismo** `idPedidoExterno`; en Pedidos hay uno solo |
| Reintentos simultáneos | La misma clave en varios `curl` en paralelo | Todos `201` con el mismo id; en el log puede aparecer un `WFLYEJB0034` por la restricción única (esperado) |
| Clave reutilizada | Misma clave, otro `importe` | `422`, `type: /problemas/clave-idempotencia-reutilizada` |
| Sin clave | Sacar el header `Idempotency-Key` | `400`, `type: /problemas/clave-idempotencia-invalida` |
| Formato inválido | `"importe": 0` y sin `direccionEntrega` | `400` con `errores` (un mensaje por campo) |
| Regla de negocio | `"origen": "PUNTO_PICKING"` sin `idPuntoPicking`, o `"codigoPostalEntrega": "12"` | `422`, `type: /problemas/datos-invalidos` |
| Sin stock | `"cantidad": 9999` | `201`, y al consultarlo `Descartado` con el motivo ("se pidieron 9999 y hay N libres"); no se crea pedido ni se toca el stock |
| Comercio dado de baja | Dar de baja el comercio y mandar un pedido | `201`, y al consultarlo `Descartado` ("El comercio N no existe o está dado de baja"). Reactivarlo después |
| JSON roto | `-d '{"importe":'` | `400`, `type: /problemas/cuerpo-invalido` |
| Pedido de otro comercio | `GET` de un `idPedidoExterno` de otro comercio | `404` (no se revela que existe) |
| Cancelar | `curl -i -X POST -u demo.erp:<contraseña> .../v1/pedidos-externos/<id>/cancelacion` | `200` (`Sincronizado` con `estadoPedido: CANCELADO`, sin link `cancelar`); repetirlo da `200` igual |
| Cancelar tarde | Lo mismo con el pedido ya `CONFIRMADO` | `409`, `type: /problemas/cancelacion-no-permitida` |
| Seguimiento público | `curl -ik https://localhost:8443/Rabbit/api/v1/seguimiento/<codigoSeguimiento>` | `200 {"codigoSeguimiento", "estado"}`, sin login |
| URL vieja | `curl -i .../Rabbit/api/pedidos-externos/1` | `404` en `application/problem+json` (la API es `/v1`) |

**Sobre el `409` de cancelar tarde:** puede salir con dos tipos distintos
de `Problema` — `cancelacion-no-permitida` (el pedido ya avanzó, es
definitivo) o, más raro, `stock-en-conflicto` (la devolución de stock
chocó con otra sesión tocando el mismo ítem al mismo tiempo — ahí sí vale
la pena reintentar la cancelación, el conflicto es pasajero).

El contrato está en [openapi.yaml](../integraciones/openapi.yaml): pegalo en
[Swagger Editor](https://editor.swagger.io). Para probar con Postman está
la colección armada
[`Rabbit.postman_collection.json`](Rabbit.postman_collection.json), con
todos estos casos en orden (ver
[PRUEBAS-Y-COMANDOS.md](PRUEBAS-Y-COMANDOS.md#colección-de-postman)).

**Qué mirar:** en el log de WildFly deberían verse las trazas de
`PedidoExternoListener` procesando el mensaje de la cola
`cola.pedidos.externos` (`[Pedidos][JMS] Pedido externo N sincronizado en
tiempo real`). En **Pedidos → Recibidos del ERP** el pedido aparece como
"Sincronizado".

---

## 5. Ciclo completo de un pedido (cola + tópico + SOAP + transacción)

1. Como administrador u operador, en **Pedidos**, botón **Confirmar** del
   pedido creado en el paso 4. Confirmar es del personal de Rabbit: el
   comercio no puede. En la misma transacción:
   - si el medio de pago es `PREPAGO`, se cobra en el banco legado por SOAP
     (`BancoLegadoService`, WSDL en
     `http://localhost:8080/Rabbit/BancoLegadoService?wsdl`);
   - se asigna un repartidor con lugar. Si ninguno tiene (o ya pasó la
     hora de corte), el pedido queda en la **lista de espera**, sin
     cobrar, y se asigna solo cuando se libera alguno (ADR-037).
2. En **Entregas en curso** aparece la hoja de ruta del pedido (retiro →
   entrega).
3. Logueate como `demo.repartidor` → **Mis entregas**: arriba, la **hoja
   de ruta recomendada** con todas sus paradas en orden (retiros primero)
   y **Abrir en Google Maps** (ADR-038). En cada pedido, **Ya retiré el
   pedido** y después **Entregar pedido**, que abre una ventana para
   subir la foto del remito.
4. Logueate como `demo.comercio` → **Mis pedidos**: el pedido figura
   "Entregado", con su **código de seguimiento para el cliente** y el
   enlace "Ver lo que ve el cliente" (la API pública), y en "Avisos de
   Rabbit" aparece un aviso por cada cambio de estado. Eso confirma que el tópico `topico.pedidos.estado` distribuye
   los cambios a sus suscriptores (Notificaciones y Pagos).

**Qué mirar:** en **Pedidos** (vista del personal) el pedido pasa por
Pendiente → Confirmado → En camino → Entregado, y la columna Cobro por
"Cobrado" (prepago) o "A cobrar" → "Cobrado" (contra entrega, se acredita
al entregar).

**Variantes para probar la transacción:** un pedido `PREPAGO` de más de
$500.000 lo rechaza el banco y sigue Pendiente; un pedido `PREPAGO` sin
repartidor con lugar ya no se cobra: va a la lista de espera (antes de
cobrar se consulta si hay lugar, ADR-037). La reversa del banco
(`[Banco legado] Reversado ...`) queda para cuando otro se queda con el
lugar justo entre esa consulta y la asignación, y al cancelar un pedido
prepago confirmado.

---

## 6. Circuit breaker frente al banco caído

Con WildFly corriendo:

```bash
# Simular la caída del banco (tarda 10 s en vez de responder, más que el timeout)
~/wildfly/bin/jboss-cli.sh --connect \
  --command="/system-property=rabbit.banco.simular.caida:add(value=true)"
```

1. Como administrador, en **Pedidos**, confirmá 4 veces seguidas un pedido
   `PREPAGO` pendiente (puede ser el mismo: con el banco caído no se
   confirma y sigue Pendiente).
2. Los primeros 3 deberían tardar ~5 s cada uno (timeout) y fallar.
3. El 4.º debería fallar **al instante** — el circuito ya está `ABIERTO` y
   ni siquiera llama al banco.
4. En el log de WildFly buscá las líneas con el prefijo
   `[Pagos][Circuito]`: deberían mostrar la transición CERRADO → ABIERTO.

```bash
# Restaurar el banco
~/wildfly/bin/jboss-cli.sh --connect \
  --command="/system-property=rabbit.banco.simular.caida:remove"
```

5. Esperá 30 segundos (umbral configurado) y confirmá un pedido más: el
   circuito debería pasar a `SEMIABIERTO`, dejar pasar esa llamada de
   prueba y, si responde bien, volver a `CERRADO` — visible en el log.

---

## 6b. Derivar un pedido a un transportista

1. Como administrador u operador, **Transportistas** → "Registrar
   transportista":
   - uno REST: endpoint
     `http://localhost:8080/Rabbit/api/simulador/transportista-rest`;
   - uno SOAP legado: endpoint
     `http://localhost:8080/Rabbit/TransportistaLegadoService?wsdl`.
2. En **Pedidos** → "Derivar a un transportista": elegí un pedido pendiente
   y tocá **Cotizar** (sin elegir transportista). Aparece la tabla de
   cotizaciones: el REST cotiza (por ejemplo, 2 bultos prepago = $3.200,
   24 h) y el legado figura "No cotiza (sistema legado)". Tocá **Derivar
   con este** en el que quieras (o elegilo en el desplegable y
   **Derivar**). El mensaje muestra el código de seguimiento, y en la
   tabla el pedido figura Confirmado con el transportista en lugar del
   repartidor.
3. Esperá: cada 15 s Rabbit consulta al transportista. A los ~20 s el
   pedido pasa a **En camino** y a los ~40 s a **Entregado** (en
   Transportistas, el envío pasa por En tránsito y Entregado). Si era
   contra entrega, el cobro pasa a Cobrado.
4. Derivá otro y cancelalo enseguida desde Pedidos: en el log aparece
   `[Transportista ...] Envío ... cancelado`. Esto bloquea el pedido y
   después el envío — el mismo orden que usan las novedades del
   transportista (ver más abajo), así que no hay riesgo de que se crucen.

**Qué mirar:** en el log, `[Transportistas]` con cada novedad; el
transportista legado responde `EN_VIAJE` y Rabbit lo muestra como
"En tránsito" (lo traduce su adaptador).

## 6c. Ruteo por zona

Necesita los dos transportistas de la sección 6b.

1. En **Ruteo por zona** → "Registrar zona":
   - "Norte", códigos postales 1400-1499, reparten repartidores propios,
     transportista de respaldo: el SOAP legado;
   - "Sur", 1800-1899, reparte un transportista: el REST.
   Probá también los rechazos: un rango que se superpone con Norte, uno
   invertido (1499-1400) y una zona de transportista sin transportista.
2. En **Repartidores**, poné un repartidor en la zona Norte ("Cambiar la
   zona", o elegila al darlo de alta).
3. Simulá (en **Pedidos**) o mandá por la API cuatro pedidos: dos con
   código postal 1414 y 1426, uno con la dirección "Calle 12 1846, Adrogué
   (1846)" sin código postal, y uno sin código postal en ninguna parte.
4. En **Ruteo por zona**: Norte tiene dos, Sur uno (el CP salió de la
   dirección) y "Sin zona" uno, sin botón Despachar.
5. "Despachar toda la zona" en Norte: el primero sale con el repartidor de
   la zona; el segundo, como ya no hay repartidores libres, se deriva al
   transportista de respaldo. "Despachar" el de Sur: se deriva al
   transportista REST. La tabla "Resultado del despacho" muestra qué pasó
   con cada uno y el código de seguimiento.

**Qué mirar:** los derivados siguen solos hasta Entregado (como en 6b); el
de sin zona se confirma o deriva a mano desde Pedidos.

---

## 7. Seguridad: accesos restringidos

Con `demo.comercio` logueado:
- Intentá entrar directo a `https://localhost:8443/Rabbit/personal/usuarios.xhtml`
  (pantalla solo `ADMINISTRADOR`): debería redirigirte, no mostrar la
  pantalla. Con `demo.operador` pasa lo mismo (ver 1b).
- Intentá pegar la URL de puntos de picking de otro comercio
  (`comercio/puntos-picking.xhtml?idComercio=<otro id>`): como usuario COMERCIO el
  parámetro se ignora y siempre ves el tuyo — confirmá que no aparecen
  datos ajenos.

Contra la API del ERP:
```bash
curl -ik https://localhost:8443/Rabbit/api/v1/pedidos-externos/1                            # sin credenciales
curl -ik -u claude-cb-admin:<contraseña> https://localhost:8443/Rabbit/api/v1/pedidos-externos/1   # usuario sin rol ERP
curl -i http://localhost:8080/Rabbit/api/v1/seguimiento/X                                     # por HTTP
```
Debería responder `401` sin credenciales y `403` con un usuario válido que
no tiene el rol `ERP`. Esos dos vienen en HTML (`<body>Unauthorized</body>`)
y no en Problem Details: los responde WildFly antes de que el pedido
llegue al código de Rabbit (seguridad declarativa). Por HTTP responde
`302` a la misma URL en HTTPS (puerto 8443; en Postman hay que apagar
*Automatically follow redirects* para verlo). La cookie de sesión del login sale con `Secure` y `HttpOnly`.

---

## 7b. Límite de intentos de login

1. En el login, poner 5 veces una contraseña incorrecta para un usuario.
2. Al quinto, y por 15 minutos, cualquier intento (aun con la contraseña
   correcta) responde "Demasiados intentos fallidos. Probá de nuevo en N
   minutos". El mensaje es el mismo para un usuario que no existe.

## 8. Mensaje de cuenta sin asociar (regresión ya corregida)

Para confirmar el fix reciente: creá un usuario nuevo con rol `Comercio`
pero **sin** elegir el desplegable de comercio... en realidad el formulario
lo exige, así que para reproducir el caso hay que dar de baja el comercio
asociado a una cuenta ya creada (o borrarlo) y volver a entrar como esa
cuenta a **Puntos de picking**.

**Resultado esperado:** el mensaje
*"Tu cuenta no está asociada a un comercio activo. Contactá a un
administrador."* — no el mensaje interno `Comercio no encontrado: <id>`.

---

## 9. Demos de los desafíos opcionales

Los pasos de estas demos están en
[DESAFIOS-OPCIONALES.md](../consigna/DESAFIOS-OPCIONALES.md):

- **Escalabilidad:** `scripts/prueba_escalabilidad.py` manda 100 pedidos
  por la API v1 y mide cuánto tarda la cola con 1, 4 y 8 consumidores.
  Necesita un usuario `ERP` creado desde la app (2.5) y un punto de
  picking activo de su comercio.
- **Heterogeneidad:** está en la [rama `desafio-heterogeneidad`](https://github.com/feliforte14/Rabbit/tree/desafio-heterogeneidad) (banco en Node.js y
  transportista en Python), con sus pasos en esa misma guía.

---

## 10. Mejoras del tablero: peso, capacidad, remito, mapa y liquidación

Probado de punta a punta el 15/10/2026 (ADR-031 a 036). Lo que cambió el
16/10 (lista de espera, devolución y hoja de ruta recomendada) está en la
sección 11. Con pedidos
`[TEST]`, así la opción A de `scripts/Rabbit-limpieza-datos-de-prueba.sql`
los borra después.

1. **Peso:** en Depósitos y stock → un depósito; "Cargar stock" pide el
   peso por unidad (obligatorio) y "Corregir peso" lo cambia. La tabla lo
   muestra como "1 kg" / "0,9 kg". Un pedido de stock consignado (por
   ejemplo con "Simular un pedido del ERP") se sincroniza solo y muestra
   su peso en Pedidos ("· 3 kg"); en "Recibidos del ERP" aparece con el
   nombre del producto. Por la API, un pedido de punto de picking con
   `"pesoKg": 30` guarda 30 kg; con stock consignado y `pesoKg` responde
   422.
2. **Capacidad:** en Repartidores → "Capacidad del vehículo": 60 kg y 20
   bultos. Confirmar dos pedidos de 30 y 25 kg: los dos quedan con el
   mismo repartidor ("Lleva: 2 pedidos (55 kg, 5 bultos)"). Uno de 10 kg
   más: queda **en lista de espera** (ver la sección 11).
   Al entregar uno, se libera su lugar y el que esperaba se asigna solo.
3. **Remito:** como el repartidor, en Mis entregas: "Ya retiré el pedido"
   y después "Entregar pedido", que abre una ventana para la foto. Sin
   foto, el navegador no deja enviar; con un archivo que no es imagen →
   "tiene que ser una imagen JPG, PNG o WEBP"; con la foto → entregado. "Ver remito" aparece en
   Pedidos (personal), Mis pedidos (comercio) y el historial del
   repartidor. Un OPERADOR ya no puede marcar entregado; un ADMINISTRADOR
   sí, con foto, en "Registrar una entrega" de Pedidos.
4. **Mapa:** con el pedido EN_CAMINO y Mis entregas abierta en el celular
   (permitir la ubicación), el personal ve al repartidor en "Mapa de
   repartidores", el cliente en `seguimiento.xhtml?codigo=RB-...` y la API
   `GET /api/v1/seguimiento/{codigo}` trae `ubicacionRepartidor`.
5. **Liquidación:** en "Liquidaciones mensuales", el mes en curso es una
   vista previa (abono + envíos + kilos). Un mes cerrado: "Liquidar y
   cobrar este mes" → COBRADA con código de autorización del banco (el
   botón solo aparece en meses cerrados). El comercio ve en "Mis
   liquidaciones" la vista previa del mes en curso (solo lo suyo) y los
   meses ya liquidados. (Para no dejar una liquidación de prueba en la
   base compartida, borrarla después:
   `DELETE FROM liquidaciones WHERE periodo = 'AAAA-MM'`.)
6. **Eliminar (solo administrador, ADR-036):** el botón "Eliminar" está en
   Usuarios, Repartidores y Ruteo por zona (zonas); un OPERADOR no lo ve.
   - Usuario: se borra y no se puede reactivar. La propia cuenta no se
     puede eliminar, ni el último administrador activo.
   - Repartidor: con cuenta de usuario → "tiene una cuenta de usuario
     (eliminala primero desde Usuarios)"; con entregas en curso, también
     se rechaza. Uno recién creado se elimina.
   - Zona: se elimina y sus repartidores quedan "Sin zona fija".

## 11. Correcciones del equipo: lista de espera, hora de corte, devolución y hoja de ruta

Probado de punta a punta el 16/10/2026 (ADR-037 y 038), con un
repartidor de prueba de 3 kg además de Juan Perez (10 kg, con 9 kg
cargados) y pedidos `[PRUEBA]` de 1 y 2 kg.

1. **A otro repartidor y lista de espera:** confirmar uno de 2 kg → como
   a Juan no le entra, se le asigna al de 3 kg. Otro de 2 kg → "Pedido N
   en lista de espera: Ningún repartidor tiene lugar para 2 kg y 2
   bultos…" y en Pedidos figura "En espera", debajo "Desde el …" y el
   motivo, Cobro "—" (no se cobró) y el botón "Reintentar". Si lo devolvió
   un repartidor, se ve además "Devuelto: <motivo>" y el motivo de la
   espera aclara que no vuelve al que lo devolvió. Al cancelar o entregar el primero, en unos segundos
   el que esperaba pasa solo a Confirmado.
2. **Hora de corte:** `cli --command="/system-property=rabbit.ruteo.hora-corte:add(value=00:01)"`
   y confirmar uno de 1 kg aunque Juan tenga lugar → en espera, "Pasó la
   hora de corte (00:01): se asigna mañana". En Ruteo por zona,
   "Despachar" muestra "En espera". Al quitar la propiedad vuelve el corte
   de las 21:00 y, si es antes de esa hora, en menos de un minuto se
   asigna solo. **Quitarla al terminar.** Si probás después de las 21,
   todo queda en espera aunque no configures nada: para probar de noche,
   `value=ninguno`.
3. **Devolver a depósito:** como el repartidor, en un pedido Confirmado o
   En camino → "Devolver a depósito" → motivo ("Otro" pide el detalle).
   El pedido sale de su lista, vuelve a Pendiente en espera y en unos
   segundos se le asigna a **otro** repartidor (nunca al que lo devolvió).
   Si era prepago, no se cobra dos veces (en el log, un solo
   `[Pagos] Cobro … del pedido N`). El comercio recibe "volvió al
   depósito". Forzar el formulario con un pedido ajeno → "no está
   asignado a vos".
4. **Hoja de ruta recomendada:** arriba de Mis entregas, primero los
   retiros pendientes y después las entregas por cercanía de código
   postal; dos pedidos a la misma dirección son una sola parada
   ("Pedido #353, #354"). "Abrir en Google Maps" lleva el recorrido; con
   más de 9 paradas aparecen varios tramos. Ya no están "Ver recorrido en
   el mapa" ni la sección "Registrar una entrega".

## 12. Mejoras frente a los líderes: destinatario, avisos, intentos, promesa, horario e indicadores

En la rama `feature/mejoras-lideres-ultima-milla` (ADR-039 a 044).
**Todavía sin probar de punta a punta en WildFly**: los tests unitarios
pasan (98), pero esto es lo que hay que recorrer antes de mergear. Antes
de desplegar, mejor que no haya cobros contra entrega pendientes de
acreditar: el MDB de Pagos cambia de selector y Artemis recrea su
suscripción (ADR-041).

1. **Quién recibe (ADR-039):** en Pedidos → "Simular un pedido del ERP",
   cargar nombre, teléfono y un email propio. En la tabla, debajo de la
   dirección, "Recibe: …" y "Prometido para el dd/MM". Por la API, mandar
   `nombreDestinatario`, `telefonoDestinatario` y `emailDestinatario`; un
   email sin @ responde 400 con el campo. Sin esos campos, el alta sigue
   igual que antes. El repartidor ve "Recibe: … · teléfono" (el teléfono
   abre la llamada en el celular). El seguimiento público **no** los
   muestra.
2. **Avisos a quien recibe (ADR-040):** con el email cargado, cada cambio
   de estado suma una fila en Pedidos → "Avisos a destinatarios" ("Recibimos
   tu pedido de …", "…ya tiene quién lo lleve", "…llega hoy!",
   "Entregamos…"), con el link al seguimiento. Con
   `rabbit.mail.smtp.host` configurado, llegan por mail. Un pedido sin
   email no genera avisos. En el log:
   `[Notificaciones] Aviso al destinatario del pedido N: …`.
3. **Fecha prometida (ADR-042):** la respuesta del POST ya trae
   `fechaPrometida` (hoy si es antes de las 21:00). Con
   `rabbit.ruteo.hora-corte=00:01`, un pedido nuevo trae la de mañana y el
   seguimiento dice "Llega mañana". Sin código postal, no viene. Quitar la
   propiedad al terminar.
4. **Entrega fallida con intentos (ADR-041):** como repartidor, con un
   pedido En camino → "Devolver a depósito" → "Cliente ausente". Vuelve a
   la espera; en Pedidos dice "1 visita(s) fallida(s)" y el cliente recibe
   "No pudimos entregar tu pedido". Al reasignarlo, la tarjeta del
   repartidor dice "Visita 2 de 2: la anterior falló (Cliente ausente)".
   Retirarlo y devolverlo de nuevo → pasa a **Devuelto al comercio**: el
   stock consignado vuelve (ver Depósitos y stock), el cobro pasa a
   Anulado (en el log, `[Pagos] … anulado: el pedido volvió al comercio`)
   y, si era prepago, se pide la reversa al banco. Con "El cliente rechazó
   el pedido" vuelve al comercio en la primera visita. "No entra en el
   vehículo", o devolverlo desde Confirmado, no gasta una visita. El ERP
   ve `estadoPedido: DEVUELTO_AL_COMERCIO` e `intentosFallidos`.
5. **Horario estimado (ADR-043):** con dos o tres pedidos del mismo
   repartidor, retirar uno y abrir su seguimiento: "Sos la parada N de M:
   llega aproximadamente entre las HH:mm y las HH:mm". La API devuelve
   `llegadaEstimada`. El número coincide con la hoja de ruta recomendada
   del repartidor. Si la estimación falla, la página se ve igual, sin
   horario (y queda un aviso en el log).
6. **Indicadores (ADR-044):** Personal → Indicadores: a tiempo, al primer
   intento, horas de recibido a entregado, en espera, costo por envío,
   motivos de las visitas fallidas y la tabla por repartidor; filtrar por
   comercio y por 7, 30 o 90 días (queda en la URL). Como comercio →
   Mis indicadores: lo mismo, solo lo suyo y sin la tabla por
   repartidor. Sin entregas en el período, los porcentajes dicen "—".

## Checklist rápido

| Ítem | Resultado esperado |
|---|---|
| Login admin | Entra a personal/pedidos.xhtml sin error |
| Login operador | Mismas pantallas que admin, sin **Usuarios** en el menú |
| Operador intenta eliminar comercio / cancelar pedido confirmado | Rechazado con mensaje de negocio, no una excepción cruda |
| Alta comercio/depósito/stock/repartidor | Aparecen en sus listados; CUIT duplicado se rechaza con mensaje claro |
| Alta de usuarios operador/comercio/repartidor/ERP | Cuenta creada, loguea, ve solo lo suyo (o nada, si es ERP) |
| POST /api/v1/pedidos-externos (ERP) | 201 + Location, luego Sincronizado; reintento con la misma clave no duplica |
| Ciclo pedido completo | Pasa por todos los estados hasta ENTREGADO |
| Circuit breaker | 3 fallas → ABIERTO → corta instantáneo → SEMIABIERTO a los 30s |
| Derivar a un transportista | Confirmado con código de seguimiento; pasa solo a En camino y Entregado |
| Cancelar un pedido derivado | Se cancela sin cruzarse con una novedad del transportista en curso |
| Ruteo por zona | Pedidos agrupados por CP; despachar usa el repartidor de la zona, el respaldo o el transportista de la zona |
| Eliminar un comercio dado de baja | Con pedidos en curso o entregados, stock o cuentas: rechazado. Con solo pedidos cancelados: se borra junto con ese historial, sin dejar nada huérfano |
| Eliminar usuario / repartidor / zona (admin) | Se borra; un repartidor con cuenta o entregas en curso se rechaza; el operador no ve el botón |
| Capacidad del repartidor | Si el primero no tiene lugar, se le asigna a otro; si nadie, queda en lista de espera sin cobrar y se asigna solo al liberarse uno |
| Hora de corte (21:00, `rabbit.ruteo.hora-corte`) | Después del corte, lo que se confirma queda en espera para el día siguiente; con `ninguno`, no hay corte |
| Devolver a depósito (repartidor) | Vuelve a Pendiente en espera y se reasigna a otro repartidor, sin cobrar de nuevo |
| Hoja de ruta recomendada | Retiros primero, entregas por CP, link a Google Maps en tramos de hasta 9 paradas |
| Quién recibe (opcional) | Se ve en Pedidos, Mis pedidos y Mis entregas; no en el seguimiento público. Email inválido: 400 |
| Avisos a quien recibe | Una fila por cambio de estado en "Avisos a destinatarios" (y mail si hay SMTP); sin email, nada |
| Fecha prometida | Viene en el 201 del alta; hoy antes del corte, mañana después |
| Visitas fallidas | La primera vuelve a la espera; la segunda (o un rechazo) pasa a Devuelto al comercio, con stock devuelto y cobro anulado |
| Horario en el seguimiento | En camino: "parada N de M" y una franja; si falla, sin horario y sin error |
| Indicadores | Personal (red o comercio, por repartidor) y comercio (lo suyo); "—" sin base |
| Navegar un rato largo (más de 30 min) y después derivar o abrir Entregas en curso | Funciona: las consultas de stock no dependen de una instancia stateful que vence (ADR-027) |
| Acceso restringido a personal/usuarios.xhtml | Comercio/repartidor/operador no puede entrar |
| REST sin credenciales | 401 (HTML de WildFly) |
| REST con rol incorrecto | 403 (HTML de WildFly) |
| REST con código postal inválido | 422 (regla de negocio) |
| Pedido del ERP sin stock o de un comercio dado de baja | 201 y después Descartado, con el motivo |
| Celular (comercio y repartidor), con los 3 temas | Menú desplegable, tablas como tarjetas, botones de 44 px, nada desborda |
| Cancelar pedido externo tarde | 409 `cancelacion-no-permitida` (definitivo) o `stock-en-conflicto` (reintentar) |
| Cuenta sin comercio asociado | Mensaje amigable, no el interno |
