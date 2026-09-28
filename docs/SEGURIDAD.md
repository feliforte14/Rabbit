# Seguridad

## Autenticación

- Jakarta Security contra el `ApplicationRealm` nativo de WildFly (no hay
  un `IdentityStore` propio).
- Al registrar un usuario, `UsuarioService` lo guarda en la tabla
  `usuarios` y lo sincroniza al realm (`ApplicationRealmSync`, que
  escribe `application-users.properties`).
- Contraseñas:
  - Columna `passwordHash`: SHA-256 sin salt (`PasswordUtil`).
    Simplificación consciente para el TP, **no apta para producción**.
  - Realm: MD5 de `usuario:ApplicationRealm:contraseña`, el formato que
    exige WildFly.

## Roles

| Rol | Quién | Cómo se crea |
|---|---|---|
| `ADMINISTRADOR` | Personal de Rabbit con permisos totales | Desde `usuarios.xhtml` (enum `Rol`) |
| `OPERADOR` | Personal de Rabbit | Desde `usuarios.xhtml` (enum `Rol`) |
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

Cuando no alcanza con el rol, el EJB consulta
`SessionContext.isCallerInRole(...)`: `UsuarioService.puedeElegirRol()`
permite crear un `ADMINISTRADOR` solo a otro administrador, o a
cualquiera mientras no exista ninguno activo (arranque del sistema).

Qué permite `usuarios.xhtml` (pública, enlazada desde el login):

| Quién | Qué puede hacer |
|---|---|
| Sin sesión u `OPERADOR` | Solo crearse una cuenta, siempre como `OPERADOR` |
| `ADMINISTRADOR` | Ver el padrón, dar de baja usuarios y crear otros administradores |
| Cualquiera, mientras no exista ningún administrador activo | Crear el primer `ADMINISTRADOR` |

La regla la impone `UsuarioService`; la vista solo oculta lo que no
corresponde.

### Control de UX en la vista

`SesionBean.exigirSesion()` (vía `<f:viewAction>`) redirige a
`login.xhtml` si no hay sesión. **No es seguridad**: la vista puede
ocultar botones, pero la autorización siempre la impone el EJB.

### Operación pública

`GET /api/seguimiento/{idPedido}` (`SeguimientoResource`, `@PermitAll`)
es la única operación sin autenticación: devuelve solo el estado del
pedido, sin importes, cobros ni datos del comercio.

## Planificado

| Operación | Rol | Motivo |
|---|---|---|
| Listado de cobros | `ADMINISTRADOR` | Información financiera |
