package com.rabbit.seguridad.negocio;

/**
 * CAPA DE NEGOCIO — componente ServicioDeUsuariosYSeguridad (EJB @Stateless)
 *
 * Tercer componente del sistema, con la misma arquitectura en capas que
 * ServicioDeComercios (también @Stateless): cada operación es autocontenida
 * y no depende de llamadas anteriores, así que no hace falta que el
 * contenedor dedique una instancia por cliente — un pool alcanza y escala
 * mejor. Contrastar con ServicioDeInventario (@Stateful), donde sí hace
 * falta recordar la reserva en curso entre llamadas.
 *
 * Gestiona el padrón de usuarios de la app (tabla "usuarios"), con el
 * hash bcrypt de cada contraseña. WildFly autentica leyendo esa misma
 * tabla con su realm JDBC (ADR-031) y de ahí saca el rol que evalúan las
 * anotaciones @RolesAllowed (ver ComercioService.eliminarComercio). Como
 * la base es compartida, una cuenta creada acá entra en el WildFly de
 * cualquier integrante.
 *
 * EVIDENCIA DE CICLO DE VIDA GESTIONADO POR EL CONTENEDOR:
 * @PostConstruct/@PreDestroy no los llama nadie del código de la app — los
 * dispara WildFly al tomar y devolver una instancia del pool. El hashCode()
 * en el log deja ver que, entre llamadas, puede tratarse de instancias
 * distintas (a diferencia de InventarioService, donde el mismo hashCode
 * se repite durante toda la conversación).
 */

import com.rabbit.comercios.negocio.IConsultaComercios;
import com.rabbit.repartidores.negocio.IGestionRepartidores;
import com.rabbit.seguridad.datos.UsuarioRepository;
import com.rabbit.seguridad.datos.model.Rol;
import com.rabbit.seguridad.datos.model.Usuario;
import com.rabbit.seguridad.dto.DatosUsuarioDTO;
import com.rabbit.seguridad.dto.UsuarioDTO;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// Seguridad declarativa, mismo esquema que ComercioService: @PermitAll a
// nivel de clase (este WildFly deniega por default todo método sin permiso
// declarado) y @RolesAllowed("ADMINISTRADOR") en lo sensible: dar de alta,
// listar el padrón y dar de baja. No hay alta pública: una cuenta nueva
// la crea un administrador, y el primer administrador se crea en el
// servidor con add-user.sh (ver README). Un alta abierta le daba a
// cualquiera un OPERADOR, que puede operar casi todo el sistema.
@Stateless
@DeclareRoles({"ADMINISTRADOR", "OPERADOR"})
@PermitAll
public class UsuarioService implements IConsultaUsuarios, IRegistroUsuarios {

    private static final Logger LOG = Logger.getLogger(UsuarioService.class.getName());

    @Inject
    private UsuarioRepository repository;

    @Resource
    private SessionContext contexto;

    // Para validar a quién representa una cuenta COMERCIO o REPARTIDOR.
    @Inject
    private IConsultaComercios comercios;

    @Inject
    private IGestionRepartidores repartidores;

    /** El contenedor tomó una instancia del pool para atender una llamada. */
    @PostConstruct
    public void alCrear() {
        LOG.info("[Usuarios] Instancia tomada del pool por el contenedor — " + hashCode());
    }

    /** El contenedor devuelve la instancia al pool (o la descarta). */
    @PreDestroy
    public void alDestruir() {
        LOG.info("[Usuarios] Instancia devuelta/descartada por el contenedor — " + hashCode());
    }

    // IRegistroUsuarios

