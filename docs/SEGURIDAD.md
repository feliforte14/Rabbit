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
| `COMERCIO` | Un comercio: ve solo sus pedidos, su stock y sus puntos de picking | Un administrador, desde `usuarios.xhtml`, asociándolo a un comercio activo |
| `REPARTIDOR` | Un repartidor: ve y mueve solo sus entregas | Un administrador, desde `usuarios.xhtml`, asociándolo a un repartidor (una cuenta por repartidor) |
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
| Alta y cambios de comercios, depósitos, stock y repartidores; `confirmarPedido` y `cancelarPedido` | Solo el personal de Rabbit (`ADMINISTRADOR`, `OPERADOR`) |
| `despacharPedido`, `registrarEntrega` | Personal de Rabbit o el `REPARTIDOR` que tiene asignado ese pedido |
| `registrarPedidoExterno` | Personal de Rabbit (simulación) o el `ERP` |
| Puntos de picking (alta, baja, reactivación) | Personal de Rabbit o el `COMERCIO` dueño |
| Consultas del portal (`listarPedidosDelComercioActual`, `listarStockDelComercioActual`, `listarDelComercioActual`, `listarCobrosDelComercioActual`) | Solo `COMERCIO`, y solo lo suyo. El cobro guarda el comercio dueño, así que Pagos valida la pertenencia sin consultar a Pedidos |
| Listados de pedidos, pedidos del ERP y cobros (`listarTodos`, `listarPedidosExternos`, `listarEntregasEnCurso`, `obtenerCobroDePedido`) | Solo el personal de Rabbit. `consultarPedidoExterno`, también el `ERP`. `consultarEstadoPedido` es pública a propósito (seguimiento sin login, solo el estado) |
| Transportistas: alta, baja, derivar un pedido (`derivarATransportista`), envíos | Solo el personal de Rabbit. Un `COMERCIO` ve solo los envíos de sus pedidos (`listarEnviosDelComercioActual`) |
| Ruteo: tablero de entregas / entregas del repartidor | Personal de Rabbit / solo el `REPARTIDOR`, y solo las suyas |
| Ruteo: zonas (alta, baja, reactivación), `despacharPedido`, `despacharZona`, `listarPendientesPorZona`; zona de un repartidor (`asignarZona`) | Solo el personal de Rabbit |
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

### Pertenencia: cada uno ve y mueve solo lo suyo

El rol no alcanza: dos comercios tienen el mismo rol. `IContextoUsuario`
resuelve a quién representa el usuario que llama (la cuenta de la tabla
`usuarios`, asociada a un comercio o a un repartidor) a partir de la
identidad autenticada. Los servicios nunca reciben el comercio o el
repartidor del cliente:

- las consultas del portal del comercio filtran por su comercio;
- `despacharPedido` y `registrarEntrega` rechazan a un `REPARTIDOR` que no
  tiene asignado ese pedido;
- `ComercioService` rechaza que un `COMERCIO` toque puntos de picking de
  otro comercio (y `puntos-picking.xhtml` ignora el `idComercio` de la URL
  para un comercio).

El seguimiento de envíos (`SeguimientoDeEnvios`, un timer sin usuario)
mueve pedidos con las mismas operaciones que el personal, así que corre
con `@RunAs("OPERADOR")`: una identidad de sistema con ese rol. WildFly
avisa una vez (`ELYEE01007`) que no existe un usuario llamado así y crea
la identidad; es lo esperado.

Las operaciones que disparan el listener JMS y los timers (sin usuario)
siguen con `@PermitAll` de clase; por eso cada EJB que tiene alguna
restricción declara `@PermitAll` a nivel de clase y `@RolesAllowed` en
los métodos que solo invoca una persona.

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
  pisen;
- cada archivo se reemplaza de forma atómica (temporal + move), y los dos
  van juntos: si falla el segundo, el primero vuelve a su contenido
  original;
- si el alta se deshace después de escribir el realm (por ejemplo, falla
  el commit), se quita al usuario del realm. En la baja, el caso inverso
  deja la cuenta sin acceso, que es el lado seguro.

### Control de UX en la vista

Cada página tiene su guardián (`<f:viewAction>`): `exigirPersonal`,
`exigirAdministrador`, `exigirComercio`, `exigirRepartidor` o
`exigirPersonalOComercio`. Sin sesión mandan al login; con sesión pero
sin el tipo de usuario correcto, a la página de inicio de ese usuario.
Cada tipo de usuario ve su propio menú, y un usuario `ERP` no puede
entrar a la web (el login lo rechaza). **No es seguridad**: la vista puede
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

### Operaciones sin autenticación

- `GET /api/seguimiento/{idPedido}` (`SeguimientoResource`, `@PermitAll`),
  a propósito: devuelve solo el estado del pedido, sin importes, cobros ni
  datos del comercio.
- El banco simulado (`BancoLegadoService`, SOAP): no es parte de Rabbit
  sino el sistema externo simulado, pero se publica en el mismo WAR y sin
  autenticación (ver Limitaciones conocidas).
- Los transportistas simulados (`/api/simulador/transportista-rest` y
  `TransportistaLegadoService`): como el banco, son sistemas de otras
  empresas simulados en el mismo WAR, sin autenticación. El banco en Node.js
  (`banco-legado/`) tampoco tiene autenticación.

Además, el login y la página de error, que tienen que verse sin sesión.

## Limitaciones conocidas

- Sin HTTPS: el login y el HTTP Basic del ERP viajan en claro. En
  producción iría `transport-guarantee CONFIDENTIAL` y TLS en WildFly.
- Contraseñas con SHA-256 sin salt, mínimo de 6 caracteres y sin límite
  de intentos de login.
- Un usuario `ERP` puede cargar pedidos de cualquier comercio: no está
  atado al suyo.
- El banco simulado (`BancoLegadoService`) se publica sin autenticación.
  En producción no viviría dentro de Rabbit: por eso existe también como
  servicio aparte en Node.js (`banco-legado/`), que Rabbit usa apuntando
  `rabbit.banco.wsdl`. Tampoco tiene autenticación.
- `/api/seguimiento/{id}` usa IDs secuenciales: se puede recorrer el
  estado de todos los pedidos (solo el estado).
