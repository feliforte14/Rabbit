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

Credenciales del realm de WildFly disponibles hoy (rol `ADMINISTRADOR`):
usuario `claude-cb-admin`. La contraseña la tenés guardada de cuando se
generó; si la perdiste, reseteala con:
```bash
~/wildfly/bin/add-user.sh -a -u claude-cb-admin -g ADMINISTRADOR
```

---

## 1. Login como administrador

1. Entrá a `http://localhost:8080/Rabbit/login.xhtml`.
2. Ingresá con `claude-cb-admin`.
3. Deberías caer en `pedidos.xhtml` (la pantalla de Pedidos, vacía porque
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
2. Cargar nombre y teléfono. Botón **Registrar**. Sin al menos un
   repartidor disponible no se puede confirmar ningún pedido.

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
cuentas viejas quedaron sin fila asociada en la base tras la limpieza. Dar
de alta desde acá crea la cuenta en el realm de WildFly *y* la asociación
en la base al mismo tiempo — sin eso, cualquier pantalla del comercio o
repartidor muestra "cuenta no asociada".

### 2.5 (Opcional) Recrear la cuenta del ERP
El usuario del ERP no se gestiona desde la app (no tiene rol en el enum
`Rol` ni pantalla propia) — es una cuenta de servidor pura, solo para la
API REST:
```bash
~/wildfly/bin/add-user.sh -a -u demo.erp -g ERP
```

---

## 3. Portal del comercio

1. Cerrar sesión, loguearte con `demo.comercio`.
2. Caés en `mis-pedidos.xhtml`: debería estar vacío, sin errores.
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
  -X POST http://localhost:8080/Rabbit/api/pedidos-externos \
  -H "Content-Type: application/json" \
  -d '{
    "idComercio": 1,
    "origen": "STOCK_CONSIGNADO",
    "lineas": [{"idItem": 1, "cantidad": 1}],
    "importe": 2500,
    "medioPago": "PREPAGO",
    "direccionEntrega": "Av. Corrientes 1234, CABA"
  }'
```

- Ajustá `idComercio` e `idItem` a los IDs reales que quedaron después del
  alta (columna ID del listado de comercios y de la tabla de stock).
- `direccionEntrega` es obligatoria: sin ella la API responde `400`.
- Debería responder `201 Created` con `idPedidoExterno` y `Location`.

Consultar cómo terminó:
```bash
curl -u demo.erp:<contraseña> \
  http://localhost:8080/Rabbit/api/pedidos-externos/<idPedidoExterno>
```
Estados posibles: `Pendiente` → `Sincronizado` (con el ID del pedido real)
o `Descartado` (con motivo). La conversión es asincrónica (cola JMS), así
que puede tardar un instante en pasar de Pendiente a Sincronizado.

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
   ruta. Botón **Ya retiré el pedido** y después **Entregué el pedido**.
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

---

## 7. Seguridad: accesos restringidos

Con `demo.comercio` logueado:
- Intentá entrar directo a `http://localhost:8080/Rabbit/usuarios.xhtml`
  (pantalla solo ADMINISTRADOR): debería redirigirte, no mostrar la
  pantalla.
- Intentá pegar la URL de puntos de picking de otro comercio
  (`puntos-picking.xhtml?idComercio=<otro id>`): como usuario COMERCIO el
  parámetro se ignora y siempre ves el tuyo — confirmá que no aparecen
  datos ajenos.

Contra la API del ERP:
```bash
curl -i http://localhost:8080/Rabbit/api/pedidos-externos/1                            # sin credenciales
curl -i -u claude-cb-admin:<contraseña> http://localhost:8080/Rabbit/api/pedidos-externos/1   # usuario sin rol ERP
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
  por la API y mide cuánto tarda la cola con 1, 4 y 8 consumidores.
  Necesita un comercio y un punto de picking activos y un usuario `ERP`.
- **Heterogeneidad:** levantar el banco en Node.js (`banco-legado/`,
  `npm install && npm start`), apuntar `rabbit.banco.wsdl` y redesplegar;
  las confirmaciones prepago del paso 5 quedan registradas en la consola
  de Node.

---

## Checklist rápido

| Ítem | Resultado esperado |
|---|---|
| Login admin | Entra a pedidos.xhtml sin error |
| Alta comercio/depósito/stock/repartidor | Aparecen en sus listados |
| Alta de usuarios comercio/repartidor | Cuenta creada, loguea, ve solo lo suyo |
| POST /api/pedidos-externos (ERP) | 201 + Location, luego Sincronizado |
| Ciclo pedido completo | Pasa por todos los estados hasta ENTREGADO |
| Circuit breaker | 3 fallas → ABIERTO → corta instantáneo → SEMIABIERTO a los 30s |
| Derivar a un transportista | Confirmado con código de seguimiento; pasa solo a En camino y Entregado |
| Acceso restringido a usuarios.xhtml | Comercio/repartidor no puede entrar |
| REST sin credenciales | 401 |
| REST con rol incorrecto | 403 |
| Cuenta sin comercio asociado | Mensaje amigable, no el interno |
