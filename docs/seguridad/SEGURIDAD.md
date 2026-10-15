# Seguridad

## Autenticación

- El login (`request.login`) lo valida WildFly con el dominio
  `ApplicationDomain`, que busca la cuenta primero en la tabla `usuarios`
  de la base compartida (un `jdbc-realm` de Elytron) y, si no está ahí,
  en los archivos de add-user.sh de esa máquina (ADR-031,
  `scripts/configurar-realm-compartido.cli`). No hay un `IdentityStore`
  propio.
- Al registrar un usuario, `UsuarioService` guarda en la tabla `usuarios`
  su perfil y el hash de su contraseña. Como la base es compartida, la
  cuenta entra en el WildFly de cualquier integrante.
- **No hay alta pública de cuentas:** los usuarios los crea un
  `ADMINISTRADOR`. El primer administrador se crea en el servidor con
  `add-user.sh` (README, paso 5).
- Al iniciar sesión se renueva el ID de sesión (`changeSessionId`),
  contra *session fixation*.
- Contraseñas: **bcrypt** (costo 10, con sal propia) en
  `usuarios.hash_clave` (`Claves`), y es lo que valida el login. El realm
  JDBC solo lee el hash de cuentas activas: una baja corta el acceso en
  todas las máquinas a la vez. Las cuentas creadas antes de ADR-031 siguen
  en los archivos de add-user.sh (MD5) hasta que un administrador les
  restablezca la contraseña. La columna vieja `passwordhash` (SHA-256 sin
  salt, que nadie usaba) la borra `LimpiezaDeCredenciales` al desplegar
  (ADR-023).
- **Límite de intentos de login:** 5 intentos fallidos seguidos bloquean
  ese usuario 15 minutos (`LimiteDeIntentos`); el login ni consulta al
  realm mientras dura. El mensaje es el mismo exista o no el usuario. Vive
  en memoria de cada servidor; la API del ERP (HTTP Basic) la autentica
  WildFly y no pasa por ese freno.

## Roles

| Rol | Quién | Cómo se crea |
|---|---|---|
| `ADMINISTRADOR` | Personal de Rabbit con permisos totales | El primero con `add-user.sh -a -u <usuario> -p '<clave>' -g ADMINISTRADOR`; los demás, un administrador desde `personal/usuarios.xhtml` |
| `OPERADOR` | Personal de Rabbit | Un administrador, desde `personal/usuarios.xhtml` |
| `COMERCIO` | Un comercio: ve solo sus pedidos, su stock y sus puntos de picking | Un administrador, desde `personal/usuarios.xhtml`, asociándolo a un comercio activo |
| `REPARTIDOR` | Un repartidor: ve y mueve solo sus entregas | Un administrador, desde `personal/usuarios.xhtml`, asociándolo a un repartidor (una cuenta por repartidor) |
| `ERP` | Sistema de un comercio que usa la API REST (no es una persona): carga y ve solo los pedidos de su comercio | Un administrador, desde `personal/usuarios.xhtml`, asociándolo a un comercio activo |

