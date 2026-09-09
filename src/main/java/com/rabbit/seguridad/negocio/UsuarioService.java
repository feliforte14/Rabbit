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
 * Gestiona el padrón de usuarios de la app (tabla "usuarios") Y, vía
 * ApplicationRealmSync, lo mantiene sincronizado con el ApplicationRealm
 * nativo de WildFly — el que realmente resuelve la autenticación y el rol
 * que evalúan las anotaciones @RolesAllowed en operaciones sensibles de
 * otros componentes (ver ComercioService.eliminarComercio). Sin esa
 * sincronización, un alta acá no alcanzaría para poder loguearse.
 *
 * EVIDENCIA DE CICLO DE VIDA GESTIONADO POR EL CONTENEDOR:
 * @PostConstruct/@PreDestroy no los llama nadie del código de la app — los
 * dispara WildFly al tomar y devolver una instancia del pool. El hashCode()
 * en el log deja ver que, entre llamadas, puede tratarse de instancias
 * distintas (a diferencia de InventarioService, donde el mismo hashCode
 * se repite durante toda la conversación).
 */

import com.rabbit.seguridad.datos.UsuarioRepository;
import com.rabbit.seguridad.datos.model.Rol;
import com.rabbit.seguridad.datos.model.Usuario;
import com.rabbit.seguridad.dto.DatosUsuarioDTO;
import com.rabbit.seguridad.dto.UsuarioDTO;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
public class UsuarioService implements IConsultaUsuarios, IRegistroUsuarios {

    private static final Logger LOG = Logger.getLogger(UsuarioService.class.getName());

    @Inject
    private UsuarioRepository repository;

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
     * Crea un usuario nuevo: lo persiste en la tabla "usuarios" (con el
     * password ya hasheado, ver PasswordUtil) y lo sincroniza contra el
     * ApplicationRealm de WildFly para que pueda loguearse (ver
     * ApplicationRealmSync).
     *
     * @param datos datos ingresados en el formulario de alta
     * @return el ID asignado por la BD al nuevo usuario
     * @throws ValidacionException si el username/password son inválidos o el username ya existe
     */
    @Override
    @Transactional
    public Long registrarUsuario(DatosUsuarioDTO datos) {
        validarUsername(datos.username);
        validarPassword(datos.password);
        if (repository.existeUsername(datos.username.trim())) {
            throw new ValidacionException("Ya existe un usuario con el nombre \"" + datos.username + "\"");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(datos.username.trim());
        usuario.setPasswordHash(PasswordUtil.hash(datos.password));
        usuario.setRol(datos.rol != null ? datos.rol : Rol.OPERADOR);
        usuario.setActivo(true);
        Long id = repository.guardar(usuario).getId();

        // Sin esto, el usuario queda en esta tabla pero no puede loguearse
        // — ver ApplicationRealmSync. Si esto falla, el throw revierte
        // también el alta en la tabla "usuarios" (unchecked -> rollback).
        ApplicationRealmSync.altaUsuario(usuario.getUsername(), datos.password, usuario.getRol().name());
        return id;
    }

    /**
     * Baja lógica: el usuario deja de poder autenticarse (ver
     * Usuario.activo), tanto en la tabla propia como en el
     * ApplicationRealm de WildFly.
     *
     * @param id ID del usuario a dar de baja
     * @throws ValidacionException si el usuario no existe
     */
    @Override
    @Transactional
    public void darDeBaja(Long id) {
        Usuario usuario = obtenerOFallar(id);
        usuario.setActivo(false);
        repository.actualizar(usuario);
        ApplicationRealmSync.bajaUsuario(usuario.getUsername());
    }

    // Username obligatorio, sin más restricción de formato: el TP no exige
    // reglas de complejidad de usuario, solo que no venga vacío.
    private void validarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidacionException("El nombre de usuario es obligatorio");
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
    public List<UsuarioDTO> listarTodos() {
        return repository.listarTodos().stream().map(UsuarioDTO::desde).collect(Collectors.toList());
    }

    // Devuelve el usuario como DTO (nunca expone la entidad, ni su passwordHash)
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
