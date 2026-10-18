# Rabbit

Plataforma de logística de última milla que conecta comercios con una red
de depósitos y repartidores. Trabajo práctico integrador de Desarrollo de
Aplicaciones II (UADE, 2.º cuatrimestre 2026)

## Qué hace

- **Comercios:** alta y gestión de comercios y sus puntos de picking, con
  baja lógica en cascada y reactivación. Un comercio dado de baja se puede
  eliminar si no tiene nada vivo (solo pedidos cancelados, que se borran
  con él). Rabbit no lleva el catálogo de productos de cada comercio: eso
  vive en el ERP del comercio, y Rabbit solo conoce lo que tiene que
  retirar y entregar.
- **Inventario:** depósitos propios de Rabbit con stock consignado por los
  comercios (con el peso de cada unidad), y reservas de stock con
  vencimiento (5 minutos, extensibles): si nadie las confirma ni las
  libera, se liberan solas.
- **Pedidos:** recepción de pedidos desde el ERP de cada comercio por una
  API REST versionada (`/api/v1`, alta idempotente, consulta y
  cancelación), su sincronización automática con stock y su seguimiento
  (`PENDIENTE → CONFIRMADO → EN_CAMINO → ENTREGADO`), también público por
  código de seguimiento. El ERP puede mandar quién recibe (nombre,
  teléfono y email, ADR-039), y Rabbit le promete el día de entrega según
  la hora de corte, ya en la respuesta del alta (ADR-042). En camino, el
  seguimiento dice en qué parada del repartidor va y en qué franja llega
  (ADR-043).
- **Pagos y cobranzas:** un pedido prepago se cobra en el banco legado al
  confirmarlo (con reversa si la confirmación falla después, y un circuit
  breaker si el banco no responde); uno contra entrega se acredita cuando
  se entrega. Además, cada mes se le liquida y cobra a cada comercio el
  servicio de Rabbit: abono + cargo por envío + cargo por kilo (ADR-034).
- **Repartidores:** padrón de repartidores propios con su zona y la
  capacidad de su vehículo (kilos y bultos): al confirmar un pedido se le
  asigna al primero al que le entra, que puede llevar varios a la vez
  (ADR-032). Si no le entra a nadie (o ya pasó la hora de corte de la hoja
  de ruta), el pedido queda en una lista de espera, sin cobrar, y se
  asigna solo cuando se libera alguno; el repartidor puede devolver al
  depósito un pedido que no pudo entregar, con un motivo fijo, y se le
  asigna a otro (ADR-037). Después de 2 visitas fallidas (o si el cliente
  lo rechaza) el pedido vuelve al comercio: el stock consignado se
  devuelve y el cobro se anula (ADR-041). Mientras reparte, su celular comparte la ubicación: el
  personal lo ve en un mapa y el cliente, en el seguimiento (ADR-035). Un
  administrador puede eliminar un repartidor sin entregas en curso ni
  cuenta de usuario (ADR-036).
- **Transportistas:** un pedido se puede derivar a una empresa de envíos
  externa (integrada por API REST o por SOAP legado), después de comparar
  sus cotizaciones; Rabbit sigue el estado del envío (consultándolo cada 15 s)
  y mueve el pedido solo.
- **Ruteo y entregas:** zonas de reparto por código postal; los pedidos
  pendientes se agrupan por zona y se despachan solos según quién cubre la
  zona (repartidores propios o un transportista; si el transportista no lo
  toma, el más barato de los demás). Las zonas se dan de baja, se
  reactivan o, solo un administrador, se eliminan (ADR-036). Hoja de ruta de cada
  pedido (de dónde se retira y adónde se entrega), hoja de ruta
  recomendada del repartidor con todas sus paradas en orden y el recorrido
  en Google Maps (ADR-038), y tablero de entregas en curso.
