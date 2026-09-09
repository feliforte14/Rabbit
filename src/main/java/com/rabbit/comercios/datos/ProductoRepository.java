package com.rabbit.comercios.datos;

/**
 * CAPA DE DATOS (Patrón DAO, sobre JPA) — ver ComercioRepository para la
 * explicación completa del patrón, se aplica igual acá.
 *
 * ALCANCE ACTUAL: la entidad Producto (ver Sección 1.7 del documento
 * técnico) queda modelada y persistible, pensada para cuando
 * ServicioDeIntegracionERP importe el catálogo real de cada comercio.
 * Por ahora no tiene una pantalla propia — es la base de datos sobre la
 * que se construiría esa importación, no una funcionalidad entregada en
 * esta instancia.
 */

import com.rabbit.comercios.datos.model.Producto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class ProductoRepository {

    // Puente hacia la base de datos: sabe traducir entidades @Entity a filas
    // y viceversa. Lo administra el contenedor (WildFly), no se instancia a mano.
    @PersistenceContext(unitName = "comerciosPU")
    private EntityManager em;

    /**
     * Persiste un producto nuevo (sin ID) en la BD.
     *
     * @param producto entidad transitoria (recién creada con new, sin ID), ya con su Comercio asignado
     * @return el mismo objeto, ya con el ID asignado por la BD
     */
    public Producto guardar(Producto producto) {
        em.persist(producto);
        return producto;
    }

    /**
     * Actualiza un producto existente en la BD (incluye el JSONB de
     * atributos: se reemplaza entero, no se mergea campo a campo).
     *
     * @param producto entidad con el ID de un registro existente y los campos actualizados
     * @return la entidad managed con los cambios ya aplicados
     */
    public Producto actualizar(Producto producto) {
        return em.merge(producto);
    }

    /**
     * Busca un producto por su ID.
     *
     * @param id identificador del producto
     * @return el producto encontrado, o null si no existe
     */
    public Producto buscarPorId(Long id) {
        return em.find(Producto.class, id);
    }

    /**
     * Lista el catálogo completo de un comercio, ordenado por nombre —
     * pensado para la futura pantalla de catálogo (ver clase Producto).
     *
     * @param idComercio ID del comercio dueño del catálogo
     * @return todos los productos de ese comercio
     */
    public List<Producto> listarPorComercio(Long idComercio) {
        return em.createQuery(
                "SELECT p FROM Producto p WHERE p.comercio.id = :idComercio ORDER BY p.nombre",
                Producto.class)
                .setParameter("idComercio", idComercio)
                .getResultList();
    }

    /**
     * Resuelve un producto a partir del código con el que lo identifica el
     * ERP del comercio — es la búsqueda que usaría el sincronizador de
     * pedidos externos para mapear "qué me pidieron" a un Producto real de
     * Rabbit (ver Producto sobre por qué hace falta ese código externo).
     *
     * @param idComercio ID del comercio dueño del catálogo
     * @param codigoExternoERP código con el que el ERP identifica ese producto
     * @return el producto correspondiente, o null si ninguno matchea ese código
     */
    public Producto buscarPorCodigoExternoERP(Long idComercio, String codigoExternoERP) {
        return em.createQuery(
                "SELECT p FROM Producto p WHERE p.comercio.id = :idComercio AND p.codigoExternoERP = :codigo",
                Producto.class)
                .setParameter("idComercio", idComercio)
                .setParameter("codigo", codigoExternoERP)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