    /**
     * Crea un usuario nuevo: guarda en la tabla "usuarios" su perfil y el
     * hash bcrypt de su contraseña, que es lo que valida WildFly al iniciar
     * sesión (ADR-031).
     *
     * @param datos datos ingresados en el formulario de alta
     * @return el ID asignado por la BD al nuevo usuario
     * @throws ValidacionException si el username/password son inválidos o el username ya existe
     */
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ADMINISTRADOR")
    public Long registrarUsuario(DatosUsuarioDTO datos) {
        validarUsername(datos.username);
        validarPassword(datos.password);
        if (repository.existeUsername(datos.username.trim()) || ApplicationRealmSync.existeEnRealm(datos.username.trim())) {
            throw new ValidacionException("Ya existe un usuario con el nombre \"" + datos.username.trim() + "\"");
        }

        Rol rol = datos.rol != null ? datos.rol : Rol.OPERADOR;
        validarAsociacion(rol, datos);

        Usuario usuario = new Usuario();
        usuario.setUsername(datos.username.trim());
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setIdComercio(representaUnComercio(rol) ? datos.idComercio : null);
        usuario.setIdRepartidor(rol == Rol.REPARTIDOR ? datos.idRepartidor : null);
        usuario.setHashClave(Claves.hashear(datos.password));
        return repository.guardar(usuario).getId();
    }

    /**
     * Reactiva una cuenta dada de baja con una contraseña nueva. Revalida
     * a quién representa: el
     * comercio pudo darse de baja, o el repartidor tener otra cuenta, desde
     * que esta se dio de baja.
     */
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ADMINISTRADOR")
    public void reactivarUsuario(Long id, String passwordNueva) {
        Usuario usuario = obtenerOFallar(id);
        if (usuario.isActivo()) {
            throw new ValidacionException("El usuario " + usuario.getUsername() + " ya está activo");
        }
        validarPassword(passwordNueva);
        // Alguien pudo crear en WildFly (add-user.sh) una cuenta con el mismo
        // nombre mientras esta estaba dada de baja: no se pisa.
        if (ApplicationRealmSync.existeEnRealm(usuario.getUsername())) {
            throw new ValidacionException("Ya existe en el servidor otra cuenta llamada " + usuario.getUsername());
        }
        DatosUsuarioDTO asociacion = new DatosUsuarioDTO();
        asociacion.idComercio = usuario.getIdComercio();
        asociacion.idRepartidor = usuario.getIdRepartidor();
        validarAsociacion(usuario.getRol(), asociacion);

        usuario.setActivo(true);
        usuario.setHashClave(Claves.hashear(passwordNueva));
        repository.actualizar(usuario);
    }

    /**
     * Le pone una contraseña nueva a una cuenta activa. Sirve para quien
     * olvidó la suya y para pasar a la base las cuentas creadas antes de
     * ADR-031, que solo entraban en la máquina donde se crearon.
     */
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ADMINISTRADOR")
    public void restablecerClave(Long id, String passwordNueva) {
        Usuario usuario = obtenerOFallar(id);
        if (!usuario.isActivo()) {
            throw new ValidacionException("El usuario " + usuario.getUsername()
                    + " está dado de baja: reactivalo con una contraseña nueva");
        }
        validarPassword(passwordNueva);
        usuario.setHashClave(Claves.hashear(passwordNueva));
        repository.actualizar(usuario);
        // Desde ahora entra con la contraseña de la base: la línea vieja del
        // realm de archivos de esta máquina ya no se usa.
        ApplicationRealmSync.bajaUsuario(usuario.getUsername());
    }

    /**
     * Baja lógica: el usuario deja de poder autenticarse (ver
     * Usuario.activo) en el WildFly de todos, porque el realm JDBC solo
     * acepta cuentas activas.
     *
     * @param id ID del usuario a dar de baja
     * @throws ValidacionException si el usuario no existe
     */
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ADMINISTRADOR")
    public void darDeBaja(Long id) {
        Usuario usuario = obtenerOFallar(id);
        exigirQueSePuedaSacar(usuario, "dar de baja");
        usuario.setActivo(false);
        repository.actualizar(usuario);
        // Una cuenta vieja (sin hash en la base) puede tener además una
        // línea en el realm de archivos de esta máquina: se borra. En las
        // otras máquinas la bloquea el realm JDBC (ADR-031).
        ApplicationRealmSync.bajaUsuario(usuario.getUsername());
    }

    /**
     * Baja física: borra la fila de "usuarios". Mismas protecciones que la
     * baja lógica; además se borra la línea que pudiera tener en el realm de
     * archivos de esta máquina (cuentas anteriores a ADR-031).
     */
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RolesAllowed("ADMINISTRADOR")
    public void eliminarUsuario(Long id) {
        Usuario usuario = obtenerOFallar(id);
        exigirQueSePuedaSacar(usuario, "eliminar");
        repository.eliminar(usuario);
        ApplicationRealmSync.bajaUsuario(usuario.getUsername());
    }

