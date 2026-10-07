# Rabbit

Plataforma de logística de última milla que conecta comercios con una red
de depósitos y repartidores. Trabajo práctico integrador de Desarrollo de
Aplicaciones II (UADE, 2.º cuatrimestre 2026), opción B "LogiRed".

## Qué hace

- **Comercios:** alta y gestión de comercios y sus puntos de picking. El
  catálogo de productos está modelado (entidad `Producto`) pero todavía no
  tiene servicio ni pantalla.
- **Inventario:** depósitos propios de Rabbit con stock consignado por los
  comercios, y reservas de stock con vencimiento.
- **Pedidos:** recepción de pedidos desde el ERP de cada comercio por una
  API REST versionada (`/api/v1`, alta idempotente, consulta y
  cancelación), su sincronización automática con stock y su seguimiento
  (`PENDIENTE → CONFIRMADO → EN_CAMINO → ENTREGADO`), también público por
  código de seguimiento.
- **Transportistas:** un pedido se puede derivar a una empresa de envíos
  externa (integrada por API REST o por SOAP legado), que lo lleva; Rabbit
  sigue el estado del envío y mueve el pedido solo.
- **Ruteo y entregas:** zonas de reparto por código postal; los pedidos
  pendientes se agrupan por zona y se despachan solos según quién cubre la
  zona (repartidores propios o un transportista). Hoja de ruta de cada
  pedido (de dónde se retira y adónde se entrega, con el recorrido en
  Google Maps) y tablero de entregas en curso.
- **Seguridad y vistas por tipo de usuario:** el personal de Rabbit
  (`ADMINISTRADOR`, `OPERADOR`) opera toda la red; un `COMERCIO` sigue sus
  pedidos, su stock y sus puntos de picking; un `REPARTIDOR` ve su hoja de
  ruta y marca retiro y entrega desde el celular; un `ERP` (el sistema del
  comercio) solo usa la API REST, y solo con los pedidos de su comercio.

## Tecnologías

Jakarta EE 10 sobre WildFly (perfil `standalone-full`), Java 17, JSF +
Facelets, EJB, JPA/Hibernate, PostgreSQL (Supabase), JMS (ActiveMQ
Artemis embebido), JAX-WS (SOAP), JAX-RS (REST), Jakarta Security,
Maven (WAR). El banco legado también está implementado en Node.js
([`banco-legado/`](banco-legado/README.md)), como servicio aparte con el
mismo contrato SOAP.

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
├── resources/rabbit/1_7/     ← CSS y JS versionados (tablas.js: tablas como tarjetas en el celular)
└── WEB-INF/
    ├── plantillas/template.xhtml  ← layout y menú (no se puede pedir por URL)
    ├── web.xml, beans.xml, jboss-ejb3.xml
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

| Integración | Tipo | Estado |
|---|---|---|
| Pedidos del ERP → sincronización | Asincrónica, cola JMS | Implementado |
| Pagos → banco legado (cobro y reversa) | Sincrónica, SOAP | Implementado |
| ERP del comercio → Rabbit (y seguimiento público) | Sincrónica, REST | Implementado |
| Cambios de estado del pedido → Notificaciones, Pagos | Asincrónica, tópico JMS | Implementado |
| Transportistas → transportista moderno / legado | Sincrónica, REST saliente / SOAP | Implementado |

## Documentación técnica

El detalle y la justificación de cada decisión están en
[`docs/`](docs/README.md):

- [Arquitectura](docs/ARQUITECTURA.md): capas, componentes, interfaces y estados del pedido.
- [Patrones de diseño](docs/PATRONES.md)
- [Seguridad](docs/SEGURIDAD.md)
- [Transacciones](docs/TRANSACCIONES.md)
- [Mensajería: justificación final](docs/MensajeriaFinal.md): por qué cada integración usa cola, tópico, SOAP o REST.
- [Mensajería sincrónica](docs/MENSAJERIA-SINCRONICA.md): SOAP con el banco legado (con circuit breaker) y API REST.
- [Contrato OpenAPI](docs/openapi.yaml) de la API REST (Swagger Editor o Postman).
- [Mensajería asincrónica](docs/MENSAJERIA-ASINCRONICA.md): cola y tópico.
- [Decisiones (ADRs)](docs/DECISIONES.md)
- [Flujo de pruebas](docs/FLUJO-DE-PRUEBAS.md): recorrido manual de punta a punta, para verificar y para la demo.
- [Desafíos opcionales](docs/DESAFIOS-OPCIONALES.md): resiliencia, escalabilidad medida, ADRs con alternativas y heterogeneidad tecnológica (banco en Node.js).

## Cómo levantar el sistema

### Requisitos

- Java 17 o superior (el proyecto compila con `--release 17`; probado con
  JDK 21).
- Maven 3.9+.
- WildFly 41 (probado con 41.0.1.Final), arrancado con el perfil
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
mvn package wildfly:deploy
```

y abrir https://localhost:8443/Rabbit. Rabbit exige HTTPS: si se entra por
`http://localhost:8080/Rabbit`, redirige solo. En local WildFly usa un
certificado autofirmado, así que el navegador muestra una advertencia la
primera vez (aceptarla) y `curl` necesita `-k`.

### 5. Primer acceso

El login valida contra el `ApplicationRealm` de WildFly, así que un
WildFly recién instalado no reconoce usuarios creados en otra instalación
aunque estén en la base. No hay alta pública de cuentas: el primer
administrador se crea en WildFly:

```bash
$WILDFLY_HOME/bin/add-user.sh -a -u <usuario> -p '<contraseña>' -g ADMINISTRADOR -s
```

Desde ahí, ese administrador crea los demás usuarios en "Usuarios"
(`personal/usuarios.xhtml`).

Los usuarios que crea un administrador desde la app se sincronizan solos
contra el realm (ver `ApplicationRealmSync`). Hay cinco tipos de cuenta:
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
tiene comercio y la API le responde `403`: hay que borrarlo del realm y
crearlo desde la app.

Contrato en [docs/openapi.yaml](docs/openapi.yaml) y ejemplos en
[MENSAJERIA-SINCRONICA.md](docs/MENSAJERIA-SINCRONICA.md).