- **Seguridad y vistas por tipo de usuario:** el personal de Rabbit
  (`ADMINISTRADOR`, `OPERADOR`) opera toda la red; un `COMERCIO` sigue sus
  pedidos, su stock, sus puntos de picking y lo que se le cobra por mes
  (con la vista previa del mes en curso); un `REPARTIDOR` ve su hoja de
  ruta recomendada y marca retiro y entrega (con la foto del remito
  firmado, ADR-033) o devuelve el pedido al depósito, desde el celular; un `ERP` (el sistema del
  comercio) solo usa la API REST, y solo con los pedidos de su comercio.
  Todo por HTTPS, con límite de intentos en el login.
- **Notificaciones:** cada cambio de estado le llega al comercio en su
  portal y, si hay un servidor de correo configurado, por mail. A quien
  recibe también, al email que manda el ERP con cada pedido (obligatorio
  desde el 18/10), con el link al seguimiento (ADR-039, ADR-040).
- **Indicadores:** tablero para el personal (toda la red, un comercio y
  por repartidor) y para cada comercio: entregas a tiempo, al primer
  intento, horas de recibido a entregado, lista de espera y costo por
  envío, de los últimos 7, 30 o 90 días (ADR-044).
- **Interfaz:** tres temas visuales (normal, claro y oscuro) que se
  cambian en el momento desde el menú o el pie de las páginas públicas, y
  diseño responsive sin scroll horizontal: a menor pantalla se ocultan las
  columnas secundarias de cada tabla, en celular las tablas se ven como
  tarjetas con lo esencial y el menú se despliega con un botón (ver
  `resources/rabbit/1_17/`). En la compu, la barra lateral entra entera
  sin scroll propio también en pantallas bajas (notebooks de 13").

## Tecnologías

Jakarta EE 10 sobre WildFly (perfil `standalone-full`), Java 17, JSF +
Facelets, EJB, JPA/Hibernate, PostgreSQL (Supabase), JMS (ActiveMQ
Artemis embebido), JAX-WS (SOAP), JAX-RS (REST), Jakarta Security,
Jakarta Mail, Maven (WAR), JUnit 5 y REST Assured.

Esta rama (`main`) es el sistema de Rabbit solo: el banco y los
transportistas con los que se integra están simulados dentro del mismo
WAR. La rama [`desafio-heterogeneidad`](https://github.com/feliforte14/Rabbit/tree/desafio-heterogeneidad) suma el desafío de heterogeneidad tecnológica: el
mismo banco legado en Node.js y el transportista moderno en Python, como
servicios aparte con el mismo contrato.

## Estructura

Cada componente vive en `com.rabbit.<componente>` y se divide en capas:

```
com.rabbit.<componente>/
├── presentacion/   ← Managed Beans JSF
├── negocio/        ← EJB e interfaces @Local
├── datos/          ← Repositorios (DAO) y entidades JPA (model/)
└── dto/            ← Objetos que cruzan capas y componentes
```

Las pantallas (JSF/Facelets) están en `src/main/webapp`, ordenadas por
tipo de usuario, igual que el menú:

```
src/main/webapp/
├── login.xhtml, error.html   ← públicas
├── seguimiento.xhtml         ← pública: el cliente final sigue su pedido con el código
├── personal/                 ← personal de Rabbit (ADMINISTRADOR, OPERADOR)
├── comercio/                 ← portal del COMERCIO (puntos-picking también lo usa el personal)
├── repartidor/               ← hoja de ruta del REPARTIDOR
├── resources/rabbit/1_17/     ← CSS y JS versionados: estilos.css (con los 3 temas), tema.js (cambio
│                               de tema), menu.js (menú del celular), tablas.js (tablas como tarjetas),
│                               remito.js (achica la foto del remito), ubicacion.js y mapa.js (mapas)
└── WEB-INF/
    ├── plantillas/template.xhtml  ← layout y menú (no se puede pedir por URL)
    ├── web.xml, beans.xml, jboss-ejb3.xml
```

Fuera de la aplicación:

```
src/test/java/             ← tests unitarios (*Test) y de integración de la API (*IT)
docs/                      ← documentación técnica por tema (ver docs/README.md)
scripts/                   ← prueba de escalabilidad y limpieza de datos de prueba
```

| Componente | Paquete | Estado |
|---|---|---|
| Comercios | `comercios` | Implementado |
| Inventario | `inventario` | Implementado |
| Pedidos | `pedidos` | Implementado |
| Seguridad | `seguridad` | Implementado |
| Pagos y Cobranzas | `pagos` | Implementado |
| Repartidores | `repartidores` | Implementado |
| Integración con el banco legado (SOAP) | `integracion.banco` | Implementado |
| Notificaciones | `notificaciones` | Implementado |
| Ruteo (zonas, despacho por zona, hoja de ruta y tablero de entregas) | `ruteo` | Implementado |
| Transportistas (REST y SOAP legado) | `transportistas` | Implementado |

## Integraciones

2 SOAP, 3 REST, 1 cola punto a punto y 1 tópico pub/sub (más el mail, opcional):

| Integración | Tipo | Dirección |
|---|---|---|
| Pagos → banco legado (cobro y reversa, con circuit breaker) | Sincrónica, **SOAP** | Saliente |
| Transportistas → transportista legado (derivar, seguir, cancelar) | Sincrónica, **SOAP** | Saliente |
| ERP del comercio → Rabbit (`/api/v1/pedidos-externos`: alta idempotente, consulta y cancelación) | Sincrónica, **REST** | Entrante |
| Cliente final → Rabbit (`/api/v1/seguimiento/{codigo}`, sin login) | Sincrónica, **REST** | Entrante |
| Transportistas → transportista moderno (cotizar, derivar, seguir, cancelar) | Sincrónica, **REST** | Saliente |
| Pedido del ERP → pedido real (`cola.pedidos.externos`) | Asincrónica, **cola JMS** | Interna |
| Cambios de estado del pedido → Pagos y Notificaciones (`topico.pedidos.estado`) | Asincrónica, **tópico JMS** | Interna |
| Notificaciones → comercio | SMTP (Jakarta Mail), después del commit | Saliente (opcional, por configuración) |

El seguimiento de los envíos derivados es por consulta periódica (cada
15 s) a todos los transportistas, modernos y legados (ADR-029).

## Documentación técnica

El detalle y la justificación de cada decisión están en
[`docs/`](docs/README.md) (índice de todos los documentos):

- **Arquitectura** (`docs/arquitectura/`): [Arquitectura](docs/arquitectura/ARQUITECTURA.md) (capas, componentes, interfaces y estados del pedido), [Patrones de diseño](docs/arquitectura/PATRONES.md), [Transacciones](docs/arquitectura/TRANSACCIONES.md) y [Decisiones (48 ADRs)](docs/arquitectura/DECISIONES.md).
- **Integraciones** (`docs/integraciones/`): [Mensajería: justificación final](docs/integraciones/MensajeriaFinal.md) (por qué cola, tópico, SOAP o REST), [Mensajería sincrónica](docs/integraciones/MENSAJERIA-SINCRONICA.md) (banco, transportistas y API REST), [Mensajería asincrónica](docs/integraciones/MENSAJERIA-ASINCRONICA.md) (cola, tópico y mail) y el [contrato OpenAPI](docs/integraciones/openapi.yaml).
- **Seguridad** (`docs/seguridad/`): [Seguridad](docs/seguridad/SEGURIDAD.md).
- **Pruebas** (`docs/pruebas/`): [Flujo de pruebas](docs/pruebas/FLUJO-DE-PRUEBAS.md), recorrido manual de punta a punta para verificar y para la demo, y [Pruebas y comandos](docs/pruebas/PRUEBAS-Y-COMANDOS.md), comandos de cada sistema y lista de pruebas para marcar.
- **Consigna** (`docs/consigna/`): [Checklist](docs/consigna/CHECKLIST.md) de requisitos (Sección 6 y anexo), [Desafíos opcionales](docs/consigna/DESAFIOS-OPCIONALES.md) y [Uso de IA](docs/consigna/USO-DE-IA.md).

## Cumplimiento de la consigna

| Requisito | Cómo |
|---|---|
| Mínimo 6 componentes con interfaz explícita | 10 componentes, cada uno con interfaces `@Local` |
| 1 componente stateful y 1 stateless | `InventarioService` (`@Stateful`: la reserva de stock es una conversación) y el resto `@Stateless` |
| Arquitectura en capas | `presentacion/`, `negocio/`, `datos/`, `dto/` en cada componente |
| 3 patrones de diseño | DAO, DTO, Facade, Adapter, Singleton, Provider, Observer, máquina de estados, transacción compensatoria y Circuit Breaker |
| SOAP con un sistema legado / REST con un partner moderno | Ver [Integraciones](#integraciones) |
| Cola punto a punto y tópico pub/sub | `cola.pedidos.externos` y `topico.pedidos.estado` (ActiveMQ Artemis) |
| Seguridad declarativa | `@RolesAllowed` en las operaciones sensibles, 5 roles, HTTPS obligatorio |
| Transacciones declarativas | `confirmarPedido`: cobrar → asignar repartidor → confirmar, con reversa en el banco si falla |
| Desafíos opcionales | Resiliencia, escalabilidad medida y ADRs. La heterogeneidad tecnológica (banco en Node.js, transportista en Python) está en la rama [`desafio-heterogeneidad`](https://github.com/feliforte14/Rabbit/tree/desafio-heterogeneidad) |
| Anexo: responsive y temas | Responsive de 360 a 1920 px; temas normal, claro y oscuro en tiempo real |

El detalle, requisito por requisito, en el [Checklist](docs/consigna/CHECKLIST.md).

## Credenciales y cuentas necesarias

Ninguna credencial real va en este repositorio (es público). Esta sección
es el checklist de qué necesita cada quien y dónde se consigue o se crea
— no reemplaza al detalle de cada paso en
[Cómo levantar el sistema](#cómo-levantar-el-sistema).

### Infraestructura

| Credencial | Para qué | De dónde sale |
|---|---|---|
| Usuario/contraseña de la base PostgreSQL | Datasource `RabbitDS` (paso 2) | Si es la base compartida de Supabase, pedírsela al equipo; si es una base propia, la que elijas al crearla |
| Usuario/contraseña de management de WildFly | `wildfly-maven-plugin` para desplegar (paso 3), puerto `9990` | Se crea una sola vez con `add-user.sh -u ... -p ...` (sin `-a`, no es un usuario de la app) y se guarda en `~/.m2/settings.xml`, nunca en `pom.xml` |
| Servidor SMTP (host, puerto, remitente) | Avisos por mail al comercio (paso 7, opcional) | El que dé el equipo, o un `smtpd` local de prueba (ver paso 7) — sin esto configurado, la app funciona igual y los avisos solo quedan en el portal |

### Cuentas de la aplicación

Estas **no** se precargan: las crea cada administrador desde la pantalla
**Usuarios**, salvo la primera, que arranca el sistema. Se guardan en la
base compartida (con la contraseña en bcrypt), así que una cuenta creada
desde cualquier máquina entra en el WildFly de todos (paso 5, ADR-031).

| Tipo de cuenta | Cómo se crea | Qué usa/ve |
|---|---|---|
| `ADMINISTRADOR` (la primera) | `add-user.sh -a -u <usuario> -p '<contraseña>' -g ADMINISTRADOR -s` (paso 5) — es la única que se crea por fuera de la app | Todo; además, único rol que puede eliminar comercios, repartidores, zonas y usuarios, anular un cobro, dar de alta/baja otros usuarios y liquidar el mes |
| `ADMINISTRADOR` / `OPERADOR` (resto) | Desde **Usuarios**, con un `ADMINISTRADOR` ya logueado | Comercios, Depósitos y stock, Pedidos, Repartidores, Transportistas, Ruteo — el `OPERADOR` no ve **Usuarios** ni puede eliminar nada ni anular cobros |
| `COMERCIO` | Desde **Usuarios**, asociándola a un comercio ya dado de alta | Sus pedidos (con el remito de cada entrega), su stock (solo lectura), sus puntos de picking y sus liquidaciones mensuales |
| `REPARTIDOR` | Desde **Usuarios**, asociándola a un repartidor ya dado de alta | Su hoja de ruta; marca retiro y entrega (con foto del remito) y comparte su ubicación mientras reparte |
| `ERP` | Desde **Usuarios**, asociándola a un comercio (paso 6) | Nada de la web — solo la API REST `/api/v1`, y solo los pedidos de ese comercio (HTTP Basic) |

Un usuario creado a mano con `add-user.sh -g ERP`/`-g COMERCIO` (sin pasar
por la app) no queda asociado a ningún comercio ni repartidor: la app
responde "cuenta sin asociar" (web) o `403` (API). El flujo completo para
probar los cinco tipos de cuenta está en
[FLUJO-DE-PRUEBAS.md](docs/pruebas/FLUJO-DE-PRUEBAS.md).

### Sistemas legados y modernos

Rabbit no necesita levantar nada aparte: el banco legado (SOAP), el
transportista legado (SOAP) y el transportista moderno (REST) están
simulados dentro del mismo WAR y se llaman por la red igual que si fueran
externos:

| Sistema | Protocolo | Dónde |
|---|---|---|
| Banco legado | SOAP | `http://localhost:8080/Rabbit/BancoLegadoService?wsdl` |
| Transportista legado | SOAP | `http://localhost:8080/Rabbit/TransportistaLegadoService?wsdl` |
| Transportista moderno | REST | `http://localhost:8080/Rabbit/api/simulador/transportista-rest` |

Ninguno tiene autenticación. Las versiones en otros lenguajes (banco en
Node.js, transportista en Python) están en la rama
[`desafio-heterogeneidad`](https://github.com/feliforte14/Rabbit/tree/desafio-heterogeneidad).

## Cómo levantar el sistema

### Requisitos

- Java 17 o superior (el proyecto compila con `--release 17`; probado con
  JDK 21 y 24).
- Maven 3.9+.
- WildFly 41 (probado con 41.0.0.Final y 41.0.1.Final), arrancado con el perfil
  **`standalone-full.xml`** (lo necesita JMS).
- Acceso a la base PostgreSQL del proyecto (datasource JNDI
  `java:jboss/datasources/RabbitDS`, ver
  [`persistence.xml`](src/main/resources/META-INF/persistence.xml)).

> Las credenciales de la base y de la consola de WildFly **no van en este
> repositorio**: pedíselas al equipo.

### 1. Arrancar WildFly

```bash
$WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml
```

### 2. Registrar el driver de PostgreSQL y el datasource (una sola vez)

Con el `.jar` del driver JDBC de PostgreSQL descargado y WildFly corriendo:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --commands="module add --name=org.postgresql --resources=/ruta/a/postgresql.jar --dependencies=jakarta.transaction.api,/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-class-name=org.postgresql.Driver,driver-xa-datasource-class-name=org.postgresql.xa.PGXADataSource)"

$WILDFLY_HOME/bin/jboss-cli.sh --connect --commands="data-source add --name=RabbitDS --jndi-name=java:jboss/datasources/RabbitDS --driver-name=postgresql --connection-url=jdbc:postgresql://HOST:5432/BASE --user-name=USUARIO --password=CONTRASEÑA --use-ccm=false,/subsystem=datasources/data-source=RabbitDS:test-connection-in-pool"
```

El último comando tiene que responder `"outcome" => "success"`.

**Configurar el pool de conexiones (una sola vez, importante).**

- **Validar las conexiones:** Supabase cierra las conexiones inactivas.
  Sin validación, WildFly las sigue entregando y la app falla sola después
  de un rato sin uso (los timers fallan cada minuto y los despliegues no
  levantan) hasta que se vacía el pool a mano. Con esto, WildFly revisa las
  conexiones cada 30 s, descarta las rotas y cierra las que llevan 5
  minutos sin usarse.
- **Limitar el pool a 10:** el pooler de Supabase admite como máximo 15
  conexiones para todo el proyecto (compartidas por todo el equipo) y el
  pool de WildFly permite 20 por defecto; al pasarse, la base rechaza
  conexiones (`EMAXCONNSESSION`).

```bash
DS=/subsystem=datasources/data-source=RabbitDS
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS:write-attribute(name=max-pool-size,value=10)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS:write-attribute(name=valid-connection-checker-class-name,value=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS:write-attribute(name=exception-sorter-class-name,value=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLExceptionSorter)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS:write-attribute(name=background-validation,value=true)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS:write-attribute(name=background-validation-millis,value=30000)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS:write-attribute(name=idle-timeout-minutes,value=5)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS/connection-properties=socketTimeout:add(value=30)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS/connection-properties=connectTimeout:add(value=10)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS/connection-properties=tcpKeepAlive:add(value=true)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command=":reload"
```

Las tres últimas son del driver de PostgreSQL. `socketTimeout` corta una
consulta que no responde a los 30 s: sin él, una conexión que Supabase
dejó colgada (sin cerrarla) bloqueó un hilo 16 minutos, hasta que el
sistema operativo la cortó; en ese lapso los timers no avanzaron.

Si igual aparece `This connection has been closed` o
`Unable to determine Dialect without JDBC metadata` al desplegar, vaciar
el pool:
`$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="$DS:flush-all-connection-in-pool"`.

Si la base está en Supabase y responde `EAUTHQUERY ... connection to database not
available`, el proyecto de Supabase está pausado: reactivarlo desde su
dashboard.

**Con Supabase, usar el host y puerto del Session Pooler (`:5432`), no el
Transaction Pooler (`:6543`).** El host directo (`db.<ref>.supabase.co`)
solo tiene registro DNS **IPv6**, así que si tu red no tiene salida IPv6
(común en redes hogareñas/ISP) la conexión ni arranca — hay que usar el
pooler (`aws-0-<región>.pooler.supabase.com`), que sí resuelve por IPv4.
Pero el Transaction Pooler (`:6543`) hace fallar el deploy con
`Unable to determine Dialect without JDBC metadata`: no soporta las
consultas de metadata que Hibernate necesita al levantar el
`EntityManagerFactory`. El Session Pooler, mismo host pero puerto `5432`,
se comporta como una conexión normal y funciona sin este problema.

`hibernate.hbm2ddl.auto=update` crea/actualiza las tablas solo al
desplegar: no hace falta correr ningún script de esquema. Los `CHECK` de
las columnas enum (que `update` no actualiza cuando un enum suma valores)
los realinea solo `AlineadorDeRestriccionesEnum` en cada despliegue
(ADR-008): tampoco hay que tocarlos a mano.

### 3. Usuario de management (una sola vez)

El `wildfly-maven-plugin` despliega por la API de management (puerto
`9990`). Ese usuario tiene que existir en WildFly:

```bash
$WILDFLY_HOME/bin/add-user.sh -u <usuario> -p '<contraseña>' -s
```

Sus credenciales **no van en el `pom.xml`** (el repositorio es público):
el plugin las toma del `<server>` con id `rabbit-wildfly` de
`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>rabbit-wildfly</id>
      <username>USUARIO</username>
      <password>CONTRASEÑA</password>
    </server>
  </servers>
</settings>
```

### 4. Desplegar

```bash
mvn clean package wildfly:deploy
```

(`clean` siempre: una compilación incremental ya dejó pasar un import
faltante). El despliegue queda guardado en WildFly: al reiniciarlo, Rabbit
vuelve a levantar solo; solo hace falta redesplegar cuando cambia el código.

Abrir https://localhost:8443/Rabbit. Rabbit exige HTTPS: si se entra por
`http://localhost:8080/Rabbit`, redirige solo. En local WildFly usa un
certificado autofirmado, así que el navegador muestra una advertencia la
primera vez (aceptarla) y `curl` necesita `-k`.

### 5. Cuentas compartidas y primer acceso (una sola vez)

Las cuentas viven en la tabla `usuarios` de la base compartida y WildFly
las valida leyendo esa tabla (ADR-031). Para eso, **después del primer
despliegue** (que agrega la columna de contraseñas), cada integrante
configura su WildFly una vez:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --file=scripts/configurar-realm-compartido.cli
```

Tiene que terminar con `"outcome" => "success"` y recargar WildFly. Desde
ahí, cualquier cuenta creada desde la app de cualquier integrante entra en
todas las máquinas, y una baja corta el acceso en todas. Para volver
atrás: `scripts/revertir-realm-compartido.cli`.

Si la base ya tiene administradores, entrá con uno de ellos. Si es una
base vacía, no hay alta pública de cuentas: el primer administrador se
crea en tu WildFly y entra solo en tu máquina:

```bash
$WILDFLY_HOME/bin/add-user.sh -a -u <usuario> -p '<contraseña>' -g ADMINISTRADOR -s
```

Desde ahí, ese administrador crea los demás usuarios en "Usuarios"
(`personal/usuarios.xhtml`), ya en la base. Ahí también los da de baja
(se pueden reactivar con una contraseña nueva) o los elimina para siempre.
Una cuenta de comercio o ERP solo se reactiva si su comercio está activo.

**Cuentas creadas antes de ADR-031:** no tienen contraseña en la base y
solo entran en la máquina donde se crearon; la pantalla Usuarios las
marca "Solo en una máquina". Un administrador les pone una contraseña
nueva en "Restablecer contraseña" y desde ahí entran en todas.

Hay cinco tipos de cuenta:
`ADMINISTRADOR` y `OPERADOR` (personal de Rabbit), `COMERCIO` (se asocia a
un comercio), `REPARTIDOR` (se asocia a un repartidor) y `ERP` (se asocia
a un comercio; solo usa la API REST, ver paso 6). Cada uno de los cuatro
primeros entra a su propia pantalla.

### 6. Usuario del ERP para la API REST

La API `/api/v1/pedidos-externos` exige un usuario de tipo `ERP` (HTTP
Basic). Representa al sistema de un comercio, no a una persona, y está
atado a ese comercio: solo carga y ve sus pedidos. Lo crea un
administrador desde `personal/usuarios.xhtml`, eligiendo el tipo "ERP
(API REST)" y el comercio. No puede entrar a la web.

Un usuario ERP creado a mano con `add-user.sh` (como se hacía antes) no
tiene comercio y la API le responde `403`: hay que borrarlo de los
archivos del realm de esa máquina y crearlo desde la app.

Contrato en [docs/integraciones/openapi.yaml](docs/integraciones/openapi.yaml) y ejemplos en
[MENSAJERIA-SINCRONICA.md](docs/integraciones/MENSAJERIA-SINCRONICA.md).

### 7. (Opcional) Tarifa de la liquidación mensual

Por defecto: $15.000 de abono, $1.200 por pedido entregado y $80 por kilo
(ADR-034). Para cambiarla, sin redesplegar:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.tarifa.abono:add(value=20000)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.tarifa.por-envio:add(value=1500)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.tarifa.por-kilo:add(value=100)"
```

### 7b. Hora de corte de la hoja de ruta (leer antes de una demo)

Por defecto, **07:00** (la definió el equipo: a las 7 se cierra la hoja
del día, así hay tiempo de preparar los pedidos y salir a las 9). Desde
esa hora no se suman pedidos a la hoja del día de ningún repartidor: lo
que se confirma después queda **en la lista de espera** y se asigna solo
al día siguiente, antes de las 7 (ADR-037). La fecha prometida al
comercio (ADR-042) sigue la misma regla.

> **Ojo:** durante el día ya pasó el corte, así que **todo lo que se
> confirme va a quedar "En espera"**. Para una demo o para probar a
> cualquier hora, sacá el corte con `ninguno` antes de empezar y
> volvé a ponerlo al terminar. Se lee en cada uso: no hace falta
> redesplegar ni reiniciar.

```bash
# sin corte (demos y pruebas durante el día):
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.ruteo.hora-corte:add(value=ninguno)"
# volver al corte de las 07:00:
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.ruteo.hora-corte:remove"
# otra hora (si ya existe la propiedad, primero el remove):
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.ruteo.hora-corte:add(value=08:30)"
```

Para ver si está configurada:
`$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.ruteo.hora-corte:read-resource"`
(`failed` = no está definida, rige 07:00).

### 8. (Opcional) Avisos por mail

Los avisos a los comercios siempre quedan en su portal. Para que además
salgan por mail (al email que cargó el comercio y al de quien recibe),
configurar el servidor de correo con system properties. Se leen en cada
envío: no hace falta redesplegar (ADR-047).

**Con Gmail** (llega a casillas reales). Hace falta una cuenta de Gmail
para Rabbit con verificación en dos pasos y una **contraseña de
aplicación** (Cuenta de Google → Seguridad → Contraseñas de aplicaciones):
no la contraseña normal. Esa contraseña **no va al repositorio**; cada
integrante la carga en su WildFly:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.mail.smtp.host:add(value=smtp.gmail.com)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.mail.smtp.usuario:add(value=CUENTA@gmail.com)"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.mail.smtp.password:add(value=CONTRASEÑA_DE_APLICACION)"
```

Con usuario y contraseña usa STARTTLS por el puerto 587, y el remitente
es la misma cuenta (Gmail no deja mandar como otra). Otro proveedor:
`rabbit.mail.smtp.port`, `rabbit.mail.smtp.seguridad` (`starttls`, `ssl`
para el 465, o `ninguna`) y `rabbit.mail.remitente`. Gmail permite unos
500 mails por día desde una cuenta común. Si un mail no sale, el log dice
`[Notificaciones][Mail] No se pudo mandar el aviso…` con el motivo, y el
aviso igual queda en Rabbit.

Para probar en local sin un servidor real:
`python3 -m smtpd -n -c DebuggingServer localhost:2525` (Python hasta 3.11)
y `rabbit.mail.smtp.host=localhost`, `rabbit.mail.smtp.port=2525`: cada
mail se imprime en esa consola. Sin servidor configurado no se intenta
mandar nada.

**Mail para pedir una cuenta (opcional).** No hay alta abierta: el login
muestra un mail de contacto (con asunto y datos a completar ya escritos) y
un `ADMINISTRADOR` crea la cuenta desde **Usuarios** después de hablar con
el comercio o repartidor. Por defecto es `altas_rabbit@gmail.com`; para
cambiarlo:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="/system-property=rabbit.contacto.mail:add(value=altas@tu-dominio.com)"
```

## Tests

```bash
mvn test                       # 148 tests unitarios (JUnit 5), sin servidor
```

Corren en cada `mvn package`: reglas de transición de estados, códigos
postales y de seguimiento, huella de idempotencia, límite de intentos de
login, reglas del transportista simulado, validación del cuerpo de la
API, hash de contraseñas, capacidad de los repartidores, a quién se le
asigna un pedido (y la lista de espera), hora de corte, orden de la hoja
de ruta recomendada y sus links a Google Maps, validación de la foto del
remito, tarifa mensual, datos de quien recibe, intentos de entrega, fecha
prometida, horario estimado, textos de los avisos al cliente y al
comercio, cuándo no se puede cancelar un pedido (ADR-048) e
indicadores (con la comparación contra el período anterior).

17 tests de integración de la API REST (REST Assured), contra Rabbit ya
desplegado y con una cuenta ERP:

```bash
mvn verify -Pintegracion -Drabbit.erp.usuario=<erp> -Drabbit.erp.clave=<clave> -Drabbit.punto=<id punto de picking>
```

Prueban alta, idempotencia, errores en Problem Details, pertenencia,
cancelación y seguimiento público (`ApiRestIT`), y los datos de quien
recibe (con el email obligatorio), la fecha prometida y que el
seguimiento no exponga a quien recibe (`MejorasUltimaMillaIT`). Los pedidos que crean llevan
`[TEST]` en la dirección. Conviene correrlos contra una base local, no contra la
compartida del equipo: los pedidos de prueba quedan guardados (se borran
con la opción A de `scripts/Rabbit-limpieza-datos-de-prueba.sql`).

Para probar a mano, de punta a punta: [FLUJO-DE-PRUEBAS.md](docs/pruebas/FLUJO-DE-PRUEBAS.md)
(recorrido guiado por pantalla) y [PRUEBAS-Y-COMANDOS.md](docs/pruebas/PRUEBAS-Y-COMANDOS.md)
(comandos de cada sistema y lista para marcar). Para la API y los
sistemas externos hay una colección de Postman lista para importar:
[`Rabbit.postman_collection.json`](docs/pruebas/Rabbit.postman_collection.json).

## Apagar todo

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command=":shutdown"   # WildFly (Rabbit, broker y simulados)
```
