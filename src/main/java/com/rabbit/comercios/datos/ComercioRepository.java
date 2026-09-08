package com.rabbit.comercios.datos;

/**
 * CAPA DE DATOS (Patrón DAO - Data Access Object, sobre JPA)
 *
 * Esta carpeta contiene las clases que se comunican directamente con la base de datos.
 * Su única responsabilidad es persistir, buscar, actualizar y eliminar datos.
 * NO contiene lógica de negocio — eso le pertenece a la capa de Negocio.
 *
 * ComercioRepository usa JPA a través del EntityManager para operar sobre
 * las entidades Comercio y PuntoPicking sin escribir SQL manual: los métodos de
 * abajo o bien delegan directamente en el EntityManager (persist/merge/find/
 * remove) o ejecutan JPQL (una variante de SQL que opera sobre entidades y
 * sus atributos Java en vez de sobre tablas y columnas).
 *
 * El EntityManager se inyecta vía @PersistenceContext, apuntando a la unidad
 * de persistencia "comerciosPU" definida en persistence.xml (que a su vez
 * apunta al datasource JNDI configurado en WildFly). Al ser @ApplicationScoped
 * y usar transacciones JTA administradas por el contenedor, el repository no
 * necesita abrir ni cerrar transacciones ni conexiones manualmente — eso lo
 * resuelve el @Transactional de ComercioService.
 *
 * Al aislar el acceso a datos acá, si mañana cambiamos de motor de BD
 * o de JPA a otra tecnología, solo se toca esta capa.
 */

