# Rabbit — Gestión de Comercios

Aplicación Jakarta EE / JSF para el alta, baja, modificación y consulta de
comercios y sus sucursales. Arquitectura en capas: Presentación (JSF Managed
Beans) → Negocio (EJB `@Stateless`) → Datos (JPA/Hibernate) → PostgreSQL.

> ⚠️ **Este README contiene credenciales reales** (WildFly admin y base de
> datos Postgres en Supabase) para poder levantar el sistema rápido durante
> el desarrollo. Como el repositorio es público en GitHub, estas credenciales
> quedan expuestas a cualquiera. Antes de un uso más serio, rotarlas y
> moverlas fuera del control de versiones (variables de entorno, vault, etc.).

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
   http://localhost:8080/Rabbit/comercios.xhtml
   ```
   (`comercios.xhtml` es la página de bienvenida configurada en
   [`web.xml`](src/main/webapp/WEB-INF/web.xml)).

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

Cada componente de negocio (por ejemplo `comercios`) replica el mismo
esqueleto de paquetes:

```
com.rabbit.comercios/
├── presentacion/     ← Managed Beans JSF (ComercioBean, SucursalBean)
├── negocio/          ← EJB (ComercioService, ValidacionException)
├── datos/
│   ├── model/         ← Entidades JPA (Comercio, Sucursal)
│   └── ComercioRepository.java
└── dto/               ← DTOs que viajan entre capas (ComercioDTO, SucursalDTO, ...)
```

### Por qué `model` y `dto` están separados

- **`datos/model/`** contiene las **entidades JPA** (`Comercio`, `Sucursal`):
  representan filas de la base de datos tal cual, con anotaciones de
  persistencia (`@Entity`, `@OneToMany`, `@JoinColumn`) y relaciones lazy.
  Están acopladas al motor de persistencia (Hibernate).
- **`dto/`** contiene objetos planos (`ComercioDTO`, `SucursalDTO`, etc.) que
  viajan entre capas, sobre todo hacia la Presentación. Las entidades **nunca**
  se exponen directo a la vista: si `ComercioBean` trabajara con la entidad
  `Comercio`, quedaría acoplado a detalles de Hibernate (por ejemplo, acceder
  a una relación lazy fuera de una transacción tira `LazyInitializationException`),
  y cualquier cambio en el modelo de datos rompería la vista.

En resumen: `model` es "cómo se guarda", `dto` es "qué se muestra". Esta
separación permite cambiar la capa de Datos (agregar una columna, una
relación) sin tocar las vistas `.xhtml`.

## Funcionalidad disponible

- Alta, baja lógica, reactivación y eliminación física de comercios
  (`comercios.xhtml`) — la eliminación física solo se permite si el
  comercio ya está dado de baja, y borra en cascada sus sucursales.
- Alta, baja lógica y reactivación de sucursales por comercio
  (`sucursales.xhtml`, accesible desde el link "Ver sucursales" de cada
  comercio).
- Validaciones de negocio centralizadas en
  [`ComercioService`](src/main/java/com/rabbit/comercios/negocio/ComercioService.java):
  nombre/razón social obligatorios, formato y unicidad de CUIT, formato de
  email.