    // Ni la propia cuenta ni el último administrador activo: sin
    // administradores nadie podría volver a dar de alta usuarios desde la
    // aplicación.
    private void exigirQueSePuedaSacar(Usuario usuario, String accion) {
        if (usuario.getUsername().equals(contexto.getCallerPrincipal().getName())) {
            throw new ValidacionException("No podés " + accion + " tu propia cuenta");
        }
        if (usuario.getRol() == Rol.ADMINISTRADOR && usuario.isActivo()
                && repository.contarAdministradoresActivos() <= 1) {
            throw new ValidacionException("No se puede " + accion + " al último administrador activo");
        }
    }

    // Una cuenta COMERCIO o ERP representa a un comercio existente y activo;
    // una REPARTIDOR, a un repartidor que todavía no tiene cuenta.
    private void validarAsociacion(Rol rol, DatosUsuarioDTO datos) {
        if (representaUnComercio(rol)) {
            if (datos.idComercio == null) {
                throw new ValidacionException("Elegí el comercio que representa la cuenta");
            }
            if (!comercios.validarComercioActivo(datos.idComercio)) {
                throw new ValidacionException("El comercio elegido no existe o está dado de baja");
            }
        } else if (rol == Rol.REPARTIDOR) {
            if (datos.idRepartidor == null) {
                throw new ValidacionException("Elegí el repartidor que representa la cuenta");
            }
            if (repartidores.obtenerRepartidor(datos.idRepartidor) == null) {
                throw new ValidacionException("El repartidor elegido no existe");
            }
            if (repository.existeUsuarioDeRepartidor(datos.idRepartidor)) {
                throw new ValidacionException("Ese repartidor ya tiene una cuenta");
            }
        }
    }

    // El ERP de un comercio actúa en nombre de ese comercio por la API, así
    // que su cuenta queda atada a él igual que la del portal.
    private static boolean representaUnComercio(Rol rol) {
        return rol == Rol.COMERCIO || rol == Rol.ERP;
    }

    // Lista blanca estricta para el username: es el identificador de login
    // en WildFly y también se busca en los archivos del realm local (ver
    // ApplicationRealmSync), donde un salto de línea, "=" o ":" podría
    // confundir las entradas de otros usuarios.
    private void validarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidacionException("El nombre de usuario es obligatorio");
        }
        if (!ApplicationRealmSync.usernameValido(username.trim())) {
            throw new ValidacionException("El nombre de usuario debe tener entre 3 y 30 caracteres: "
                    + "letras, números, punto, guion o guion bajo, sin espacios");
        }
    }

    // Password obligatoria y con un mínimo de longitud — la única regla de
    // complejidad que exige este alcance.
    private void validarPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ValidacionException("La contraseña es obligatoria");
        }
        if (password.length() < 6) {
            throw new ValidacionException("La contraseña debe tener al menos 6 caracteres");
        }
    }

    // IConsultaUsuarios

    // Devuelve todos los usuarios como DTO — usado por la vista de listado (JSF)
    @Override
    @RolesAllowed("ADMINISTRADOR")
    public List<UsuarioDTO> listarTodos() {
        return repository.listarTodos().stream().map(UsuarioDTO::desde).collect(Collectors.toList());
    }

    // Devuelve el usuario como DTO (nunca expone la entidad, ni el hash de la contraseña)
    @Override
    public UsuarioDTO obtenerUsuario(Long id) {
        return UsuarioDTO.desde(obtenerOFallar(id));
    }

    // Busca un usuario o falla con un mensaje de negocio entendible, en vez
    // de dejar que el resto del método reciba un null y explote más abajo
    // con un NullPointerException sin contexto.
    private Usuario obtenerOFallar(Long id) {
        Usuario usuario = repository.buscarPorId(id);
        if (usuario == null) {
            throw new ValidacionException("Usuario no encontrado: " + id);
        }
        return usuario;
    }
}
