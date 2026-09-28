# Rabbit

Plataforma de logística de última milla que conecta comercios con una red
de depósitos y repartidores. Trabajo práctico integrador de Desarrollo de
Aplicaciones II (UADE, 2.º cuatrimestre 2026), opción B "LogiRed".

## Qué hace

- **Comercios:** alta y gestión de comercios, sus productos y puntos de
  picking.
- **Inventario:** depósitos propios de Rabbit con stock consignado por los
  comercios, y reservas de stock con vencimiento.
- **Pedidos:** recepción de pedidos desde el ERP de cada comercio, su
  sincronización automática con stock y su seguimiento
  (`PENDIENTE → CONFIRMADO → EN_CAMINO → ENTREGADO`).
- **Seguridad:** usuarios con rol `ADMINISTRADOR` u `OPERADOR`.

## Tecnologías

Jakarta EE 10 sobre WildFly (perfil `standalone-full`), Java 17, JSF +
Facelets, EJB, JPA/Hibernate, PostgreSQL (Supabase), JMS (ActiveMQ
Artemis embebido), JAX-WS (SOAP), JAX-RS (REST), Jakarta Security,
Maven (WAR).

## Estructura

Cada componente vive en `com.rabbit.<componente>` y se divide en capas:

```
com.rabbit.<componente>/
├── presentacion/   ← Managed Beans JSF
├── negocio/        ← EJB e interfaces @Local
├── datos/          ← Repositorios (DAO) y entidades JPA (model/)
└── dto/            ← Objetos que cruzan capas y componentes
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
| Ruteo, Transportistas | — | Pendiente |

## Integraciones

| Integración | Tipo | Estado |
|---|---|---|
| Pedidos del ERP → sincronización | Asincrónica, cola JMS | Implementado |
| Pagos → banco legado (cobro y reversa) | Sincrónica, SOAP | Implementado |
| ERP del comercio → Rabbit (y seguimiento público) | Sincrónica, REST | Implementado |
| Cambios de estado del pedido → Notificaciones, Pagos | Asincrónica, tópico JMS | Implementado |

## Documentación técnica

El detalle y la justificación de cada decisión están en
[`docs/`](docs/README.md):

- [Arquitectura](docs/ARQUITECTURA.md): capas, componentes, interfaces y estados del pedido.
- [Patrones de diseño](docs/PATRONES.md)
- [Seguridad](docs/SEGURIDAD.md)
- [Transacciones](docs/TRANSACCIONES.md)
- [Mensajería sincrónica](docs/MENSAJERIA-SINCRONICA.md): SOAP con el banco legado (con circuit breaker) y API REST.
- [Mensajería asincrónica](docs/MENSAJERIA-ASINCRONICA.md): cola y tópico.
- [Decisiones (ADRs)](docs/DECISIONES.md)

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

El último comando tiene que responder `"outcome" => "success"`. Si la base
está en Supabase y responde `EAUTHQUERY ... connection to database not
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
desplegar: no hace falta correr ningún script de esquema.

**Excepción: valores nuevos en un enum.** Hibernate crea las columnas
`@Enumerated(EnumType.STRING)` con un `CHECK` que enumera los valores
válidos, y `update` no lo vuelve a generar cuando el enum crece. Si la
tabla ya existía, al usar un valor nuevo (por ejemplo `EN_CAMINO` en
`EstadoPedido`) el `UPDATE` falla por violación del check. Se resuelve
una vez, borrando el check viejo desde el SQL Editor de Supabase:

```sql
ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS pedidos_estado_check;
```

### 3. Usuario de management (una sola vez)

El `wildfly-maven-plugin` despliega por la API de management (puerto
`9990`) con el usuario configurado en [`pom.xml`](pom.xml). Ese usuario
tiene que existir en WildFly:

```bash
$WILDFLY_HOME/bin/add-user.sh -u <usuario> -p '<contraseña>' -s
```

### 4. Desplegar

```bash
mvn package wildfly:deploy
```

y abrir http://localhost:8080/Rabbit.

### 5. Primer acceso

El login valida contra el `ApplicationRealm` de WildFly, así que un
WildFly recién instalado no reconoce usuarios creados en otra instalación
aunque estén en la base. Hay dos formas de entrar la primera vez:

- **Desde la app:** si en la base no hay ningún administrador activo,
  "Registrate acá" (`usuarios.xhtml`) permite crear el primero.
- **Desde WildFly:** crear un usuario de aplicación con el rol
  `ADMINISTRADOR`:
  ```bash
  $WILDFLY_HOME/bin/add-user.sh -a -u <usuario> -p '<contraseña>' -g ADMINISTRADOR -s
  ```

Los usuarios que después se registran desde la app se sincronizan solos
contra el realm (ver `ApplicationRealmSync`).

### 6. Usuario del ERP para la API REST

La API `/api/pedidos-externos` exige el rol `ERP` (HTTP Basic). Ese
usuario representa a un sistema, no a una persona, así que se crea
directamente en WildFly:

```bash
$WILDFLY_HOME/bin/add-user.sh -a -u <usuario> -p '<contraseña>' -g ERP -s
```

Ver ejemplos de uso en [MENSAJERIA-SINCRONICA.md](docs/MENSAJERIA-SINCRONICA.md).
