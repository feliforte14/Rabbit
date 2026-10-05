# Flujo de pruebas manual — Rabbit

Guía paso a paso para probar la app de punta a punta con la base ya limpia
(sin datos de prueba). Sirve tanto para verificar que todo anda como para
la demo.

Requisitos previos:
- WildFly corriendo con `standalone-full.xml` (necesario por JMS/MDBs):
  ```bash
  ~/wildfly/bin/standalone.sh -c standalone-full.xml
  ```
- App deployada: `mvn clean package wildfly:deploy` desde la raíz del repo.
- App disponible en `http://localhost:8080/Rabbit`.

Estado de la base: se limpió el 29/09/2026. No hay comercios, depósitos,
stock, repartidores, pedidos, cobros, envíos, transportistas ni zonas.
Solo quedan dos cuentas de prueba activas en la tabla `usuarios`,
`prueba.comercio` y `prueba.repartidor`, que quedaron **sin asociar** (su
comercio y su repartidor se borraron): al entrar ven "Cuenta sin asociar"
(ver 2.4).

Credenciales del realm de WildFly disponibles hoy: `claude-cb-admin` (rol
`ADMINISTRADOR`). `claude-cb-erp` (rol `ERP`) también existe, pero se creó
con `add-user.sh` y no tiene comercio: desde la API v1 recibe `403`. La
cuenta ERP para las pruebas se crea desde la app (2.5). La contraseña de
`claude-cb-admin` la tenés guardada de cuando se generó; si la perdiste,
reseteala con:
```bash
~/wildfly/bin/add-user.sh -a -u claude-cb-admin -g ADMINISTRADOR
```

---

## 1. Login como administrador

1. Entrá a `http://localhost:8080/Rabbit/login.xhtml`.
2. Ingresá con `claude-cb-admin`.
3. Deberías caer en `personal/pedidos.xhtml` (la pantalla de Pedidos, vacía porque
   la base está limpia).

**Qué mirar:** el menú lateral muestra "Administrador" como rol; no
aparece ningún error.

---

## 2. Alta de datos base (como administrador)

### 2.1 Crear un comercio
1. Ir a **Comercios** → formulario "Registrar nuevo comercio".
2. Cargar nombre, razón social, CUIT (formato `XX-XXXXXXXX-X`), email y
   teléfono. Botón **Registrar**.
3. Verificar que aparece en el listado con estado "Activo".

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
1. Ir a **Usuarios** (solo visible para ADMINISTRADOR).
2. Crear un usuario con:
   - Usuario: `demo.comercio` (o el que prefieras)
   - Contraseña: la que quieras
   - Tipo de cuenta: `Comercio`
   - Comercio: el que creaste en 2.1
3. Repetir para el repartidor:
   - Usuario: `demo.repartidor`
   - Tipo de cuenta: `Repartidor`
   - Repartidor: el que creaste en 2.3

**Por qué así y no reusando `prueba.comercio`/`prueba.repartidor`:** esas
cuentas quedaron sin comercio ni repartidor asociado tras la limpieza (la
app no permite reasociar una cuenta existente). Dar
de alta desde acá crea la cuenta en el realm de WildFly *y* la asociación
en la base al mismo tiempo — sin eso, cualquier pantalla del comercio o
repartidor muestra "cuenta no asociada".

### 2.5 Cuenta del ERP (para la API REST)
Igual que las anteriores, desde **Usuarios**:
   - Usuario: `demo.erp`
   - Tipo de cuenta: `ERP (API REST)`
   - Comercio: el que creaste en 2.1

La cuenta ERP representa a ese comercio: por la API solo carga y ve sus
pedidos. No puede entrar a la web.

**Cuentas ERP viejas:** las creadas a mano con `add-user.sh -g ERP` (por
ejemplo `claude-cb-erp`) no tienen comercio: la API les responde `403`
("Cuenta ERP sin comercio"). Hay que crear una nueva desde la app.

---

## 3. Portal del comercio

1. Cerrar sesión, loguearte con `demo.comercio`.
2. Caés en `comercio/mis-pedidos.xhtml`: debería estar vacío, sin errores.
3. Ir a **Mis puntos de picking** → agregar uno (nombre, dirección).
4. Ir a **Mi stock** → confirmar que ves el stock cargado en 2.2. Es solo
   lectura: el stock lo carga el personal de Rabbit.

**Qué mirar:** el título dice "Mis puntos de picking" / "Mis pedidos", no
pide elegir comercio (a diferencia del personal, que sí lo elige de un
listado).