import com.rabbit.comercios.datos.model.Comercio;
import com.rabbit.comercios.datos.model.PuntoPicking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class ComercioRepository {

    // Puente hacia la base de datos: sabe traducir entidades @Entity a filas
    // y viceversa. Lo administra el contenedor (WildFly), no se instancia a mano.
    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    /**
     * Persiste un comercio nuevo (sin ID) en la BD.
     * em.persist() marca el objeto como "managed": a partir de acá, JPA
     * sincroniza automáticamente cualquier cambio sobre él con la BD hasta
     * el fin de la transacción. El ID lo asigna la BD (GenerationType.AUTO
     * en la entidad) y queda disponible en el objeto luego de persistir.
     *
     * @param comercio entidad transitoria (recién creada con new, sin ID)
     * @return el mismo objeto, ya con el ID asignado por la BD
     */
    public Comercio guardar(Comercio comercio) {
        em.persist(comercio);
        return comercio;
    }

    /**
     * Actualiza un comercio existente en la BD.
     * em.merge() copia el estado del objeto recibido sobre la entidad
     * "managed" equivalente (buscándola por ID si hace falta) y devuelve
     * esa entidad managed — es la forma correcta de guardar cambios sobre
     * un objeto que pudo haber sido detachado (p. ej. viajó por capas).
     *
     * @param comercio entidad con el ID de un registro existente y los campos actualizados
     * @return la entidad managed con los cambios ya aplicados
     */
    public Comercio actualizar(Comercio comercio) {
        return em.merge(comercio);
    }

    /**
     * Busca un comercio por su ID.
     * em.find() primero revisa el contexto de persistencia en memoria
     * (caché de primer nivel) antes de ir a la BD — si ya se cargó esa
     * entidad en esta misma transacción, no repite la consulta.
     *
     * @param id identificador del comercio
     * @return el comercio encontrado, o null si no existe (JPA no lanza excepción acá)
     */
    public Comercio buscarPorId(Long id) {
        return em.find(Comercio.class, id);
    }

    /**
     * Lista todos los comercios registrados (activos e inactivos) — usada
     * por la vista de listado. La consulta JPQL "SELECT c FROM Comercio c"
     * es el equivalente a "SELECT * FROM comercios" pero expresado en
     * términos de la entidad Java, no de la tabla.
     *
     * @return todos los comercios persistidos, en el orden que devuelva la BD
     */
    public List<Comercio> listarTodos() {
        return em.createQuery("SELECT c FROM Comercio c", Comercio.class).getResultList();
    }

    /**
     * Indica si ya existe un comercio con ese CUIT — lo usa ComercioService
     * para garantizar unicidad antes de guardar. Se excluye idAExcluir para
     * poder reutilizar el mismo chequeo al actualizar un comercio existente
     * (si no se excluyera, el propio comercio siempre "chocaría" con su CUIT).
     *
     * @param cuit CUIT a verificar
     * @param idAExcluir ID a excluir de la búsqueda (null si es un alta nueva)
     * @return true si otro comercio ya tiene ese CUIT
     */
    public boolean existeCuit(String cuit, Long idAExcluir) {
        String jpql = "SELECT COUNT(c) FROM Comercio c WHERE c.cuit = :cuit"
                + (idAExcluir != null ? " AND c.id <> :idAExcluir" : "");
        var query = em.createQuery(jpql, Long.class).setParameter("cuit", cuit);
        if (idAExcluir != null) {
            query.setParameter("idAExcluir", idAExcluir);
        }
        return query.getSingleResult() > 0;
    }

    /**
     * Elimina físicamente un comercio de la BD (DELETE real, no baja lógica).
     * em.remove() solo acepta entidades managed, por eso primero se verifica
     * con em.contains(): si el objeto ya está managed se remueve directo, si
     * no (por ejemplo, vino detachado de otra capa) se lo vuelve a adjuntar
     * con merge() antes de removerlo.
     *
     * Por el cascade = CascadeType.ALL en Comercio.puntosPicking, este
     * borrado se propaga en cascada: también se eliminan físicamente todos
     * los puntos de picking del comercio. ComercioService exige que el
     * comercio esté dado de baja antes de permitir llegar hasta acá,
     * precisamente para evitar perder datos por accidente.
     *
     * @param comercio comercio a eliminar
     */
    public void eliminar(Comercio comercio) {
        em.remove(em.contains(comercio) ? comercio : em.merge(comercio));
    }

    /**
     * Lista solo los puntos de picking activos de un comercio, vía JPQL
     * con parámetro nombrado (:idComercio) — evita concatenar el valor en
     * el string de la consulta y así previene inyección JPQL.
     *
     * @param idComercio ID del comercio dueño de los puntos de picking
     * @return puntos de picking de ese comercio con activa = true
     */
    public List<PuntoPicking> listarPuntosPickingActivos(Long idComercio) {
        return em.createQuery(
                "SELECT p FROM PuntoPicking p WHERE p.comercio.id = :idComercio AND p.activa = true",
                PuntoPicking.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    /**
     * Lista todos los puntos de picking de un comercio (activos e
     * inactivos), ordenados por ID — la usa la pantalla de administración
     * (puntos-picking.xhtml), que necesita ver también los dados de baja.
     *
     * @param idComercio ID del comercio dueño de los puntos de picking
     * @return todos los puntos de picking de ese comercio
     */
    public List<PuntoPicking> listarPuntosPickingDeComercio(Long idComercio) {
        return em.createQuery(
                "SELECT p FROM PuntoPicking p WHERE p.comercio.id = :idComercio ORDER BY p.id",
                PuntoPicking.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    /**
     * Persiste un punto de picking nuevo, ya asociado a su comercio
     * (puntoPicking.setComercio(...) debe haberse hecho antes de llamar
     * acá, para que la FK comercio_id no quede nula).
     *
     * @param puntoPicking entidad transitoria (recién creada con new, sin ID)
     * @return el mismo objeto, ya con el ID asignado por la BD
     */
    public PuntoPicking guardarPuntoPicking(PuntoPicking puntoPicking) {
        em.persist(puntoPicking);
        return puntoPicking;
    }

    /**
     * Busca un punto de picking por su ID.
     *
     * @param id identificador del punto de picking
     * @return el punto de picking encontrado, o null si no existe
     */
    public PuntoPicking buscarPuntoPickingPorId(Long id) {
        return em.find(PuntoPicking.class, id);
    }

    /**
     * Actualiza un punto de picking existente (equivalente a
     * {@link #actualizar} pero para la entidad PuntoPicking).
     *
     * @param puntoPicking entidad con el ID de un registro existente y los campos actualizados
     * @return la entidad managed con los cambios ya aplicados
     */
    public PuntoPicking actualizarPuntoPicking(PuntoPicking puntoPicking) {
        return em.merge(puntoPicking);
    }
}
