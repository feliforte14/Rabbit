package com.rabbit.comercios.datos;

/**
 * CAPA DE DATOS (Patrón DAO - Data Access Object, sobre JPA)
 *
 * Esta carpeta contiene las clases que se comunican directamente con la base de datos.
 * Su única responsabilidad es persistir, buscar, actualizar y eliminar datos.
 * NO contiene lógica de negocio — eso le pertenece a la capa de Negocio.
 *
 * ComercioRepository usa JPA a través del EntityManager para operar sobre
 * las entidades Comercio y Sucursal sin escribir SQL manual: los métodos de
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
import com.rabbit.comercios.datos.model.Sucursal;
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
     * Por el cascade = CascadeType.ALL en Comercio.sucursales, este borrado
     * se propaga en cascada: también se eliminan físicamente todas las
     * sucursales del comercio. ComercioService exige que el comercio esté
     * dado de baja antes de permitir llegar hasta acá, precisamente para
     * evitar perder sucursales por accidente.
     *
     * @param comercio comercio a eliminar
     */
    public void eliminar(Comercio comercio) {
        em.remove(em.contains(comercio) ? comercio : em.merge(comercio));
    }

    /**
     * Lista solo las sucursales activas de un comercio, vía JPQL con
     * parámetro nombrado (:idComercio) — evita concatenar el valor en el
     * string de la consulta y así previene inyección JPQL.
     *
     * @param idComercio ID del comercio dueño de las sucursales
     * @return sucursales de ese comercio con activa = true
     */
    public List<Sucursal> listarSucursalesActivas(Long idComercio) {
        return em.createQuery(
                "SELECT s FROM Sucursal s WHERE s.comercio.id = :idComercio AND s.activa = true",
                Sucursal.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    /**
     * Lista todas las sucursales de un comercio (activas e inactivas),
     * ordenadas por ID — la usa la pantalla de administración de sucursales
     * (sucursales.xhtml), que necesita ver también las dadas de baja.
     *
     * @param idComercio ID del comercio dueño de las sucursales
     * @return todas las sucursales de ese comercio
     */
    public List<Sucursal> listarSucursalesDeComercio(Long idComercio) {
        return em.createQuery(
                "SELECT s FROM Sucursal s WHERE s.comercio.id = :idComercio ORDER BY s.id",
                Sucursal.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    /**
     * Persiste una sucursal nueva, ya asociada a su comercio
     * (sucursal.setComercio(...) debe haberse hecho antes de llamar acá,
     * para que la FK comercio_id no quede nula).
     *
     * @param sucursal entidad transitoria (recién creada con new, sin ID)
     * @return el mismo objeto, ya con el ID asignado por la BD
     */
    public Sucursal guardarSucursal(Sucursal sucursal) {
        em.persist(sucursal);
        return sucursal;
    }

    /**
     * Busca una sucursal por su ID.
     *
     * @param id identificador de la sucursal
     * @return la sucursal encontrada, o null si no existe
     */
    public Sucursal buscarSucursalPorId(Long id) {
        return em.find(Sucursal.class, id);
    }

    /**
     * Actualiza una sucursal existente (equivalente a {@link #actualizar}
     * pero para la entidad Sucursal).
     *
     * @param sucursal entidad con el ID de un registro existente y los campos actualizados
     * @return la entidad managed con los cambios ya aplicados
     */
    public Sucursal actualizarSucursal(Sucursal sucursal) {
        return em.merge(sucursal);
    }
}
