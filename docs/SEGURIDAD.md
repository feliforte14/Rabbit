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

`ADMINISTRADOR` y `OPERADOR` (enum `Rol`).

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

## Planificado (Entrega 2)

| Operación | Rol | Motivo |
|---|---|---|
| `IRegistroCobros.anularCobro` | `ADMINISTRADOR` | Revierte dinero ya registrado |
| Listado de cobros | `ADMINISTRADOR` | Información financiera |
| `POST /api/pedidos-externos` | A definir | Endpoint expuesto a sistemas externos: requiere autenticación propia del partner |
