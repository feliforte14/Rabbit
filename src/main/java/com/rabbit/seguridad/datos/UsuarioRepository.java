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

    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    public Usuario guardar(Usuario usuario) {
        em.persist(usuario);
        return usuario;
    }

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

    public boolean existeUsername(String username) {
        return em.createQuery(
                "SELECT COUNT(u) FROM Usuario u WHERE u.username = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult() > 0;
    }

    public List<Usuario> listarTodos() {
        return em.createQuery("SELECT u FROM Usuario u ORDER BY u.id", Usuario.class).getResultList();
    }

    public Usuario actualizar(Usuario usuario) {
        return em.merge(usuario);
    }
}
