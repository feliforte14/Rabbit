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
3. Deberías caer en `pedidos.xhtml` (tablero de entregas en curso, vacío
   porque la base está limpia).

**Qué mirar:** el menú lateral muestra "Administrador" como rol; no
aparece ningún error.

---

## 2. Alta de datos base (como administrador)

### 2.1 Crear un comercio
1. Ir a **Comercios** → "Crear comercio".
2. Cargar nombre, razón social, CUIT, email y teléfono. Confirmar alta.
3. Verificar que aparece en el listado con estado "Activo".

### 2.2 Crear un depósito y algo de stock
1. Ir a **Depósitos** → crear uno (nombre, dirección).
2. Ir a **Stock** (items.xhtml) → cargar al menos un ítem con cantidad
   disponible, asociado al depósito creado.

### 2.3 Crear un repartidor
1. Ir a **Repartidores** → "Crear repartidor".
2. Cargar nombre y datos de contacto. Confirmar alta.

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
3. Ir a **Puntos de picking** → agregar uno (nombre, dirección).
4. Ir a **Mi stock** → confirmar que ves el stock cargado en 2.2 (o cargá
   más si el alta de stock es solo desde el admin, según cómo esté armada
   esa pantalla).

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
    "medioPago": "PREPAGO"
  }'
```

- Ajustá `idComercio` e `idItem` a los IDs reales que quedaron después del
  alta (revisá el listado de comercios/stock, o mirá la URL al entrar al
  detalle de cada uno).
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
`jms.queue.PedidosExternos`.

---

## 5. Ciclo completo de un pedido (cola + tópico + SOAP + transacción)

1. Con `demo.comercio` logueado, confirmá el pedido creado en el paso 4
   (o generá uno nuevo desde la pantalla de pedidos si existe alta manual).
2. Si el medio de pago es `PREPAGO`, el pedido dispara el cobro contra el
   banco legado por SOAP (`BancoLegadoService`, WSDL publicado en
   `http://localhost:8080/Rabbit/BancoLegadoService?wsdl`).
3. Logueate como `demo.repartidor` → `mis-entregas.xhtml`: el pedido
   confirmado debería aparecer para asignar/retirar.
4. Marcá retiro y luego entrega.
5. Volvé a loguearte como `demo.comercio` (u observá el tópico de
   notificaciones) y confirmá que el estado del pedido se actualizó — eso
   confirma que el tópico JMS `jms.topic/EstadosPedido` está distribuyendo
   los cambios de estado a los suscriptores.

**Qué mirar:** en `pedidos.xhtml` (vista de personal) el pedido pasa por
sus estados (`CONFIRMADO` → `EN_CAMINO`/similar → `ENTREGADO`).

---

## 6. Circuit breaker frente al banco caído

Con WildFly corriendo:

```bash
# Simular la caída del banco (tarda 10 s en vez de responder, más que el timeout)
~/wildfly/bin/jboss-cli.sh --connect \
  --command="/system-property=rabbit.banco.simular.caida:add(value=true)"
```

1. Confirmá 4 pedidos `PREPAGO` seguidos (repetí el paso 4 o 5 cuatro
   veces con pedidos nuevos).
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

## 7. Seguridad: accesos restringidos

Con `demo.comercio` logueado:
- Intentá entrar directo a `http://localhost:8080/Rabbit/usuarios.xhtml`
  (pantalla solo ADMINISTRADOR): debería redirigirte, no mostrar la
  pantalla.
- Intentá pegar la URL de puntos de picking de otro comercio
  (`puntos-picking.xhtml?idComercio=<otro id>`): como usuario COMERCIO el
  parámetro se ignora y siempre ves el tuyo — confirmá que no aparecen
  datos ajenos.

Con `demo.erp` (o sin credenciales):
```bash
curl -i http://localhost:8080/Rabbit/api/pedidos-externos
```
Debería responder `401` sin Basic Auth, y `403` si las credenciales son
válidas pero el rol no es `ERP`.

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

## Checklist rápido

| Ítem | Resultado esperado |
|---|---|
| Login admin | Entra a pedidos.xhtml sin error |
| Alta comercio/depósito/stock/repartidor | Aparecen en sus listados |
| Alta de usuarios comercio/repartidor | Cuenta creada, loguea, ve solo lo suyo |
| POST /api/pedidos-externos (ERP) | 201 + Location, luego Sincronizado |
| Ciclo pedido completo | Pasa por todos los estados hasta ENTREGADO |
| Circuit breaker | 3 fallas → ABIERTO → corta instantáneo → SEMIABIERTO a los 30s |
| Acceso restringido a usuarios.xhtml | Comercio/repartidor no puede entrar |
| REST sin credenciales | 401 |
| REST con rol incorrecto | 403 |
| Cuenta sin comercio asociado | Mensaje amigable, no el interno |
