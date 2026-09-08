# Rabbit — Gestión de Comercios, Inventario y Pedidos

Aplicación Jakarta EE / JSF para la gestión de comercios, sus puntos de
picking, inventario (depósitos, ítems y reservas de stock), pedidos y
usuarios/seguridad. Arquitectura en capas: Presentación (JSF Managed Beans) →
Negocio (EJB `@Stateless` / `@Stateful`) → Datos (JPA/Hibernate) →
PostgreSQL.

> ⚠️ **Este README contiene credenciales reales** (WildFly admin y base de
> datos Postgres en Supabase) para poder levantar el sistema rápido durante
> el desarrollo.

## Requisitos

- Java 17 (el proyecto compila con `--release 17`, ver `pom.xml`)
- Maven 3.9+
- WildFly (probado con WildFly 41.0.0.Final) con el módulo del driver de
  PostgreSQL instalado
- Acceso a internet (la base de datos vive en Supabase, no es local)

## Base de datos

La app usa PostgreSQL alojado en Supabase a través del datasource JNDI
`java:jboss/datasources/RabbitDS` (ver
[`persistence.xml`](src/main/resources/META-INF/persistence.xml)).

| Dato | Valor |
|---|---|
| Host | `aws-0-sa-east-1.pooler.supabase.com` |
| Puerto | `5432` |
| Base de datos | `postgres` |
| Usuario | `postgres.jytjhmmacpkwimuyljsi` |
| Contraseña | `Rabbit7070.123123` |
| Connection URL (JDBC) | `jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres` |

`hibernate.hbm2ddl.auto=update` está configurado en `persistence.xml`, así
que Hibernate crea/actualiza las tablas automáticamente al arrancar — no
hace falta correr un script de esquema a mano.

### Configurar el datasource en WildFly

El datasource `RabbitDS` y el driver de PostgreSQL deben existir en el
`standalone.xml` de WildFly (subsistema `datasources`). Ejemplo del bloque
esperado:

```xml
<datasource jndi-name="java:jboss/datasources/RabbitDS" pool-name="RabbitDS" enabled="true" use-ccm="false">
    <connection-url>jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres</connection-url>
    <driver>postgresql</driver>
    <security user-name="postgres.jytjhmmacpkwimuyljsi" password="Rabbit7070.123123"/>
</datasource>
...
<drivers>
    <driver name="postgresql" module="org.postgresql">
        <driver-class>org.postgresql.Driver</driver-class>
        <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
    </driver>
</drivers>
```

Si el módulo `org.postgresql` no está instalado en WildFly, hay que
agregarlo (`module.xml` + el `.jar` del driver JDBC de PostgreSQL) antes de
arrancar el servidor.

## WildFly — consola de administración

