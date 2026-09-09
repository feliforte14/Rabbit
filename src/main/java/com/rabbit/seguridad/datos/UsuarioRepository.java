package com.rabbit.seguridad.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 *
 * Usa la misma unidad de persistencia "comerciosPU" que el resto de los
 * componentes (ver persistence.xml): un solo datasource para toda la app.
 */

import com.rabbit.seguridad.datos.model.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class UsuarioRepository {

    // Puente hacia la base de datos: sabe traducir entidades @Entity a filas
    // y viceversa. Lo administra el contenedor (WildFly), no se instancia a mano.
    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    /**
     * Persiste un usuario nuevo (sin ID) en la BD.
     *
     * @param usuario entidad transitoria (recién creada con new, sin ID), con passwordHash ya calculado
     * @return el mismo objeto, ya con el ID asignado por la BD
     */
    public Usuario guardar(Usuario usuario) {
        em.persist(usuario);
        return usuario;
    }

    /**
     * Busca un usuario por su ID.
     *
     * @param id identificador del usuario
     * @return el usuario encontrado, o null si no existe
     */
    public Usuario buscarPorId(Long id) {
        return em.find(Usuario.class, id);
    }

    /**
     * Usado por RabbitIdentityStore en cada intento de login — por eso no
     * lanza excepción si no existe, devuelve null (una respuesta "no está"
     * es un caso normal de autenticación fallida, no un error).
     */
    public Usuario buscarPorUsername(String username) {
        try {
            return em.createQuery(
                    "SELECT u FROM Usuario u WHERE u.username = :username",
                    Usuario.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Indica si ya existe un usuario con ese username — lo usa
     * UsuarioService para garantizar unicidad antes de registrar.
     *
     * @param username username a verificar
     * @return true si ya hay un usuario con ese username
     */
    public boolean existeUsername(String username) {
        return em.createQuery(
                "SELECT COUNT(u) FROM Usuario u WHERE u.username = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult() > 0;
    }

    /**
     * Lista todos los usuarios registrados (activos e inactivos), ordenados
     * por ID — usada por la vista de listado.
     *
     * @return todos los usuarios persistidos
     */
    public List<Usuario> listarTodos() {
        return em.createQuery("SELECT u FROM Usuario u ORDER BY u.id", Usuario.class).getResultList();
    }

    /**
     * Actualiza un usuario existente en la BD (por ejemplo, al darlo de baja).
     *
     * @param usuario entidad con el ID de un registro existente y los campos actualizados
     * @return la entidad managed con los cambios ya aplicados
     */
    public Usuario actualizar(Usuario usuario) {
        return em.merge(usuario);
    }
}