La API REST del ERP se autentica con HTTP Basic (`web.xml`:
`security-constraint` sobre `/api/v1/pedidos-externos` + `login-config`
BASIC). Las pantallas JSF no cambian: siguen con `login.xhtml`, que
rechaza al usuario ERP.

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
| `ComercioService.eliminarComercio` | Borra físicamente el comercio y sus puntos de picking en cascada, y su historial cancelado (pedidos, avisos, cobros anulados, envíos; ADR-028) |
| `UsuarioService.registrarUsuario` | Crea cuentas con acceso al sistema. Si fuera público, cualquiera se daría un `OPERADOR` |
| Alta y cambios de comercios, depósitos, stock y repartidores; `confirmarPedido` y `cancelarPedido` | Solo el personal de Rabbit (`ADMINISTRADOR`, `OPERADOR`) |
| `despacharPedido` (EN_CAMINO) | Personal de Rabbit o el `REPARTIDOR` que tiene asignado ese pedido |
| `registrarEntrega` (ENTREGADO) | Solo el `REPARTIDOR` que lleva el pedido o un `ADMINISTRADOR`, y siempre con la foto del remito (ADR-033): dispara el cobro contra entrega. La foto se valida por sus primeros bytes (JPEG, PNG o WEBP) y hasta 5 MB |
| `obtenerRemito` (`/remito?pedido=ID`) | El personal, el `COMERCIO` dueño del pedido y su `REPARTIDOR`. Cualquier otro caso responde 404, sin distinguir si existe |
| `registrarUbicacion` | Solo el `REPARTIDOR`, para sí mismo y mientras tiene entregas en curso (ADR-035). El seguimiento público muestra la ubicación solo si el repartidor lleva ESE pedido |
| `liquidarMes`, `reintentarCobro` | Solo `ADMINISTRADOR` (o el timer mensual con `@RunAs`): cobran por el banco (ADR-034) |
| `previsualizarMesEnCursoDelComercioActual`, `resumenEntregasDelComercioActual` | Solo `COMERCIO`, y solo con sus entregas: el resumen de todos los comercios (`resumenEntregas`) es del personal |
| `UsuarioService.eliminarUsuario`, `RepartidorService.eliminarRepartidor`, `ZonaService.eliminarZona` | Solo `ADMINISTRADOR`: borran físicamente y no se deshacen (ADR-036). No se puede eliminar la propia cuenta, ni al último administrador activo, ni un repartidor con entregas en curso o con cuenta |
| `IConsultaStock.pesosPorItem` | Sin rol a propósito: la usa la sincronización de pedidos del ERP, que corre sin usuario (JMS y timer). Devuelve solo el peso de cada ítem; el listado completo del stock sigue siendo del personal |
| `registrarPedidoExterno` | Personal de Rabbit (simulación) o el `ERP`, que solo carga pedidos de su comercio (el comercio sale de su cuenta, no del cuerpo del pedido) |
| `cancelarPedidoExterno` | Solo el `ERP`, y solo pedidos de su comercio que todavía estén pendientes |
| Puntos de picking (alta, baja, reactivación) | Personal de Rabbit o el `COMERCIO` dueño |
| Consultas del portal (`listarPedidosDelComercioActual`, `listarStockDelComercioActual`, `listarDelComercioActual`, `listarCobrosDelComercioActual`) | Solo `COMERCIO`, y solo lo suyo. El cobro guarda el comercio dueño, así que Pagos valida la pertenencia sin consultar a Pedidos |
| Listados de pedidos, pedidos del ERP y cobros (`listarTodos`, `listarPedidosExternos`, `listarEntregasEnCurso`, `obtenerCobroDePedido`) | Solo el personal de Rabbit. `consultarPedidoExterno`, también el `ERP` (solo los de su comercio: uno ajeno responde como inexistente). `consultarSeguimiento` es pública a propósito (seguimiento sin login por código aleatorio, solo el estado) |
| Transportistas: alta, baja, derivar un pedido (`derivarATransportista`), envíos | Solo el personal de Rabbit. Un `COMERCIO` ve solo los envíos de sus pedidos (`listarEnviosDelComercioActual`) |
| Ruteo: tablero de entregas / entregas del repartidor | Personal de Rabbit / solo el `REPARTIDOR`, y solo las suyas |
| Ruteo: zonas (alta, baja, reactivación), `despacharPedido`, `despacharZona`, `listarPendientesPorZona`; zona de un repartidor (`asignarZona`) | Solo el personal de Rabbit |
| `UsuarioService.listarTodos` | Expone el padrón completo de usuarios |
| `UsuarioService.darDeBaja` | Deja a un usuario sin acceso |
| `UsuarioService.reactivarUsuario` | Le devuelve el acceso a una cuenta dada de baja, con una contraseña nueva (la anterior no se puede recuperar del hash). Revalida que su comercio siga activo o que su repartidor no tenga otra cuenta activa |
| `UsuarioService.restablecerClave` | Cambia la contraseña de una cuenta activa: quien la cambia puede entrar como esa cuenta. También pasa a la base las cuentas creadas antes de ADR-031 |
| `PagoService.anularCobro` | Revierte dinero ya registrado. Por eso cancelar un pedido CONFIRMADO (que ya tiene cobro) solo lo puede hacer un `ADMINISTRADOR` |
| `PedidosExternosResource` (`/api/v1/pedidos-externos`) | Un sistema externo carga pedidos en Rabbit: solo el rol `ERP`. Sin credenciales responde `401`; con un usuario de otro rol, o un ERP sin comercio asociado, `403`. El `401` y el `403` por rol los responde WildFly en HTML, antes de llegar al código: es la seguridad declarativa en acción |

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
  tiene asignado ese pedido, y el remito solo lo ve el comercio dueño o
  ese repartidor;
