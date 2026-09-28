# Seguridad

## Autenticación

- Jakarta Security contra el `ApplicationRealm` nativo de WildFly (no hay
  un `IdentityStore` propio).
- Al registrar un usuario, `UsuarioService` lo guarda en la tabla
  `usuarios` y lo sincroniza al realm (`ApplicationRealmSync`, que
  escribe `application-users.properties`).
- **No hay alta pública de cuentas:** los usuarios los crea un
  `ADMINISTRADOR`. El primer administrador se crea en el servidor con
  `add-user.sh` (README, paso 5).
- Al iniciar sesión se renueva el ID de sesión (`changeSessionId`),
  contra *session fixation*.
- Contraseñas:
  - Columna `passwordHash`: SHA-256 sin salt (`PasswordUtil`).
    Simplificación consciente para el TP, **no apta para producción**.
  - Realm: MD5 de `usuario:ApplicationRealm:contraseña`, el formato que
    exige WildFly.

## Roles

| Rol | Quién | Cómo se crea |
|---|---|---|
| `ADMINISTRADOR` | Personal de Rabbit con permisos totales | El primero con `add-user.sh -a -u <usuario> -p '<clave>' -g ADMINISTRADOR`; los demás, un administrador desde `usuarios.xhtml` |
| `OPERADOR` | Personal de Rabbit | Un administrador, desde `usuarios.xhtml` |
| `ERP` | Sistema del comercio que usa la API REST (no es una persona) | Solo en WildFly: `add-user.sh -a -u <usuario> -p '<clave>' -g ERP` |

La API REST del ERP se autentica con HTTP Basic (`web.xml`:
`security-constraint` sobre `/api/pedidos-externos` + `login-config`
BASIC). Las pantallas JSF no cambian: siguen con `login.xhtml`.

## Autorización declarativa

La autorización real está en la capa de Negocio, sobre los EJB:

| Anotación | Dónde | Para qué |
|---|---|---|
| `@DeclareRoles({"ADMINISTRADOR", "OPERADOR"})` | Clase del EJB | Documenta los roles del componente |
| `@PermitAll` | Clase del EJB | Necesario: WildFly está con `default-missing-method-permissions-deny-access=true`, así que sin esto todo método sin anotación queda denegado |
| `@RolesAllowed("ADMINISTRADOR")` | Método sensible | La restricción real |

### Operaciones sensibles protegidas (implementado)

| Operación | Por qué es sensible |
|---|---|
| `ComercioService.eliminarComercio` | Borra físicamente el comercio y sus puntos de picking en cascada |
| `UsuarioService.registrarUsuario` | Crea cuentas con acceso al sistema. Si fuera público, cualquiera se daría un `OPERADOR` |
| `UsuarioService.listarTodos` | Expone el padrón completo de usuarios |
| `UsuarioService.darDeBaja` | Deja a un usuario sin acceso |
| `PagoService.anularCobro` | Revierte dinero ya registrado. Por eso cancelar un pedido CONFIRMADO (que ya tiene cobro) solo lo puede hacer un `ADMINISTRADOR` |
| `PedidosExternosResource` (`POST` y `GET /api/pedidos-externos`) | Un sistema externo carga pedidos en Rabbit: solo el rol `ERP`. Sin credenciales responde `401`; con un usuario de otro rol, `403` |

El suscriptor de Pagos al tópico (`SuscriptorPagosEstadoPedido`) corre
sin usuario: por eso `registrarCobroContraEntrega` no lleva restricción de
rol (y además solo acredita un cobro que ya existe).

Si el caller no tiene el rol, el contenedor lanza `EJBAccessException`;
la vista la traduce a un mensaje (WildFly igual la loguea como
`WFLYEJB0034`, es lo esperado).

### Reglas que dependen de datos

Cuando no alcanza con el rol, `UsuarioService.darDeBaja` controla además:

- que un administrador no se dé de baja a sí mismo;
- que no se dé de baja al último administrador activo (sin ninguno,
  nadie podría volver a crear usuarios desde la aplicación).

### Usuarios y archivos del realm

`ApplicationRealmSync` escribe el username en los archivos de properties
del realm, que son texto `usuario=valor` por línea. Un username con salto
de línea, `=` o `:` podría alterar las entradas de otros usuarios. Por
eso:

- solo se aceptan letras, números, punto, guion y guion bajo, entre 3 y
  30 caracteres (se valida en `UsuarioService` y otra vez en
  `ApplicationRealmSync`);
- no se puede crear un usuario que ya exista en el realm (por ejemplo, el
  del ERP creado con `add-user.sh`): lo pisaría;
- las escrituras se serializan para que dos altas simultáneas no se
  pisen.

### Control de UX en la vista

`SesionBean.exigirSesion()` (vía `<f:viewAction>`) redirige a
`login.xhtml` si no hay sesión, y `exigirAdministrador()` saca de
`usuarios.xhtml` a quien no es `ADMINISTRADOR`. El enlace "Usuarios" del
menú solo lo ve un administrador. **No es seguridad**: la vista puede
ocultar botones, pero la autorización siempre la impone el EJB.

### Errores

JSF corre en modo `Production` y `web.xml` define páginas de error: ante
un error el usuario ve `error.html`, una página genérica sin stack trace
ni detalles internos; el detalle queda solo en el log del servidor. Una
vista vencida (sesión expirada) vuelve al login.

### Credenciales fuera del repositorio

El repositorio es público: ninguna credencial va en el código. Las de la
base viven en el datasource de WildFly y las de management (para
`mvn wildfly:deploy`) en `~/.m2/settings.xml` (README, paso 3).

### Operación pública

`GET /api/seguimiento/{idPedido}` (`SeguimientoResource`, `@PermitAll`)
es la única operación sin autenticación: devuelve solo el estado del
pedido, sin importes, cobros ni datos del comercio.

## Planificado

| Operación | Rol | Motivo |
|---|---|---|
| Listado de cobros | `ADMINISTRADOR` | Información financiera |

## Limitaciones conocidas

- Sin HTTPS: el login y el HTTP Basic del ERP viajan en claro. En
  producción iría `transport-guarantee CONFIDENTIAL` y TLS en WildFly.
- Contraseñas con SHA-256 sin salt, mínimo de 6 caracteres y sin límite
  de intentos de login.
- Un usuario `ERP` puede cargar pedidos de cualquier comercio: no está
  atado al suyo.
- El banco simulado (`BancoLegadoService`) se publica sin autenticación.
  En producción no viviría dentro de Rabbit.
- `/api/seguimiento/{id}` usa IDs secuenciales: se puede recorrer el
  estado de todos los pedidos (solo el estado).