---

## 4. Entrada de pedidos por REST (simulando al ERP)

Con la cuenta `demo.erp` (Basic Auth):

```bash
curl -i -u demo.erp:<contraseña> \
  -X POST http://localhost:8080/Rabbit/api/v1/pedidos-externos \
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
- Debería responder `201 Created` con `idPedidoExterno`, `Location` y
  `_links`.

Consultar cómo terminó:
```bash
curl -u demo.erp:<contraseña> \
  http://localhost:8080/Rabbit/api/v1/pedidos-externos/<idPedidoExterno>
```
Estados posibles: `Pendiente` → `Sincronizado` (con `idPedido`,
`estadoPedido` y `codigoSeguimiento`), `Descartado` (con motivo) o
`Cancelado`. La conversión es asincrónica (cola JMS), así que puede tardar
un instante en pasar de Pendiente a Sincronizado.

### 4.1 Lo que tiene que mostrar la API (Clase 10)

| Prueba | Cómo | Esperado |
|---|---|---|
| Idempotencia | Repetir el mismo `curl` con la **misma** `Idempotency-Key` (fijala en una variable: `K=$(uuidgen)`) | `201` con el **mismo** `idPedidoExterno`; en Pedidos hay uno solo |
| Reintentos simultáneos | La misma clave en varios `curl` en paralelo | Todos `201` con el mismo id; en el log puede aparecer un `WFLYEJB0034` por la restricción única (esperado) |
| Clave reutilizada | Misma clave, otro `importe` | `422`, `type: /problemas/clave-idempotencia-reutilizada` |
| Sin clave | Sacar el header `Idempotency-Key` | `400`, `type: /problemas/clave-idempotencia-invalida` |
| Formato inválido | `"importe": 0` y sin `direccionEntrega` | `400` con `errores` (un mensaje por campo) |
| Regla de negocio | `"origen": "PUNTO_PICKING"` sin `idPuntoPicking` | `422`, `type: /problemas/datos-invalidos` |
| JSON roto | `-d '{"importe":'` | `400`, `type: /problemas/cuerpo-invalido` |
| Pedido de otro comercio | `GET` de un `idPedidoExterno` de otro comercio | `404` (no se revela que existe) |
| Cancelar | `curl -i -X POST -u demo.erp:<contraseña> .../v1/pedidos-externos/<id>/cancelacion` | `200`; repetirlo da `200` igual |
| Cancelar tarde | Lo mismo con el pedido ya `CONFIRMADO` | `409`, `type: /problemas/cancelacion-no-permitida` |
| Seguimiento público | `curl -i http://localhost:8080/Rabbit/api/v1/seguimiento/<codigoSeguimiento>` | `200 {"codigoSeguimiento", "estado"}`, sin login |
| URL vieja | `curl -i .../Rabbit/api/pedidos-externos/1` | `404` en `application/problem+json` (la API es `/v1`) |