- `ComercioService` rechaza que un `COMERCIO` toque puntos de picking de
  otro comercio (y `comercio/puntos-picking.xhtml` ignora el `idComercio` de la URL
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

Desde ADR-031 las altas van solo a la base, dentro de la transacción: si
se deshace, no queda nada a medias. Los archivos de add-user.sh de cada
máquina siguen existiendo (administrador inicial, cuentas viejas), y
`ApplicationRealmSync` los toca en dos casos: para no crear desde la app
una cuenta con un nombre que ya existe ahí, y para borrar la línea de una
cuenta que se da de baja o pasa a la base. Esos archivos son texto
`usuario=valor` por línea; un username con salto de línea, `=` o `:`
podría alterar las entradas de otros usuarios. Por eso:

- solo se aceptan letras, números, punto, guion y guion bajo, entre 3 y
  30 caracteres (se valida en `UsuarioService` y otra vez en
  `ApplicationRealmSync`);
- las escrituras se serializan, y cada archivo se reemplaza de forma
  atómica (temporal + move); si falla el segundo, el primero vuelve a su
  contenido original.

Una baja de una cuenta vieja borra su línea solo en la máquina donde se
hace; en las demás la bloquea el realm JDBC, que encuentra la cuenta
inactiva en la base y no sigue buscando en los archivos.

### Control de UX en la vista

Cada página tiene su guardián (`<f:viewAction>`): `exigirPersonal`,
`exigirAdministrador`, `exigirComercio`, `exigirRepartidor` o
`exigirPersonalOComercio`. Sin sesión mandan al login; con sesión pero
sin el tipo de usuario correcto, a la página de inicio de ese usuario.
Cada tipo de usuario ve su propio menú, y un usuario `ERP` no puede
entrar a la web (el login lo rechaza). Las páginas están en una carpeta
por tipo de usuario (`personal/`, `comercio/`, `repartidor/`), y la
plantilla con el menú vive en `WEB-INF/plantillas/`, que el contenedor
nunca sirve por URL. El link "Ver recorrido en el mapa" manda las
direcciones del pedido a Google solo cuando alguien lo toca (Rabbit no
llama a ningún servicio de mapas), y abre en otra pestaña con
`rel="noopener noreferrer"`. **No es seguridad**: la vista puede
ocultar botones, pero la autorización siempre la impone el EJB.

### Errores

JSF corre en modo `Production` y `web.xml` define páginas de error: ante
un error el usuario ve `error.html`, una página genérica sin stack trace
ni detalles internos; el detalle queda solo en el log del servidor. Una
vista vencida (sesión expirada) vuelve al login. La API REST no usa esas
páginas: sus errores salen en Problem Details (`application/problem+json`,
ver `ProblemaMapper`), también sin stack traces ni errores de la base. Los
únicos que no salen así son el `401` y el `403` por rol: el contenedor
corta el pedido antes de que llegue a Rabbit y responde con su página
mínima (`<body>Unauthorized</body>`), que tampoco revela nada.

### HTTPS obligatorio

Todo lo de Rabbit viaja por HTTPS: las pantallas (el login manda la
contraseña), la API del ERP (HTTP Basic manda usuario y clave en cada
pedido) y el seguimiento público. Lo impone `web.xml` con
`transport-guarantee CONFIDENTIAL` sobre `/` (el patrón por defecto) y
sobre `/api/v1/pedidos-externos`: un pedido por HTTP (8080) recibe un `302`
a la misma URL en HTTPS (8443, el `https-listener` de WildFly, que en local
usa un certificado autofirmado).

- **Excepción:** los sistemas de otras empresas que se simulan en el mismo
  WAR (`/api/simulador/*`, `/BancoLegadoService`,
  `/TransportistaLegadoService`) siguen por HTTP. No son parte de Rabbit, y
  Rabbit los llama por HTTP como a cualquier sistema externo configurado
  así: la redirección rompería esas llamadas.
- **Cookie de sesión:** `Secure` (solo viaja por HTTPS) y `HttpOnly`
  (JavaScript no la puede leer). Y la sesión viaja solo en la cookie
  (`tracking-mode COOKIE`): nunca como `;jsessionid=` en la URL, donde se
  podría filtrar al copiarla o en el encabezado `Referer`.
- **El ERP tiene que llamar directo a HTTPS:** la redirección es para el
  navegador. Si un ERP manda un pedido por HTTP, sus credenciales ya
  viajaron en claro antes del `302`.

### Credenciales fuera del repositorio

El repositorio es público: ninguna credencial va en el código. Las de la
base viven en el datasource de WildFly y las de management (para
`mvn wildfly:deploy`) en `~/.m2/settings.xml` (README, paso 3).

### Operaciones sin autenticación

- `seguimiento.xhtml?codigo=RB-...` (`SeguimientoBean`): la misma consulta
  que la API de abajo, en una página para personas. Muestra solo el estado
  (con una línea de tiempo); el código aleatorio es lo único que la abre.
- `GET /api/v1/seguimiento/{codigo}` (`SeguimientoResource`, `@PermitAll`),
  a propósito: devuelve solo el estado del pedido, sin importes, cobros ni
  datos del comercio. Entra por un código aleatorio (`RB-` + 10
  caracteres, `SecureRandom`), no por el ID secuencial, así no se puede
  recorrer el estado de todos los pedidos.
- El banco simulado (`BancoLegadoService`, SOAP): no es parte de Rabbit
  sino el sistema externo simulado, pero se publica en el mismo WAR y sin
  autenticación (ver Limitaciones conocidas).
- Los transportistas simulados (`/api/simulador/transportista-rest` y
  `TransportistaLegadoService`): como el banco, son sistemas de otras
  empresas simulados en el mismo WAR, sin autenticación.

Además, el login y la página de error, que tienen que verse sin sesión.

## Limitaciones conocidas

- El certificado HTTPS de WildFly es autofirmado (el navegador advierte,
  `curl` necesita `-k`). En producción iría un certificado de una
  autoridad reconocida en el `key-store` de Elytron.
- Las cuentas que siguen en los archivos de add-user.sh (el administrador
  inicial, las viejas sin migrar) tienen un MD5 de
  `usuario:ApplicationRealm:contraseña`, rápido de probar. Las de la base
  usan bcrypt. Mínimo de 6 caracteres.
- El límite de intentos vive en memoria de cada servidor: se pierde al
  reiniciar y, con varios nodos, cada uno cuenta por su lado. La API del
  ERP (HTTP Basic, autenticada por WildFly) no tiene ese freno.
- El banco simulado (`BancoLegadoService`) se publica sin autenticación.
  En producción no viviría dentro de Rabbit: Rabbit lo puede reemplazar
  por otro con el mismo WSDL apuntando `rabbit.banco.wsdl` (en la
  rama `desafio-heterogeneidad`, un banco en Node.js, también sin autenticación).
- Los pedidos creados antes del código de seguimiento no tienen código:
  no se pueden seguir por la API pública.