El plugin de Maven (`wildfly-maven-plugin`, ver
[`pom.xml`](pom.xml#L29-L39)) usa estas credenciales para desplegar por
management API (puerto `9990`):

| Dato | Valor |
|---|---|
| Host de management | `127.0.0.1` |
| Puerto de management | `9990` |
| Usuario | `admin` |
| Contraseña | `Ricardo1.` |

Ese usuario debe existir en `standalone/configuration/mgmt-users.properties`
de tu instalación de WildFly. Si no existe, crearlo con el script
`add-user.sh` (o `.bat` en Windows) que trae WildFly en su carpeta `bin/`,
eligiendo "Management User" y usando el mismo usuario/contraseña de arriba
(o ajustando `pom.xml` si se usa otro).

## Cómo levantar el sistema

1. Arrancar WildFly (con el datasource `RabbitDS` ya configurado como se
   explica arriba):
   ```bash
   ./bin/standalone.sh
   ```
2. Desde la raíz de este proyecto, compilar y desplegar:
   ```bash
   mvn clean package wildfly:deploy
   ```
3. Abrir en el navegador:
   ```
   http://localhost:8080/Rabbit/login.xhtml
   ```
   (`login.xhtml` es la página de bienvenida configurada en
   [`web.xml`](src/main/webapp/WEB-INF/web.xml); desde ahí se accede al
   resto del sistema autenticándose con un usuario existente).

Para volver a desplegar después de un cambio de código:
```bash
mvn clean package wildfly:redeploy
```

## Arquitectura

El proyecto sigue una arquitectura en 3 capas, con comunicación estrictamente
unidireccional: **Presentación → Negocio → Datos**. Ninguna capa accede
directo a una capa no adyacente (la Presentación nunca toca la base de
datos, la capa de Datos nunca decide reglas de negocio).

| Capa | Tecnología | Responsabilidad |
|---|---|---|
| **Presentación** | JSF (`@Named` + `@ViewScoped`) | Renderiza las vistas Facelets (`.xhtml`) y captura la entrada del usuario. No contiene reglas de negocio propias. |
| **Negocio** | EJB `@Stateless` | Aplica las validaciones y reglas del dominio, orquesta las operaciones (`@Transactional`). No conoce detalles de la vista ni del motor de base de datos. |
| **Datos** | JPA / Hibernate | Persiste y recupera información. Traduce entre objetos Java y filas de la tabla. |

Cada componente de negocio replica el mismo esqueleto de paquetes, por
ejemplo `comercios`:

```
com.rabbit.comercios/
├── presentacion/     ← Managed Beans JSF (ComercioBean, PuntoPickingBean)
├── negocio/          ← EJB (ComercioService, IConsultaComercios, IRegistroComercios)
├── datos/
│   ├── model/         ← Entidades JPA (Comercio, PuntoPicking, Producto)
│   └── ComercioRepository.java, ProductoRepository.java
└── dto/               ← DTOs que viajan entre capas (ComercioDTO, PuntoPickingDTO, ...)
```

El sistema está organizado en cuatro componentes de negocio:

| Componente | Paquete | Responsabilidad |
|---|---|---|
| **Comercios** | `com.rabbit.comercios` | Alta/baja/consulta de comercios, sus puntos de picking y productos. |
| **Inventario** | `com.rabbit.inventario` | Depósitos, ítems de inventario y reservas de stock (`ReservaStock`). |
| **Pedidos** | `com.rabbit.pedidos` | Gestión y seguimiento de pedidos, sincronización con pedidos externos. |
| **Seguridad** | `com.rabbit.seguridad` | Usuarios, login y sincronización de roles contra el `ApplicationRealm` de WildFly. |

La mayoría de los servicios de negocio son EJB `@Stateless`, salvo
[`InventarioService`](src/main/java/com/rabbit/inventario/negocio/InventarioService.java),
que es `@Stateful` (con `@StatefulTimeout`) para poder mantener el estado de
una reserva de stock en curso durante la conversación del usuario; un
[`BarredorDeReservas`](src/main/java/com/rabbit/inventario/negocio/BarredorDeReservas.java)
libera automáticamente las reservas vencidas. La decisión de por qué
Inventario es stateful y el resto stateless está documentada como ADR en el
historial de commits del proyecto.

La seguridad es declarativa: los roles (`ADMINISTRADOR`, `OPERADOR`, ver
[`Rol`](src/main/java/com/rabbit/seguridad/datos/model/Rol.java)) se
sincronizan contra el `ApplicationRealm` de WildFly
([`ApplicationRealmSync`](src/main/java/com/rabbit/seguridad/negocio/ApplicationRealmSync.java)),
y las operaciones sensibles se protegen con `@RolesAllowed` a nivel de método
en los EJB (por ejemplo,
[`ComercioService.eliminarComercio`](src/main/java/com/rabbit/comercios/negocio/ComercioService.java)).

### Por qué `model` y `dto` están separados

- **`datos/model/`** contiene las **entidades JPA** (`Comercio`,
  `PuntoPicking`, `ReservaStock`, `Pedido`, `Usuario`, etc.): representan
  filas de la base de datos tal cual, con anotaciones de persistencia
  (`@Entity`, `@OneToMany`, `@JoinColumn`) y relaciones lazy. Están acopladas
  al motor de persistencia (Hibernate).
- **`dto/`** contiene objetos planos (`ComercioDTO`, `PuntoPickingDTO`, etc.)
  que viajan entre capas, sobre todo hacia la Presentación. Las entidades
  **nunca**
  se exponen directo a la vista: si `ComercioBean` trabajara con la entidad
  `Comercio`, quedaría acoplado a detalles de Hibernate (por ejemplo, acceder
  a una relación lazy fuera de una transacción tira `LazyInitializationException`),
  y cualquier cambio en el modelo de datos rompería la vista.

En resumen: `model` es "cómo se guarda", `dto` es "qué se muestra". Esta
separación permite cambiar la capa de Datos (agregar una columna, una
relación) sin tocar las vistas `.xhtml`.

## Funcionalidad disponible

- **Login y seguridad** (`login.xhtml`, `panel.xhtml`): autenticación contra
  el `ApplicationRealm` de WildFly y navegación protegida por rol.
- **Comercios** (`comercios.xhtml`): alta, baja lógica, reactivación y
  eliminación física de comercios — la eliminación física solo se permite si
  el comercio ya está dado de baja, y borra en cascada sus puntos de
  picking.
- **Puntos de picking** (`puntos-picking.xhtml`, accesible desde cada
  comercio): alta, baja lógica y reactivación de puntos de picking por
  comercio.
- **Inventario** (`depositos.xhtml`, `items.xhtml`): gestión de depósitos e
  ítems de inventario.
- **Reservas de stock** (`reservas.xhtml`): reserva de stock con
  vencimiento; una cuenta regresiva en pantalla (`reserva.js`) refleja el
  tiempo restante y el
  [`BarredorDeReservas`](src/main/java/com/rabbit/inventario/negocio/BarredorDeReservas.java)
  libera automáticamente las reservas vencidas.
- **Pedidos** (`pedidos.xhtml`): gestión y seguimiento de pedidos, con
  sincronización de pedidos externos vía
  [`SincronizadorDePedidos`](src/main/java/com/rabbit/pedidos/negocio/SincronizadorDePedidos.java).
- **Usuarios** (`usuarios.xhtml`): alta y consulta de usuarios del sistema,
  con asignación de rol (`ADMINISTRADOR` / `OPERADOR`).
- Validaciones de negocio centralizadas en el `negocio` de cada componente
  (por ejemplo,
  [`ComercioService`](src/main/java/com/rabbit/comercios/negocio/ComercioService.java)):
  campos obligatorios, formato y unicidad de CUIT, formato de email.