El contrato está en [openapi.yaml](openapi.yaml): pegalo en
[Swagger Editor](https://editor.swagger.io) o importalo en Postman
(**Import → File**) para tener la colección armada.

**Qué mirar:** en el log de WildFly deberían verse las trazas de
`PedidoExternoListener` procesando el mensaje de la cola
`cola.pedidos.externos` (`[Pedidos][JMS] Pedido externo N sincronizado en
tiempo real`). En **Pedidos → Recibidos del ERP** el pedido aparece como
"Sincronizado".

---

## 5. Ciclo completo de un pedido (cola + tópico + SOAP + transacción)

1. Como administrador, en **Pedidos**, botón **Confirmar** del pedido
   creado en el paso 4. Confirmar es del personal de Rabbit: el comercio no
   puede. En la misma transacción:
   - si el medio de pago es `PREPAGO`, se cobra en el banco legado por SOAP
     (`BancoLegadoService`, WSDL en
     `http://localhost:8080/Rabbit/BancoLegadoService?wsdl`);
   - se asigna el repartidor disponible.
2. En **Entregas en curso** aparece la hoja de ruta del pedido (retiro →
   entrega).
3. Logueate como `demo.repartidor` → **Mis entregas**: se ve la hoja de
   ruta. **Ver recorrido en el mapa** abre Google Maps con el retiro como
   parada y la entrega como destino. Botón **Ya retiré el pedido** y
   después **Entregué el pedido**.
4. Logueate como `demo.comercio` → **Mis pedidos**: el pedido figura
   "Entregado" y en "Avisos de Rabbit" aparece un aviso por cada cambio de
   estado. Eso confirma que el tópico `topico.pedidos.estado` distribuye
   los cambios a sus suscriptores (Notificaciones y Pagos).

**Qué mirar:** en **Pedidos** (vista del personal) el pedido pasa por
Pendiente → Confirmado → En camino → Entregado, y la columna Cobro por
"Cobrado" (prepago) o "A cobrar" → "Cobrado" (contra entrega, se acredita
al entregar).

**Variantes para probar la transacción:** un pedido `PREPAGO` de más de
$500.000 lo rechaza el banco y sigue Pendiente; un pedido `PREPAGO` sin
repartidor disponible se cobra, se deshace y el banco lo reversa (se ve en
el log: `[Banco legado] Reversado ...`).

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

1. Como administrador, **Transportistas** → "Registrar transportista":
   - uno REST: endpoint
     `http://localhost:8080/Rabbit/api/simulador/transportista-rest`;
   - uno SOAP legado: endpoint
     `http://localhost:8080/Rabbit/TransportistaLegadoService?wsdl`.
2. En **Pedidos** → "Derivar a un transportista": elegí un pedido pendiente
   y un transportista. El mensaje muestra el código de seguimiento, y en la
   tabla el pedido figura Confirmado con el transportista en lugar del
   repartidor.
3. Esperá: cada 15 s Rabbit consulta al transportista. A los ~20 s el
   pedido pasa a **En camino** y a los ~40 s a **Entregado** (en
   Transportistas, el envío pasa por En tránsito y Entregado). Si era
   contra entrega, el cobro pasa a Cobrado.
4. Derivá otro y cancelalo enseguida desde Pedidos: en el log aparece
   `[Transportista ...] Envío ... cancelado`.

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
- Intentá entrar directo a `http://localhost:8080/Rabbit/personal/usuarios.xhtml`
  (pantalla solo ADMINISTRADOR): debería redirigirte, no mostrar la
  pantalla.
- Intentá pegar la URL de puntos de picking de otro comercio
  (`comercio/puntos-picking.xhtml?idComercio=<otro id>`): como usuario COMERCIO el
  parámetro se ignora y siempre ves el tuyo — confirmá que no aparecen
  datos ajenos.

Contra la API del ERP:
```bash
curl -i http://localhost:8080/Rabbit/api/v1/pedidos-externos/1                            # sin credenciales
curl -i -u claude-cb-admin:<contraseña> http://localhost:8080/Rabbit/api/v1/pedidos-externos/1   # usuario sin rol ERP
```
Debería responder `401` sin credenciales y `403` con un usuario válido que
no tiene el rol `ERP`.

---

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
[DESAFIOS-OPCIONALES.md](DESAFIOS-OPCIONALES.md):

- **Escalabilidad:** `scripts/prueba_escalabilidad.py` manda 100 pedidos
  por la API v1 y mide cuánto tarda la cola con 1, 4 y 8 consumidores.
  Necesita un usuario `ERP` creado desde la app (2.5) y un punto de
  picking activo de su comercio.
- **Heterogeneidad:** levantar el banco en Node.js (`banco-legado/`,
  `npm install && npm start`), apuntar `rabbit.banco.wsdl` y redesplegar;
  las confirmaciones prepago del paso 5 quedan registradas en la consola
  de Node.

---

## Checklist rápido

| Ítem | Resultado esperado |
|---|---|
| Login admin | Entra a personal/pedidos.xhtml sin error |
| Alta comercio/depósito/stock/repartidor | Aparecen en sus listados |
| Alta de usuarios comercio/repartidor | Cuenta creada, loguea, ve solo lo suyo |
| POST /api/v1/pedidos-externos (ERP) | 201 + Location, luego Sincronizado; reintento con la misma clave no duplica |
| Ciclo pedido completo | Pasa por todos los estados hasta ENTREGADO |
| Circuit breaker | 3 fallas → ABIERTO → corta instantáneo → SEMIABIERTO a los 30s |
| Derivar a un transportista | Confirmado con código de seguimiento; pasa solo a En camino y Entregado |
| Ruteo por zona | Pedidos agrupados por CP; despachar usa el repartidor de la zona, el respaldo o el transportista de la zona |
| Acceso restringido a personal/usuarios.xhtml | Comercio/repartidor no puede entrar |
| REST sin credenciales | 401 |
| REST con rol incorrecto | 403 |
| Cuenta sin comercio asociado | Mensaje amigable, no el interno |
